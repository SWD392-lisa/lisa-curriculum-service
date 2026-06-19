package com.lisa.curriculum.service;

import com.lisa.curriculum.dto.*;
import com.lisa.curriculum.entity.*;
import com.lisa.curriculum.exception.ResourceNotFoundException;
import com.lisa.curriculum.repository.*;
import com.lisa.curriculum.security.CurrentUserHelper;
import com.lisa.curriculum.security.LmsUserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    @Value("${lms.session.end-session-on-last-sublevel:false}")
    private boolean endSessionOnLastSublevel;

    @Transactional
    @CacheEvict(value = "mentor_dashboard", allEntries = true)
    public RoomSessionResponseDto createSession(Long levelId, boolean autoSwitchEnabled) {
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
        RoomLearningSession session = RoomLearningSession.builder()
                .id(sessionId)
                .channelName("lms-" + sessionId)
                .mentorUserId(mentorId)
                .levelId(levelId)
                .currentSubLevelId(firstSubLevel.getId())
                .status(SessionStatus.WAITING)
                .autoSwitchEnabled(autoSwitchEnabled)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        session = sessionRepo.save(session);

        return mapToResponseDto(session);
    }

    @Transactional
    @CacheEvict(value = "mentor_dashboard", allEntries = true)
    public RoomSessionResponseDto startSession(UUID sessionId) {
        RoomLearningSession session = sessionRepo.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found: " + sessionId));

        checkMentorPermission(session);

        session.setStatus(SessionStatus.LIVE);
        session.setStartedAt(Instant.now());
        session.setSubLevelStartedAt(Instant.now());
        session.setUpdatedAt(Instant.now());

        session = sessionRepo.save(session);
        return mapToResponseDto(session);
    }

    @Transactional
    @CacheEvict(value = "mentor_dashboard", allEntries = true)
    public RoomSessionResponseDto pauseSession(UUID sessionId) {
        RoomLearningSession session = sessionRepo.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found: " + sessionId));

        checkMentorPermission(session);

        session.setStatus(SessionStatus.PAUSED);
        session.setUpdatedAt(Instant.now());

        session = sessionRepo.save(session);
        return mapToResponseDto(session);
    }

    @Transactional
    @CacheEvict(value = "mentor_dashboard", allEntries = true)
    public RoomSessionResponseDto endSession(UUID sessionId) {
        RoomLearningSession session = sessionRepo.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found: " + sessionId));

        checkMentorPermission(session);

        session.setStatus(SessionStatus.ENDED);
        session.setEndedAt(Instant.now());
        session.setUpdatedAt(Instant.now());

        session = sessionRepo.save(session);
        return mapToResponseDto(session);
    }

    @Transactional(readOnly = true)
    public RoomSessionStateDto getState(UUID sessionId) {
        RoomLearningSession session = sessionRepo.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found: " + sessionId));

        Level level = levelRepo.findById(session.getLevelId())
                .orElseThrow(() -> new ResourceNotFoundException("Level not found: " + session.getLevelId()));

        SubLevel currentSubLevel = subLevelRepo.findById(session.getCurrentSubLevelId())
                .orElseThrow(() -> new ResourceNotFoundException("SubLevel not found: " + session.getCurrentSubLevelId()));

        List<PinnedMaterial> materials = pinnedMaterialRepo.findByRoomSessionIdAndActiveTrueOrderByDisplayOrderAsc(sessionId);

        long secondsRemaining = 0;
        if (session.getStatus() == SessionStatus.LIVE && session.getSubLevelStartedAt() != null) {
            long elapsed = Duration.between(session.getSubLevelStartedAt(), Instant.now()).getSeconds();
            long totalDurationSeconds = (long) currentSubLevel.getDurationMinutes() * 60;
            secondsRemaining = Math.max(0, totalDurationSeconds - elapsed);
        } else {
            secondsRemaining = (long) currentSubLevel.getDurationMinutes() * 60;
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
        realtimeMeta.put("socketEvent", "session.join");
        realtimeMeta.put("sessionId", sessionId.toString());
        realtimeMeta.put("channelName", session.getChannelName());
        realtimeMeta.put("note", "Frontend connects directly to Realtime Socket.IO");

        return RoomSessionStateDto.builder()
                .sessionId(sessionId)
                .channelName(session.getChannelName())
                .status(session.getStatus().name())
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
                .pinnedMaterials(materialDtos)
                .realtime(realtimeMeta)
                .build();
    }

    @Transactional
    @CacheEvict(value = "mentor_dashboard", allEntries = true)
    public RoomSessionResponseDto switchToNextSubLevel(UUID sessionId, String reason) {
        RoomLearningSession session = sessionRepo.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found: " + sessionId));

        checkMentorPermission(session);

        if (reason.contains("Auto-switch")) {
            SubLevel current = subLevelRepo.findById(session.getCurrentSubLevelId()).orElse(null);
            if (current != null && session.getSubLevelStartedAt() != null) {
                long elapsedSeconds = Duration.between(session.getSubLevelStartedAt(), Instant.now()).getSeconds();
                long totalDurationSeconds = (long) current.getDurationMinutes() * 60;
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

        if (currentIndex != -1 && currentIndex < subLevels.size() - 1) {
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
        return mapToResponseDto(session);
    }

    @Transactional
    @CacheEvict(value = "mentor_dashboard", allEntries = true)
    public RoomSessionResponseDto switchToSubLevel(UUID sessionId, Long targetSubLevelId, String reason) {
        RoomLearningSession session = sessionRepo.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found: " + sessionId));

        checkMentorPermission(session);

        SubLevel targetSubLevel = subLevelRepo.findById(targetSubLevelId)
                .orElseThrow(() -> new ResourceNotFoundException("SubLevel not found: " + targetSubLevelId));

        if (!targetSubLevel.getLevel().getId().equals(session.getLevelId())) {
            throw new IllegalArgumentException("Target SubLevel " + targetSubLevelId + " does not belong to session Level " + session.getLevelId());
        }

        session.setCurrentSubLevelId(targetSubLevelId);
        session.setSubLevelStartedAt(Instant.now());
        session.setUpdatedAt(Instant.now());

        log.info("Session {} switched to sublevel {} due to: {}", sessionId, targetSubLevelId, reason);

        session = sessionRepo.save(session);
        return mapToResponseDto(session);
    }

    private void checkMentorPermission(RoomLearningSession session) {
        LmsUserPrincipal currentUser = CurrentUserHelper.getCurrentUser();
        if (currentUser == null) {
            return; // bypass for system background schedules
        }
        boolean isAdmin = currentUser.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (!isAdmin && !session.getMentorUserId().equals(currentUser.getUserId())) {
            throw new AccessDeniedException("Mentor is only allowed to manage their own sessions");
        }
    }

    private RoomSessionResponseDto mapToResponseDto(RoomLearningSession session) {
        return RoomSessionResponseDto.builder()
                .sessionId(session.getId())
                .channelName(session.getChannelName())
                .status(session.getStatus().name())
                .levelId(session.getLevelId())
                .currentSubLevelId(session.getCurrentSubLevelId())
                .build();
    }
}
