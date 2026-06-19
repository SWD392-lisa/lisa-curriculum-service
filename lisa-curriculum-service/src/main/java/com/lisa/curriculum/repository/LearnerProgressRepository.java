package com.lisa.curriculum.repository;

import com.lisa.curriculum.dto.LevelProgressSummaryDto;
import com.lisa.curriculum.entity.LearnerProgress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Collection;
import java.util.Optional;

@Repository
public interface LearnerProgressRepository extends JpaRepository<LearnerProgress, Long> {
    List<LearnerProgress> findByLearnerUserId(String learnerUserId);
    List<LearnerProgress> findByLearnerUserIdIn(Collection<String> learnerUserIds);
    Optional<LearnerProgress> findByLearnerUserIdAndSubLevelId(String learnerUserId, Long subLevelId);
    List<LearnerProgress> findByLevelIdIn(Collection<Long> levelIds);

    @Query("SELECT COUNT(lp) FROM LearnerProgress lp WHERE lp.completed = true AND lp.levelId IN (SELECT DISTINCT s.levelId FROM RoomLearningSession s WHERE s.mentorUserId = :mentorId)")
    long countCompletedSubLevelsForMentor(String mentorId);

    @Query("SELECT new com.lisa.curriculum.dto.LevelProgressSummaryDto(lp.levelId, l.levelNumber, l.title, SUM(CASE WHEN lp.completed = true THEN 1 ELSE 0 END), AVG(lp.speakingSeconds)) " +
           "FROM LearnerProgress lp JOIN Level l ON lp.levelId = l.id " +
           "WHERE lp.levelId IN (SELECT DISTINCT s.levelId FROM RoomLearningSession s WHERE s.mentorUserId = :mentorId) " +
           "GROUP BY lp.levelId, l.levelNumber, l.title")
    List<LevelProgressSummaryDto> getProgressSummaryByLevelForMentor(String mentorId);
}
