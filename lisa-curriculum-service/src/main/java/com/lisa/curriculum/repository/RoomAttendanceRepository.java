package com.lisa.curriculum.repository;

import com.lisa.curriculum.entity.RoomAttendance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RoomAttendanceRepository extends JpaRepository<RoomAttendance, Long> {
    Optional<RoomAttendance> findFirstByRoomSessionIdAndLearnerUserIdAndLeftAtIsNullOrderByJoinedAtDesc(UUID roomSessionId, String learnerUserId);

    @Query("SELECT COUNT(DISTINCT ra.learnerUserId) FROM RoomAttendance ra WHERE ra.roomSessionId IN (SELECT s.id FROM RoomLearningSession s WHERE s.mentorUserId = :mentorId)")
    long countDistinctLearnersForMentor(String mentorId);

    @Query("SELECT COUNT(DISTINCT ra.learnerUserId) FROM RoomAttendance ra WHERE ra.joinedAt >= :sinceTime AND ra.roomSessionId IN (SELECT s.id FROM RoomLearningSession s WHERE s.mentorUserId = :mentorId)")
    long countDistinctLearnersForMentorSince(String mentorId, Instant sinceTime);

    @Query("SELECT COALESCE(AVG(ra.totalSeconds) / 60.0, 0.0) FROM RoomAttendance ra WHERE ra.leftAt IS NOT NULL AND ra.roomSessionId IN (SELECT s.id FROM RoomLearningSession s WHERE s.mentorUserId = :mentorId)")
    double getAverageAttendanceMinutesForMentor(String mentorId);
}
