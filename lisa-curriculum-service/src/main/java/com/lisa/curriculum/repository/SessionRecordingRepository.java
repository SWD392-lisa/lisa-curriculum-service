package com.lisa.curriculum.repository;

import com.lisa.curriculum.entity.SessionRecording;
import com.lisa.curriculum.entity.SessionRecordingStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface SessionRecordingRepository extends JpaRepository<SessionRecording, String> {
    List<SessionRecording> findByRoomSessionIdOrderByCreatedAtDesc(UUID roomSessionId);

    long countByRoomSessionIdIn(Collection<UUID> roomSessionIds);

    long countByRoomSessionIdInAndStatus(Collection<UUID> roomSessionIds, SessionRecordingStatus status);

    @Query("SELECT sr FROM SessionRecording sr WHERE sr.roomSessionId IN " +
            "(SELECT s.id FROM RoomLearningSession s WHERE s.mentorUserId = :mentorId) ORDER BY sr.createdAt DESC")
    List<SessionRecording> findByMentorUserId(String mentorId);

    @Query("SELECT sr FROM SessionRecording sr WHERE sr.roomSessionId IN " +
            "(SELECT s.id FROM RoomLearningSession s WHERE s.mentorUserId = :mentorId) ORDER BY sr.createdAt DESC")
    List<SessionRecording> findLatestByMentorUserId(String mentorId, Pageable pageable);
}
