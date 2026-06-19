package com.lisa.curriculum.scheduler;

import com.lisa.curriculum.entity.RoomLearningSession;
import com.lisa.curriculum.entity.SessionStatus;
import com.lisa.curriculum.entity.SubLevel;
import com.lisa.curriculum.repository.RoomLearningSessionRepository;
import com.lisa.curriculum.repository.SubLevelRepository;
import com.lisa.curriculum.service.RoomLearningSessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Slf4j
@Configuration
@EnableScheduling
@RequiredArgsConstructor
public class RoomSessionAutoSwitchScheduler {

    private final RoomLearningSessionRepository sessionRepo;
    private final SubLevelRepository subLevelRepo;
    private final RoomLearningSessionService sessionService;

    @Scheduled(fixedDelayString = "${lms.session.auto-switch-scan-interval-ms:60000}")
    public void scanAndAutoSwitchRooms() {
        log.debug("Auto-switch scanner started...");
        List<RoomLearningSession> liveSessions = sessionRepo.findByStatusAndAutoSwitchEnabledTrue(SessionStatus.LIVE);

        for (RoomLearningSession session : liveSessions) {
            try {
                if (session.getSubLevelStartedAt() == null) {
                    continue;
                }

                SubLevel subLevel = subLevelRepo.findById(session.getCurrentSubLevelId()).orElse(null);
                if (subLevel == null) {
                    continue;
                }

                long elapsedSeconds = Duration.between(session.getSubLevelStartedAt(), Instant.now()).getSeconds();
                long totalDurationSeconds = (long) subLevel.getDurationMinutes() * 60;

                if (elapsedSeconds >= totalDurationSeconds) {
                    log.info("Auto-switch scanner detected expired sublevel for session {}. Elapsed: {}s, Allowed: {}s. Switching...", 
                            session.getId(), elapsedSeconds, totalDurationSeconds);
                    sessionService.switchToNextSubLevel(session.getId(), "Auto-switch scanner duration expired");
                }
            } catch (ObjectOptimisticLockingFailureException e) {
                log.warn("Optimistic lock failure during auto-switch scanner for session: {}. Handled by duplicate instance prevention.", 
                        session.getId());
            } catch (Exception e) {
                log.error("Error processing auto-switch for session: {}", session.getId(), e);
            }
        }
    }
}
