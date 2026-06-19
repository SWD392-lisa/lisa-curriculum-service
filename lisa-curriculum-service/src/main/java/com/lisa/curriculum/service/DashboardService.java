package com.lisa.curriculum.service;

import com.lisa.curriculum.dto.*;
import com.lisa.curriculum.entity.*;
import com.lisa.curriculum.exception.ResourceNotFoundException;
import com.lisa.curriculum.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardService {

    private final RoomLearningSessionRepository sessionRepo;
    private final RoomAttendanceRepository attendanceRepo;
    private final LearnerProgressRepository progressRepo;
    private final PinnedMaterialRepository pinnedMaterialRepo;
    private final CacheManager cacheManager;

    // ─── ATTENDANCE ─────────────────────────────────────────

    @Transactional
    public void recordAttendanceJoin(UUID sessionId, String learnerUserId) {
        RoomLearningSession session = sessionRepo.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found: " + sessionId));

        Optional<RoomAttendance> activeOpt = attendanceRepo
                .findFirstByRoomSessionIdAndLearnerUserIdAndLeftAtIsNullOrderByJoinedAtDesc(sessionId, learnerUserId);

        if (activeOpt.isEmpty()) {
            RoomAttendance attendance = RoomAttendance.builder()
                    .roomSessionId(sessionId)
                    .learnerUserId(learnerUserId)
                    .joinedAt(Instant.now())
                    .build();
            attendanceRepo.save(attendance);
            log.info("[Attendance] Learner {} joined session {}", learnerUserId, sessionId);
        }

        // Evict caches
        evictLearnerProgress(learnerUserId);
        evictMentorDashboard(session.getMentorUserId());
    }

    @Transactional
    public void recordAttendanceLeave(UUID sessionId, String learnerUserId) {
        RoomLearningSession session = sessionRepo.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found: " + sessionId));

        RoomAttendance attendance = attendanceRepo
                .findFirstByRoomSessionIdAndLearnerUserIdAndLeftAtIsNullOrderByJoinedAtDesc(sessionId, learnerUserId)
                .orElseThrow(() -> new ResourceNotFoundException("No active attendance record found for user " + learnerUserId + " in session " + sessionId));

        attendance.setLeftAt(Instant.now());
        attendance.setTotalSeconds(java.time.Duration.between(attendance.getJoinedAt(), attendance.getLeftAt()).toSeconds());
        attendanceRepo.save(attendance);
        log.info("[Attendance] Learner {} left session {} after {} seconds", learnerUserId, sessionId, attendance.getTotalSeconds());

        // Evict caches
        evictLearnerProgress(learnerUserId);
        evictMentorDashboard(session.getMentorUserId());
    }

    // ─── PROGRESS ───────────────────────────────────────────

    @Transactional
    public LearnerProgressResponseDto saveLearnerProgress(String learnerUserId, LearnerProgressRequestDto dto) {
        LearnerProgress progress = progressRepo.findByLearnerUserIdAndSubLevelId(learnerUserId, dto.getSubLevelId())
                .orElseGet(() -> LearnerProgress.builder()
                        .learnerUserId(learnerUserId)
                        .levelId(dto.getLevelId())
                        .subLevelId(dto.getSubLevelId())
                        .build());

        if (dto.isCompleted() && !progress.isCompleted()) {
            progress.setCompleted(true);
            progress.setCompletedAt(Instant.now());
        } else if (!dto.isCompleted()) {
            progress.setCompleted(false);
            progress.setCompletedAt(null);
        }

        progress.setSpeakingSeconds(progress.getSpeakingSeconds() + dto.getSpeakingSeconds());
        progress.setUpdatedAt(Instant.now());
        progress = progressRepo.save(progress);

        log.info("[Progress] Learner {} progress updated for sublevel {}: completed={}, speakingSeconds={}",
                learnerUserId, dto.getSubLevelId(), progress.isCompleted(), progress.getSpeakingSeconds());

        // Evict caches
        evictLearnerProgress(learnerUserId);
        clearAllMentorDashboards();

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

    @Cacheable(value = "learner_progress", key = "#learnerUserId")
    @Transactional(readOnly = true)
    public List<LearnerProgressResponseDto> getLearnerProgress(String learnerUserId) {
        return progressRepo.findByLearnerUserId(learnerUserId).stream()
                .map(p -> LearnerProgressResponseDto.builder()
                        .learnerUserId(p.getLearnerUserId())
                        .levelId(p.getLevelId())
                        .subLevelId(p.getSubLevelId())
                        .completed(p.isCompleted())
                        .completedAt(p.getCompletedAt())
                        .speakingSeconds(p.getSpeakingSeconds())
                        .updatedAt(p.getUpdatedAt())
                        .build())
                .collect(Collectors.toList());
    }

    // ─── DASHBOARD ──────────────────────────────────────────

    @Cacheable(value = "mentor_dashboard", key = "#mentorId")
    @Transactional(readOnly = true)
    public MentorDashboardResponseDto getMentorDashboard(String mentorId) {
        long activeRoomCount = sessionRepo.countByMentorUserIdAndStatus(mentorId, SessionStatus.LIVE);
        long totalSessions = sessionRepo.countByMentorUserId(mentorId);
        long totalLearners = attendanceRepo.countDistinctLearnersForMentor(mentorId);

        Instant sinceTime = LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant();
        long learnersToday = attendanceRepo.countDistinctLearnersForMentorSince(mentorId, sinceTime);

        double averageAttendanceMinutes = attendanceRepo.getAverageAttendanceMinutesForMentor(mentorId);
        long completedSubLevels = progressRepo.countCompletedSubLevelsForMentor(mentorId);
        long pinnedMaterialCount = pinnedMaterialRepo.countActivePinnedMaterialsForMentor(mentorId);

        List<RoomLearningSession> currentSessions = sessionRepo.findByMentorUserIdAndStatusNot(mentorId, SessionStatus.ENDED);
        List<RoomSessionResponseDto> sessionDtos = currentSessions.stream()
                .map(s -> RoomSessionResponseDto.builder()
                        .sessionId(s.getId())
                        .channelName(s.getChannelName())
                        .status(s.getStatus().name())
                        .levelId(s.getLevelId())
                        .currentSubLevelId(s.getCurrentSubLevelId())
                        .build())
                .collect(Collectors.toList());

        List<LevelProgressSummaryDto> summaries = progressRepo.getProgressSummaryByLevelForMentor(mentorId);

        log.info("[Dashboard] Mentor {} loaded: activeRooms={}, totalSessions={}", mentorId, activeRoomCount, totalSessions);

        return MentorDashboardResponseDto.builder()
                .mentorId(mentorId)
                .activeRoomCount(activeRoomCount)
                .totalSessions(totalSessions)
                .totalLearners(totalLearners)
                .learnersToday(learnersToday)
                .averageAttendanceMinutes(averageAttendanceMinutes)
                .completedSubLevels(completedSubLevels)
                .pinnedMaterialCount(pinnedMaterialCount)
                .currentSessions(sessionDtos)
                .progressSummaryByLevel(summaries)
                .build();
    }

    // ─── PRIVATE EVICTIONS ──────────────────────────────────

    public void evictMentorDashboard(String mentorId) {
        org.springframework.cache.Cache cache = cacheManager.getCache("mentor_dashboard");
        if (cache != null && mentorId != null) {
            cache.evict(mentorId);
            log.debug("[Cache] Evicted mentor_dashboard cache for mentor {}", mentorId);
        }
    }

    public void evictLearnerProgress(String learnerId) {
        org.springframework.cache.Cache cache = cacheManager.getCache("learner_progress");
        if (cache != null && learnerId != null) {
            cache.evict(learnerId);
            log.debug("[Cache] Evicted learner_progress cache for learner {}", learnerId);
        }
    }

    public void clearAllMentorDashboards() {
        org.springframework.cache.Cache cache = cacheManager.getCache("mentor_dashboard");
        if (cache != null) {
            cache.clear();
            log.debug("[Cache] Cleared all mentor_dashboard caches");
        }
    }
}
