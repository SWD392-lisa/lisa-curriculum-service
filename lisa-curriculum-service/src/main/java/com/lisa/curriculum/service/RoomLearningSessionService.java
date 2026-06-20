package com.lisa.curriculum.service;

import com.lisa.curriculum.dto.*;
import com.lisa.curriculum.entity.*;
import com.lisa.curriculum.exception.InvalidSessionStateException;
import com.lisa.curriculum.exception.ResourceNotFoundException;
import com.lisa.curriculum.repository.*;
import com.lisa.curriculum.security.CurrentUserHelper;
import com.lisa.curriculum.security.LmsUserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RoomLearningSessionService {

    private final RoomLearningSessionRepository sessionRepo;
    private final PinnedMaterialRepository pinnedMaterialRepo;
    private final LevelRepository levelRepo;
    private final SubLevelRepository subLevelRepo;
    private final LmsCacheService cacheService;
    private final RoomAttendanceRepository roomAttendanceRepo;
    private final RoomSessionSubLevelHistoryRepository subLevelHistoryRepo;

    @Value("${lms.session.end-session-on-last-sublevel:false}")
    private boolean endSessionOnLastSublevel;

    @Value("${lms.session.realtime-session-join-event:session.join}")
    private String realtimeSessionJoinEvent;

    @Value("${lms.integration.realtime-service-base-url:}")
    private String realtimeServiceBaseUrl;

    @Value("${lms.integration.realtime-socket-url:}")
    private String realtimeSocketUrl;

    @Transactional
    @CacheEvict(value = "mentor_dashboard", allEntries = true)
    public RoomSessionResponseDto createSession(CreateRoomSessionRequestDto request) {
        Long levelId = request.getLevelId();
        boolean autoSwitchEnabled = request.isAutoSwitchEnabled();
        Level level = levelRepo.findById(levelId)
                .orElseThrow(() -> new ResourceNotFoundException("Level not found: " + levelId));

        List<SubLevel> subLevels = level.getSubLevels();
        if (subLevels == null || subLevels.isEmpty()) {
            subLevels = subLevelRepo.findByLevelIdOrderBySubNumberAsc(levelId);
        }
        if (subLevels.isEmpty()) {
            throw new ResourceNotFoundException("Level does not contain any sub-levels: " + levelId);
        }

        // Make a mutable copy so we can sort it
        List<SubLevel> sortedSubLevels = new ArrayList<>(subLevels);
        sortedSubLevels.sort(Comparator.comparingInt(SubLevel::getSubNumber));
        SubLevel firstSubLevel = sortedSubLevels.get(0);

        LmsUserPrincipal currentUser = CurrentUserHelper.getCurrentUser();
        String mentorId = currentUser != null ? currentUser.getUserId() : "SYSTEM";

        UUID sessionId = UUID.randomUUID();
        String channelName = "lms-" + sessionId;
        RoomLearningSession session = RoomLearningSession.builder()
                .id(sessionId)
                .channelName(channelName)
                .realtimeRoomId(normalizeOptional(request.getRealtimeRoomId()))
                .realtimeAgoraChannelName(resolveRealtimeAgoraChannelName(request, channelName))
                .mentorUserId(mentorId)
                .levelId(levelId)
                .currentSubLevelId(firstSubLevel.getId())
                .status(SessionStatus.WAITING)
                .autoSwitchEnabled(autoSwitchEnabled)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        session = sessionRepo.save(session);
        writeSubLevelHistory(session.getId(), null, firstSubLevel.getId(), mentorId, SubLevelChangeSource.INIT, "Initial sub-level when session was created");
        cacheService.evictMentorDashboard(session.getMentorUserId());

        return mapToResponseDto(session);
    }

    @Transactional
    @CacheEvict(value = "mentor_dashboard", allEntries = true)
    public RoomSessionResponseDto startSession(UUID sessionId) {
        RoomLearningSession session = sessionRepo.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found: " + sessionId));

        checkMentorPermission(session);
        validateCurrentSubLevelBelongsToSessionLevel(session);
        assertTransitionAllowed(session.getStatus(), SessionStatus.LIVE, sessionId);

        session.setStatus(SessionStatus.LIVE);
        session.setStartedAt(Instant.now());
        session.setSubLevelStartedAt(Instant.now());
        session.setUpdatedAt(Instant.now());

        session = sessionRepo.save(session);
        cacheService.evictRoomState(sessionId);
        cacheService.evictRecordings(sessionId);
        return mapToResponseDto(session);
    }

    @Transactional
    @CacheEvict(value = "mentor_dashboard", allEntries = true)
    public RoomSessionResponseDto pauseSession(UUID sessionId) {
        RoomLearningSession session = sessionRepo.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found: " + sessionId));

        checkMentorPermission(session);
        validateCurrentSubLevelBelongsToSessionLevel(session);
        assertTransitionAllowed(session.getStatus(), SessionStatus.PAUSED, sessionId);

        session.setStatus(SessionStatus.PAUSED);
        session.setUpdatedAt(Instant.now());

        session = sessionRepo.save(session);
        cacheService.evictRoomState(sessionId);
        cacheService.evictRecordings(sessionId);
        return mapToResponseDto(session);
    }

    @Transactional
    @CacheEvict(value = "mentor_dashboard", allEntries = true)
    public RoomSessionResponseDto endSession(UUID sessionId) {
        RoomLearningSession session = sessionRepo.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found: " + sessionId));

        checkMentorPermission(session);
        validateCurrentSubLevelBelongsToSessionLevel(session);

        session.setStatus(SessionStatus.ENDED);
        session.setEndedAt(Instant.now());
        session.setUpdatedAt(Instant.now());

        session = sessionRepo.save(session);
        cacheService.evictRoomState(sessionId);
        cacheService.evictRecordings(sessionId);
        return mapToResponseDto(session);
    }

    @Cacheable(value = "room_state", key = "#sessionId")
    @Transactional(readOnly = true)
    public RoomSessionStateDto getState(UUID sessionId) {
        RoomLearningSession session = sessionRepo.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found: " + sessionId));
        validateCurrentSubLevelBelongsToSessionLevel(session);

        Level level = levelRepo.findById(session.getLevelId())
                .orElseThrow(() -> new ResourceNotFoundException("Level not found: " + session.getLevelId()));

        SubLevel currentSubLevel = subLevelRepo.findById(session.getCurrentSubLevelId())
                .orElseThrow(() -> new ResourceNotFoundException("SubLevel not found: " + session.getCurrentSubLevelId()));

        List<PinnedMaterial> materials = pinnedMaterialRepo.findByRoomSessionIdAndActiveTrueOrderByDisplayOrderAsc(sessionId);

        int subLevelDuration = currentSubLevel.getDurationMinutes() > 0 ? currentSubLevel.getDurationMinutes() : 10;

        long secondsRemaining = 0;
        if (session.getStatus() == SessionStatus.LIVE && session.getSubLevelStartedAt() != null) {
            long elapsed = Duration.between(session.getSubLevelStartedAt(), Instant.now()).getSeconds();
            long totalDurationSeconds = (long) subLevelDuration * 60;
            secondsRemaining = Math.max(0, totalDurationSeconds - elapsed);
        } else {
            secondsRemaining = (long) subLevelDuration * 60;
        }

        List<RoomSessionStateDto.PinnedMaterialDto> materialDtos = materials.stream()
                .map(m -> RoomSessionStateDto.PinnedMaterialDto.builder()
                        .id(m.getId())
                        .title(m.getTitle())
                        .materialType(m.getMaterialType().name())
                        .url(m.getUrl())
                        .displayOrder(m.getDisplayOrder())
                        .active(m.isActive())
                        .pinnedByUserId(m.getPinnedByUserId())
                        .pinnedAt(m.getPinnedAt())
                        .build())
                .collect(Collectors.toList());

        Map<String, Object> realtimeMeta = new LinkedHashMap<>();
        realtimeMeta.put("socketEvent", realtimeSessionJoinEvent);
        realtimeMeta.put("sessionId", sessionId.toString());
        realtimeMeta.put("lmsChannelName", session.getChannelName());
        realtimeMeta.put("roomId", session.getRealtimeRoomId());
        realtimeMeta.put("agoraChannelName", session.getRealtimeAgoraChannelName());
        realtimeMeta.put("baseUrl", realtimeServiceBaseUrl);
        realtimeMeta.put("socketUrl", realtimeSocketUrl);
        realtimeMeta.put("bindEndpoint", "/api/lms/room-sessions/" + sessionId + "/realtime-binding");
        realtimeMeta.put("roomLookupEndpointTemplate", realtimeServiceBaseUrl + "/rooms/{roomId}");
        realtimeMeta.put("participantsEndpointTemplate", realtimeServiceBaseUrl + "/rooms/{roomId}/participants");
        realtimeMeta.put("mappingStatus", session.getRealtimeRoomId() == null ? "UNBOUND" : "BOUND");
        realtimeMeta.put("note", "LMS stores explicit Realtime room binding because Realtime roomId is generated independently.");

        LmsUserPrincipal principal = CurrentUserHelper.getCurrentUser();
        boolean hasFullAccess = false;
        if (principal != null) {
            // SUPER (role_id=3) → ROLE_CREATOR là quyền cao nhất trong hệ thống
            boolean isCreator = principal.getAuthorities() != null && principal.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_CREATOR"));
            boolean isOwnerMentor = session.getMentorUserId() != null && session.getMentorUserId().equals(principal.getUserId());
            boolean isJoinedLearner = roomAttendanceRepo.existsByRoomSessionIdAndLearnerUserId(sessionId, principal.getUserId());
            if (isCreator || isOwnerMentor || isJoinedLearner) {
                hasFullAccess = true;
            }
        }

        if (!hasFullAccess) {
            throw new AccessDeniedException("enrollmentValidationMode=LMS_ATTENDANCE_GUARD: learner must join session before viewing room state");
        }

        return RoomSessionStateDto.builder()
                .sessionId(sessionId)
                .channelName(session.getChannelName())
                .status(session.getStatus().name())
                .realtimeRoomId(hasFullAccess ? session.getRealtimeRoomId() : null)
                .realtimeAgoraChannelName(hasFullAccess ? session.getRealtimeAgoraChannelName() : null)
                .levelSummary(RoomSessionStateDto.LevelSummaryDto.builder()
                        .id(level.getId())
                        .language(level.getLanguage().name())
                        .stage(level.getStage())
                        .levelNumber(level.getLevelNumber())
                        .title(level.getTitle())
                        .cefrTarget(level.getCefrTarget())
                        .durationMinutes(level.getDurationMinutes())
                        .build())
                .currentSubLevel(SubLevelDto.builder()
                        .id(currentSubLevel.getId())
                        .subNumber(currentSubLevel.getSubNumber())
                        .topic(currentSubLevel.getTopic())
                        .durationMinutes(currentSubLevel.getDurationMinutes())
                        .tasks(currentSubLevel.getTasks().stream()
                                .map(t -> SpeakingTaskDto.builder()
                                        .id(t.getId())
                                        .taskType(t.getTaskType())
                                        .content(t.getContent())
                                        .pronunciation(t.getPronunciation())
                                        .orderIndex(t.getOrderIndex())
                                        .build())
                                .collect(Collectors.toList()))
                        .build())
                .subLevelStartedAt(session.getSubLevelStartedAt())
                .secondsRemaining(secondsRemaining)
                .autoSwitchEnabled(session.isAutoSwitchEnabled())
                .pinnedMaterials(hasFullAccess ? materialDtos : Collections.emptyList())
                .realtime(hasFullAccess ? realtimeMeta : null)
                .build();
    }

    @Transactional
    @CacheEvict(value = "mentor_dashboard", allEntries = true)
    public RoomSessionResponseDto switchToNextSubLevel(UUID sessionId, String reason) {
        RoomLearningSession session = sessionRepo.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found: " + sessionId));

        checkMentorPermission(session);
        validateCurrentSubLevelBelongsToSessionLevel(session);
        assertCanSwitchSubLevel(session);

        if (reason.contains("Auto-switch")) {
            SubLevel current = subLevelRepo.findById(session.getCurrentSubLevelId()).orElse(null);
            if (current != null && session.getSubLevelStartedAt() != null) {
                long elapsedSeconds = Duration.between(session.getSubLevelStartedAt(), Instant.now()).getSeconds();
                int durationMinutes = current.getDurationMinutes() > 0 ? current.getDurationMinutes() : 10;
                long totalDurationSeconds = (long) durationMinutes * 60;
                if (elapsedSeconds < totalDurationSeconds) {
                    log.info("Session {} was already switched by another instance. Ignoring duplicate auto-switch.", sessionId);
                    return mapToResponseDto(session);
                }
            }
        }

        List<SubLevel> subLevels = subLevelRepo.findByLevelIdOrderBySubNumberAsc(session.getLevelId());
        if (subLevels.isEmpty()) {
            throw new ResourceNotFoundException("No sublevels found for level: " + session.getLevelId());
        }

        int currentIndex = -1;
        for (int i = 0; i < subLevels.size(); i++) {
            if (subLevels.get(i).getId().equals(session.getCurrentSubLevelId())) {
                currentIndex = i;
                break;
            }
        }

        if (currentIndex == -1) {
            throw new IllegalArgumentException("Current sub-level " + session.getCurrentSubLevelId() + " is not part of level " + session.getLevelId());
        }

        if (currentIndex < subLevels.size() - 1) {
            Long fromSubLevelId = session.getCurrentSubLevelId();
            SubLevel nextSubLevel = subLevels.get(currentIndex + 1);
            session.setCurrentSubLevelId(nextSubLevel.getId());
            session.setSubLevelStartedAt(Instant.now());
            log.info("Session {} auto-switched from sublevel {} to next sublevel {} due to: {}",
                    sessionId, subLevels.get(currentIndex).getId(), nextSubLevel.getId(), reason);
        } else {
            if (endSessionOnLastSublevel) {
                session.setStatus(SessionStatus.ENDED);
                session.setEndedAt(Instant.now());
                log.info("Session {} automatically ended on the last sublevel due to: {}", sessionId, reason);
            } else {
                log.info("Session {} is already on the last sublevel. Switch ignored.", sessionId);
            }
        }

        session.setUpdatedAt(Instant.now());
        session = sessionRepo.save(session);
        if (currentIndex < subLevels.size() - 1) {
            SubLevelChangeSource source = reason != null && reason.contains("Auto-switch")
                    ? SubLevelChangeSource.AUTO
                    : SubLevelChangeSource.MANUAL;
            writeSubLevelHistory(sessionId, subLevels.get(currentIndex).getId(), session.getCurrentSubLevelId(), resolveCurrentUserId(), source, reason);
        }
        cacheService.evictRoomState(sessionId);
        cacheService.evictRecordings(sessionId);
        return mapToResponseDto(session);
    }

    @Transactional
    @CacheEvict(value = "mentor_dashboard", allEntries = true)
    public RoomSessionResponseDto switchToSubLevel(UUID sessionId, Long targetSubLevelId, String reason) {
        RoomLearningSession session = sessionRepo.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found: " + sessionId));

        checkMentorPermission(session);
        validateCurrentSubLevelBelongsToSessionLevel(session);
        assertCanSwitchSubLevel(session);

        SubLevel targetSubLevel = subLevelRepo.findById(targetSubLevelId)
                .orElseThrow(() -> new ResourceNotFoundException("SubLevel not found: " + targetSubLevelId));

        if (!targetSubLevel.getLevel().getId().equals(session.getLevelId())) {
            throw new IllegalArgumentException("Target SubLevel " + targetSubLevelId + " does not belong to session Level " + session.getLevelId());
        }

        Long fromSubLevelId = session.getCurrentSubLevelId();
        session.setCurrentSubLevelId(targetSubLevelId);
        session.setSubLevelStartedAt(Instant.now());
        session.setUpdatedAt(Instant.now());

        log.info("Session {} switched to sublevel {} due to: {}", sessionId, targetSubLevelId, reason);

        session = sessionRepo.save(session);
        writeSubLevelHistory(sessionId, fromSubLevelId, targetSubLevelId, resolveCurrentUserId(), SubLevelChangeSource.MANUAL, reason);
        cacheService.evictRoomState(sessionId);
        cacheService.evictRecordings(sessionId);
        return mapToResponseDto(session);
    }

    @Transactional
    @CacheEvict(value = "mentor_dashboard", allEntries = true)
    public RoomSessionResponseDto bindRealtimeRoom(UUID sessionId, RealtimeRoomBindingRequestDto request) {
        RoomLearningSession session = sessionRepo.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found: " + sessionId));

        checkMentorPermission(session);
        validateCurrentSubLevelBelongsToSessionLevel(session);

        session.setRealtimeRoomId(request.getRealtimeRoomId().trim());
        session.setRealtimeAgoraChannelName(normalizeOptional(request.getRealtimeAgoraChannelName()));
        session.setUpdatedAt(Instant.now());

        session = sessionRepo.save(session);
        cacheService.evictRoomState(sessionId);
        cacheService.evictRecordings(sessionId);
        return mapToResponseDto(session);
    }

    @Transactional
    @CacheEvict(value = "mentor_dashboard", allEntries = true)
    public RoomSessionResponseDto unbindRealtimeRoom(UUID sessionId) {
        RoomLearningSession session = sessionRepo.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found: " + sessionId));

        checkMentorPermission(session);
        validateCurrentSubLevelBelongsToSessionLevel(session);

        session.setRealtimeRoomId(null);
        session.setRealtimeAgoraChannelName(null);
        session.setUpdatedAt(Instant.now());

        session = sessionRepo.save(session);
        cacheService.evictRoomState(sessionId);
        cacheService.evictRecordings(sessionId);
        return mapToResponseDto(session);
    }

    private void checkMentorPermission(RoomLearningSession session) {
        LmsUserPrincipal currentUser = CurrentUserHelper.getCurrentUser();
        if (currentUser == null) {
            return; // bypass for system background schedules
        }
        // SUPER (role_id=3) → ROLE_CREATOR là quyền cao nhất, có thể manage mọi session
        boolean isCreator = currentUser.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_CREATOR"));
        if (!isCreator && !session.getMentorUserId().equals(currentUser.getUserId())) {
            throw new AccessDeniedException("Mentor is only allowed to manage their own sessions");
        }
    }

    @Transactional(readOnly = true)
    public List<SubLevelHistoryDto> getSubLevelHistory(UUID sessionId) {
        RoomLearningSession session = sessionRepo.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found: " + sessionId));

        LmsUserPrincipal currentUser = CurrentUserHelper.getCurrentUser();
        boolean isOwnerMentor = currentUser != null && session.getMentorUserId().equals(currentUser.getUserId());
        boolean isCreator = currentUser != null && currentUser.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_CREATOR"));
        boolean isJoinedLearner = currentUser != null && roomAttendanceRepo.existsByRoomSessionIdAndLearnerUserId(sessionId, currentUser.getUserId());
        if (!isOwnerMentor && !isCreator && !isJoinedLearner) {
            throw new AccessDeniedException("enrollmentValidationMode=LMS_ATTENDANCE_GUARD: learner must join session before viewing sub-level history");
        }

        return subLevelHistoryRepo.findByRoomSessionIdOrderByChangedAtAscIdAsc(sessionId).stream()
                .map(item -> SubLevelHistoryDto.builder()
                        .id(item.getId())
                        .roomSessionId(item.getRoomSessionId())
                        .fromSubLevelId(item.getFromSubLevelId())
                        .toSubLevelId(item.getToSubLevelId())
                        .changedByUserId(item.getChangedByUserId())
                        .changeSource(item.getChangeSource().name())
                        .note(item.getNote())
                        .changedAt(item.getChangedAt())
                        .build())
                .toList();
    }

    private void validateCurrentSubLevelBelongsToSessionLevel(RoomLearningSession session) {
        if (!subLevelRepo.existsByIdAndLevelId(session.getCurrentSubLevelId(), session.getLevelId())) {
            throw new IllegalArgumentException("Current sub-level " + session.getCurrentSubLevelId() + " does not belong to level " + session.getLevelId());
        }
    }

    private void assertTransitionAllowed(SessionStatus currentStatus, SessionStatus targetStatus, UUID sessionId) {
        if (currentStatus == SessionStatus.ENDED && (targetStatus == SessionStatus.LIVE || targetStatus == SessionStatus.PAUSED)) {
            throw new InvalidSessionStateException("Session " + sessionId + " is ENDED and cannot transition to " + targetStatus);
        }
        if (currentStatus == SessionStatus.WAITING && targetStatus == SessionStatus.PAUSED) {
            throw new InvalidSessionStateException("Session " + sessionId + " is WAITING and cannot transition to PAUSED");
        }
    }

    private void assertCanSwitchSubLevel(RoomLearningSession session) {
        if (session.getStatus() == SessionStatus.ENDED) {
            throw new InvalidSessionStateException("Session " + session.getId() + " is ENDED and cannot switch sub-level");
        }
    }

    private void writeSubLevelHistory(UUID sessionId, Long fromSubLevelId, Long toSubLevelId, String changedByUserId, SubLevelChangeSource source, String note) {
        subLevelHistoryRepo.save(RoomSessionSubLevelHistory.builder()
                .roomSessionId(sessionId)
                .fromSubLevelId(fromSubLevelId)
                .toSubLevelId(toSubLevelId)
                .changedByUserId(changedByUserId)
                .changeSource(source)
                .note(note)
                .changedAt(Instant.now())
                .build());
    }

    private String resolveCurrentUserId() {
        LmsUserPrincipal currentUser = CurrentUserHelper.getCurrentUser();
        return currentUser != null ? currentUser.getUserId() : "SYSTEM";
    }

    private String resolveRealtimeAgoraChannelName(CreateRoomSessionRequestDto request, String defaultChannelName) {
        String explicit = normalizeOptional(request.getRealtimeAgoraChannelName());
        if (explicit != null) {
            return explicit;
        }
        return normalizeOptional(request.getRealtimeRoomId()) != null ? defaultChannelName : null;
    }

    private RoomSessionResponseDto mapToResponseDto(RoomLearningSession session) {
        return RoomSessionResponseDto.builder()
                .sessionId(session.getId())
                .channelName(session.getChannelName())
                .status(session.getStatus().name())
                .levelId(session.getLevelId())
                .currentSubLevelId(session.getCurrentSubLevelId())
                .realtimeRoomId(session.getRealtimeRoomId())
                .realtimeAgoraChannelName(session.getRealtimeAgoraChannelName())
                .build();
    }

    private String normalizeOptional(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
