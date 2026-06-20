package com.lisa.curriculum.service;

import com.lisa.curriculum.dto.*;
import com.lisa.curriculum.entity.*;
import com.lisa.curriculum.exception.InvalidSessionStateException;
import com.lisa.curriculum.exception.ResourceNotFoundException;
import com.lisa.curriculum.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardService {

    private final RoomLearningSessionRepository sessionRepo;
    private final RoomAttendanceRepository attendanceRepo;
    private final LearnerProgressRepository progressRepo;
    private final PinnedMaterialRepository pinnedMaterialRepo;
    private final SessionRecordingRepository sessionRecordingRepository;
    private final SubLevelRepository subLevelRepository;
    private final LmsCacheService cacheService;

    @Transactional
    public void recordAttendanceJoin(UUID sessionId, String learnerUserId) {
        RoomLearningSession session = getSession(sessionId);
        if (session.getStatus() != SessionStatus.WAITING && session.getStatus() != SessionStatus.LIVE) {
            throw new InvalidSessionStateException("Session " + sessionId + " must be WAITING or LIVE for learner join. Current status: " + session.getStatus());
        }

        Optional<RoomAttendance> activeOpt = attendanceRepo
                .findFirstByRoomSessionIdAndLearnerUserIdAndLeftAtIsNullOrderByJoinedAtDesc(sessionId, learnerUserId);

        if (activeOpt.isEmpty()) {
            try {
                RoomAttendance attendance = RoomAttendance.builder()
                        .roomSessionId(sessionId)
                        .learnerUserId(learnerUserId)
                        .joinedAt(Instant.now())
                        .build();
                attendanceRepo.saveAndFlush(attendance);
                log.info("[Attendance] Learner {} joined session {} enrollmentValidationMode=LMS_ATTENDANCE_GUARD", learnerUserId, sessionId);
            } catch (DataIntegrityViolationException e) {
                Optional<RoomAttendance> reread = attendanceRepo
                        .findFirstByRoomSessionIdAndLearnerUserIdAndLeftAtIsNullOrderByJoinedAtDesc(sessionId, learnerUserId);
                if (reread.isEmpty()) {
                    throw e;
                }
                log.info("[Attendance] Concurrent join collapsed for learner {} session {} enrollmentValidationMode=LMS_ATTENDANCE_GUARD", learnerUserId, sessionId);
            }
        }

        cacheService.evictLearnerProgress(learnerUserId);
        cacheService.evictMentorDashboard(session.getMentorUserId());
        cacheService.evictRoomState(sessionId);
    }

    @Transactional
    public void recordAttendanceLeave(UUID sessionId, String learnerUserId) {
        RoomLearningSession session = getSession(sessionId);

        Optional<RoomAttendance> activeOpt = attendanceRepo
                .findFirstByRoomSessionIdAndLearnerUserIdAndLeftAtIsNullOrderByJoinedAtDesc(sessionId, learnerUserId);

        if (activeOpt.isPresent()) {
            RoomAttendance attendance = activeOpt.get();
            attendance.setLeftAt(Instant.now());
            attendance.setTotalSeconds(Duration.between(attendance.getJoinedAt(), attendance.getLeftAt()).toSeconds());
            attendanceRepo.save(attendance);
        } else {
            // Check if there is ANY record at all
            Optional<RoomAttendance> anyOpt = attendanceRepo
                    .findFirstByRoomSessionIdAndLearnerUserIdOrderByJoinedAtDesc(sessionId, learnerUserId);
            if (anyOpt.isEmpty()) {
                throw new ResourceNotFoundException(
                        "No active attendance record found for user " + learnerUserId + " in session " + sessionId);
            }
            log.info("[Attendance] Learner {} already left session {} (no-op success)", learnerUserId, sessionId);
        }

        cacheService.evictLearnerProgress(learnerUserId);
        cacheService.evictMentorDashboard(session.getMentorUserId());
        cacheService.evictRoomState(sessionId);
    }

    @Transactional
    public LearnerProgressResponseDto saveLearnerProgress(String learnerUserId, LearnerProgressRequestDto dto) {
        if (dto.getSessionId() != null) {
            RoomLearningSession session = getSession(dto.getSessionId());
            if (!attendanceRepo.existsByRoomSessionIdAndLearnerUserId(dto.getSessionId(), learnerUserId)) {
                throw new AccessDeniedException("enrollmentValidationMode=LMS_ATTENDANCE_GUARD: learner must join session before submitting progress");
            }
            if (!subLevelRepository.existsByIdAndLevelId(dto.getSubLevelId(), session.getLevelId())) {
                throw new IllegalArgumentException("Sub-level " + dto.getSubLevelId() + " does not belong to session level " + session.getLevelId());
            }
        }

        LearnerProgress progress = progressRepo.findByLearnerUserIdAndSubLevelId(learnerUserId, dto.getSubLevelId())
                .orElseGet(() -> LearnerProgress.builder()
                        .learnerUserId(learnerUserId)
                        .levelId(dto.getLevelId())
                        .subLevelId(dto.getSubLevelId())
                        .build());

        if (dto.getIdempotencyKey() != null && !dto.getIdempotencyKey().trim().isEmpty()) {
            if (dto.getIdempotencyKey().equals(progress.getLastIdempotencyKey())) {
                log.info("[Progress] Duplicate request detected for key {}. Returning existing progress.", dto.getIdempotencyKey());
                return toLearnerProgressDto(progress);
            }
            if (dto.isCompleted() && !progress.isCompleted()) {
                progress.setCompleted(true);
                progress.setCompletedAt(Instant.now());
            } else if (!dto.isCompleted()) {
                progress.setCompleted(false);
                progress.setCompletedAt(null);
            }
            progress.setSpeakingSeconds(progress.getSpeakingSeconds() + dto.getSpeakingSeconds());
            progress.setLastIdempotencyKey(dto.getIdempotencyKey());
        } else {
            if (dto.isCompleted() && !progress.isCompleted()) {
                progress.setCompleted(true);
                progress.setCompletedAt(Instant.now());
            } else if (!dto.isCompleted()) {
                progress.setCompleted(false);
                progress.setCompletedAt(null);
            }
            // Safe fallback: use max to prevent infinite accumulation on duplicate retries
            progress.setSpeakingSeconds(Math.max(progress.getSpeakingSeconds(), dto.getSpeakingSeconds()));
        }

        progress.setUpdatedAt(Instant.now());
        progress = progressRepo.save(progress);

        cacheService.evictLearnerProgress(learnerUserId);
        cacheService.clearAllMentorDashboards();

        return toLearnerProgressDto(progress);
    }

    @Cacheable(value = "learner_progress", key = "#learnerUserId")
    @Transactional(readOnly = true)
    public List<LearnerProgressResponseDto> getLearnerProgress(String learnerUserId) {
        return progressRepo.findByLearnerUserId(learnerUserId).stream()
                .map(this::toLearnerProgressDto)
                .toList();
    }

    @Cacheable(value = "mentor_dashboard", key = "#mentorId")
    @Transactional(readOnly = true)
    public MentorDashboardResponseDto getMentorDashboard(String mentorId) {
        MentorDataBundle data = buildMentorDataBundle(mentorId);

        List<MentorSessionDashboardDto> currentRooms = buildCurrentRooms(data.activeSessions, data.subLevelMap, data.pinnedMaterials);
        MentorDashboardResponseDto.CurrentSubLevelDto currentSubLevel =
                currentRooms.isEmpty() ? null : toCurrentSubLevel(currentRooms.get(0), data.subLevelMap);

        long totalLearners = data.distinctLearnerIds.size();
        long activeLearnersToday = data.activeLearnersToday.size();
        double attendanceRate = totalLearners == 0 ? 0.0 : (activeLearnersToday * 100.0) / totalLearners;

        return MentorDashboardResponseDto.builder()
                .mentorId(mentorId)
                .overview(MentorDashboardResponseDto.OverviewDto.builder()
                        .totalLearners(totalLearners)
                        .activeLearnersToday(activeLearnersToday)
                        .activeSessions(data.activeSessions.size())
                        .completedSessions(data.completedSessions.size())
                        .build())
                .attendance(MentorDashboardResponseDto.AttendanceDto.builder()
                        .averageAttendanceMinutes(data.averageAttendanceMinutes)
                        .attendanceRate(attendanceRate)
                        .build())
                .learning(MentorDashboardResponseDto.LearningDto.builder()
                        .completedSubLevels(data.completedSubLevels)
                        .activeSubLevels(data.activeSubLevels)
                        .progressByLevel(data.progressByLevel)
                        .build())
                .liveLearning(MentorDashboardResponseDto.LiveLearningDto.builder()
                        .currentRooms(currentRooms)
                        .currentSubLevel(currentSubLevel)
                        .pinnedMaterials(data.pinnedMaterials.stream().map(this::toPinnedMaterialDto).toList())
                        .build())
                .recordings(MentorDashboardResponseDto.RecordingsDto.builder()
                        .totalRecordings(data.totalRecordings)
                        .latestRecordings(data.latestRecordings.stream().map(this::toSessionRecordingDto).toList())
                        .playbackReadyCount(data.playbackReadyCount)
                        .build())
                .externalStatus(MentorDashboardResponseDto.ExternalStatusDto.builder()
                        .userProfileApiAvailable(false)
                        .realtimePresenceApiAvailable(false)
                        .build())
                .activeRoomCount(data.activeSessions.size())
                .totalSessions(data.allSessions.size())
                .learnersToday(activeLearnersToday)
                .averageAttendanceMinutes(data.averageAttendanceMinutes)
                .completedSubLevels(data.completedSubLevels)
                .currentSessions(currentRooms)
                .progressSummaryByLevel(data.progressByLevel)
                .pinnedMaterialCount(data.pinnedMaterials.size())
                .missingExternalData(MentorDashboardResponseDto.MissingExternalDataDto.builder()
                        .userProfileApi(true)
                        .realtimePresenceApi(true)
                        .build())
                .build();
    }

    @Transactional(readOnly = true)
    public List<MentorLearnerProgressDto> getMentorLearners(String mentorId) {
        MentorDataBundle data = buildMentorDataBundle(mentorId);
        Map<String, MentorLearnerProgressDto.MentorLearnerProgressDtoBuilder> builders = new LinkedHashMap<>();
        Instant since = startOfTodayUtc();

        for (RoomAttendance attendance : data.attendances) {
            builders.computeIfAbsent(attendance.getLearnerUserId(), learnerId ->
                    MentorLearnerProgressDto.builder()
                            .learnerUserId(learnerId)
                            .activeToday(false)
            );
            MentorLearnerProgressDto.MentorLearnerProgressDtoBuilder builder = builders.get(attendance.getLearnerUserId());
            long totalSeconds = attendance.getTotalSeconds();
            if (attendance.getLeftAt() == null) {
                totalSeconds = Duration.between(attendance.getJoinedAt(), Instant.now()).toSeconds();
            }
            MentorLearnerProgressDto current = builder.build();
            builder.totalSessionsJoined(current.getTotalSessionsJoined() + 1);
            builder.totalAttendanceMinutes(current.getTotalAttendanceMinutes() + (totalSeconds / 60.0));
            if (attendance.getJoinedAt() != null && !attendance.getJoinedAt().isBefore(since)) {
                builder.activeToday(true);
            }
        }

        for (LearnerProgress progress : data.progressEntries) {
            builders.computeIfAbsent(progress.getLearnerUserId(), learnerId ->
                    MentorLearnerProgressDto.builder().learnerUserId(learnerId).activeToday(false));
            MentorLearnerProgressDto current = builders.get(progress.getLearnerUserId()).build();
            builders.get(progress.getLearnerUserId())
                    .completedSubLevels(current.getCompletedSubLevels() + (progress.isCompleted() ? 1 : 0))
                    .totalSpeakingSeconds(current.getTotalSpeakingSeconds() + progress.getSpeakingSeconds())
                    .totalSessionsJoined(current.getTotalSessionsJoined())
                    .totalAttendanceMinutes(current.getTotalAttendanceMinutes())
                    .activeToday(current.isActiveToday());
        }

        return builders.values().stream()
                .map(MentorLearnerProgressDto.MentorLearnerProgressDtoBuilder::build)
                .sorted(Comparator.comparing(MentorLearnerProgressDto::getLearnerUserId))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<MentorSessionDashboardDto> getMentorSessions(String mentorId) {
        MentorDataBundle data = buildMentorDataBundle(mentorId);
        return buildCurrentRooms(data.allSessions, data.subLevelMap, data.pinnedMaterials);
    }

    @Transactional(readOnly = true)
    public List<SessionRecordingDto> getMentorRecordings(String mentorId) {
        return sessionRecordingRepository.findByMentorUserId(mentorId).stream()
                .map(this::toSessionRecordingDto)
                .toList();
    }

    private MentorDataBundle buildMentorDataBundle(String mentorId) {
        List<RoomLearningSession> sessions = sessionRepo.findByMentorUserId(mentorId);
        List<UUID> sessionIds = sessions.stream().map(RoomLearningSession::getId).toList();
        List<Long> levelIds = sessions.stream().map(RoomLearningSession::getLevelId).distinct().toList();
        List<Long> subLevelIds = sessions.stream().map(RoomLearningSession::getCurrentSubLevelId).distinct().toList();

        List<RoomAttendance> attendances = sessionIds.isEmpty() ? List.of() : attendanceRepo.findByRoomSessionIdIn(sessionIds);
        Set<String> learnerIds = attendances.stream().map(RoomAttendance::getLearnerUserId).collect(Collectors.toCollection(LinkedHashSet::new));
        List<LearnerProgress> progressEntries = learnerIds.isEmpty() ? List.of() : progressRepo.findByLearnerUserIdIn(learnerIds);
        List<PinnedMaterial> pinnedMaterials = sessionIds.isEmpty() ? List.of() :
                pinnedMaterialRepo.findByRoomSessionIdInAndActiveTrueOrderByPinnedAtDesc(sessionIds);

        Map<Long, SubLevel> subLevelMap = subLevelIds.isEmpty() ? Map.of() :
                subLevelRepository.findAllById(subLevelIds).stream()
                        .collect(Collectors.toMap(SubLevel::getId, Function.identity()));

        List<RoomLearningSession> activeSessions = sessions.stream()
                .filter(session -> session.getStatus() != SessionStatus.ENDED)
                .sorted(Comparator.comparing(RoomLearningSession::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();

        List<RoomLearningSession> completedSessions = sessions.stream()
                .filter(session -> session.getStatus() == SessionStatus.ENDED)
                .toList();

        long activeSubLevels = activeSessions.stream()
                .map(RoomLearningSession::getCurrentSubLevelId)
                .distinct()
                .count();

        Set<String> activeLearnersToday = attendances.stream()
                .filter(attendance -> attendance.getJoinedAt() != null && !attendance.getJoinedAt().isBefore(startOfTodayUtc()))
                .map(RoomAttendance::getLearnerUserId)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        long completedSubLevels = progressEntries.stream().filter(LearnerProgress::isCompleted).count();
        double averageAttendanceMinutes = attendances.stream()
                .filter(attendance -> attendance.getJoinedAt() != null)
                .mapToLong(attendance -> {
                    if (attendance.getLeftAt() != null) {
                        return attendance.getTotalSeconds();
                    }
                    return Duration.between(attendance.getJoinedAt(), Instant.now()).toSeconds();
                })
                .average()
                .orElse(0.0) / 60.0;

        List<LevelProgressSummaryDto> progressByLevel = progressRepo.getProgressSummaryByLevelForMentor(mentorId);
        List<SessionRecording> latestRecordings = sessionRecordingRepository.findLatestByMentorUserId(mentorId, PageRequest.of(0, 5));
        long totalRecordings = sessionIds.isEmpty() ? 0 : sessionRecordingRepository.countByRoomSessionIdIn(sessionIds);
        long playbackReadyCount = sessionIds.isEmpty() ? 0 :
                sessionRecordingRepository.countByRoomSessionIdInAndStatus(sessionIds, SessionRecordingStatus.READY);

        return new MentorDataBundle(
                sessions,
                activeSessions,
                completedSessions,
                attendances,
                progressEntries,
                pinnedMaterials,
                subLevelMap,
                learnerIds,
                activeLearnersToday,
                averageAttendanceMinutes,
                completedSubLevels,
                activeSubLevels,
                progressByLevel,
                latestRecordings,
                totalRecordings,
                playbackReadyCount
        );
    }

    private List<MentorSessionDashboardDto> buildCurrentRooms(
            List<RoomLearningSession> sessions,
            Map<Long, SubLevel> subLevelMap,
            List<PinnedMaterial> pinnedMaterials
    ) {
        Map<UUID, Long> pinnedCountBySession = pinnedMaterials.stream()
                .collect(Collectors.groupingBy(PinnedMaterial::getRoomSessionId, Collectors.counting()));

        return sessions.stream()
                .map(session -> MentorSessionDashboardDto.builder()
                        .sessionId(session.getId())
                        .status(session.getStatus().name())
                        .channelName(session.getChannelName())
                        .realtimeRoomId(session.getRealtimeRoomId())
                        .realtimeAgoraChannelName(session.getRealtimeAgoraChannelName())
                        .levelId(session.getLevelId())
                        .currentSubLevelId(session.getCurrentSubLevelId())
                        .currentSubLevelTopic(Optional.ofNullable(subLevelMap.get(session.getCurrentSubLevelId()))
                                .map(SubLevel::getTopic)
                                .orElse(null))
                        .startedAt(session.getStartedAt())
                        .endedAt(session.getEndedAt())
                        .pinnedMaterialCount(pinnedCountBySession.getOrDefault(session.getId(), 0L))
                        .build())
                .toList();
    }

    private MentorDashboardResponseDto.CurrentSubLevelDto toCurrentSubLevel(
            MentorSessionDashboardDto room,
            Map<Long, SubLevel> subLevelMap
    ) {
        SubLevel subLevel = subLevelMap.get(room.getCurrentSubLevelId());
        return MentorDashboardResponseDto.CurrentSubLevelDto.builder()
                .sessionId(room.getSessionId())
                .subLevelId(room.getCurrentSubLevelId())
                .topic(subLevel != null ? subLevel.getTopic() : null)
                .durationMinutes(subLevel != null ? subLevel.getDurationMinutes() : null)
                .build();
    }

    private LearnerProgressResponseDto toLearnerProgressDto(LearnerProgress progress) {
        return LearnerProgressResponseDto.builder()
                .learnerUserId(progress.getLearnerUserId())
                .levelId(progress.getLevelId())
                .subLevelId(progress.getSubLevelId())
                .completed(progress.isCompleted())
                .completedAt(progress.getCompletedAt())
                .speakingSeconds(progress.getSpeakingSeconds())
                .updatedAt(progress.getUpdatedAt())
                .build();
    }

    private SessionRecordingDto toSessionRecordingDto(SessionRecording recording) {
        return SessionRecordingDto.builder()
                .recordingId(recording.getRecordingId())
                .roomSessionId(recording.getRoomSessionId())
                .playbackUrl(recording.getPlaybackUrl())
                .durationSeconds(recording.getDurationSeconds())
                .status(recording.getStatus().name())
                .createdAt(recording.getCreatedAt())
                .startedAt(recording.getStartedAt())
                .endedAt(recording.getEndedAt())
                .provider(recording.getProvider())
                .build();
    }

    private RoomSessionStateDto.PinnedMaterialDto toPinnedMaterialDto(PinnedMaterial material) {
        return RoomSessionStateDto.PinnedMaterialDto.builder()
                .id(material.getId())
                .title(material.getTitle())
                .materialType(material.getMaterialType().name())
                .url(material.getUrl())
                .displayOrder(material.getDisplayOrder())
                .active(material.isActive())
                .pinnedByUserId(material.getPinnedByUserId())
                .pinnedAt(material.getPinnedAt())
                .build();
    }

    private RoomLearningSession getSession(UUID sessionId) {
        return sessionRepo.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found: " + sessionId));
    }

    private Instant startOfTodayUtc() {
        return LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    private record MentorDataBundle(
            List<RoomLearningSession> allSessions,
            List<RoomLearningSession> activeSessions,
            List<RoomLearningSession> completedSessions,
            List<RoomAttendance> attendances,
            List<LearnerProgress> progressEntries,
            List<PinnedMaterial> pinnedMaterials,
            Map<Long, SubLevel> subLevelMap,
            Set<String> distinctLearnerIds,
            Set<String> activeLearnersToday,
            double averageAttendanceMinutes,
            long completedSubLevels,
            long activeSubLevels,
            List<LevelProgressSummaryDto> progressByLevel,
            List<SessionRecording> latestRecordings,
            long totalRecordings,
            long playbackReadyCount
    ) {
    }
}
