package com.lisa.curriculum.repository;

import com.lisa.curriculum.entity.RoomLearningSession;
import com.lisa.curriculum.entity.SessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RoomLearningSessionRepository extends JpaRepository<RoomLearningSession, UUID> {
    List<RoomLearningSession> findByStatusAndAutoSwitchEnabledTrue(SessionStatus status);
    long countByMentorUserIdAndStatus(String mentorUserId, SessionStatus status);
    long countByMentorUserId(String mentorUserId);
    List<RoomLearningSession> findByMentorUserId(String mentorUserId);
    List<RoomLearningSession> findByMentorUserIdAndStatusNot(String mentorUserId, SessionStatus status);
}
