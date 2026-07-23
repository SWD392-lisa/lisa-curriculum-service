package com.lisa.curriculum.repository;

import com.lisa.curriculum.entity.SpeakingAssessment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;

@Repository
public interface SpeakingAssessmentRepository extends JpaRepository<SpeakingAssessment, Long> {
    Optional<SpeakingAssessment> findByLearnerUserIdAndTaskId(String learnerUserId, Long taskId);

    @Query("SELECT CASE WHEN COUNT(a) > 0 THEN true ELSE false END " +
            "FROM SpeakingAssessment a WHERE a.learnerUserId = :learnerUserId " +
            "AND a.task.subLevel.id = :subLevelId")
    boolean existsForLearnerAndSubLevel(String learnerUserId, Long subLevelId);

    @Query("SELECT COALESCE(SUM(a.speakingSeconds), 0) FROM SpeakingAssessment a " +
            "WHERE a.learnerUserId = :learnerUserId AND a.task.subLevel.id = :subLevelId")
    long sumSpeakingSecondsForLearnerAndSubLevel(String learnerUserId, Long subLevelId);

    @Query("SELECT a FROM SpeakingAssessment a JOIN FETCH a.task " +
            "WHERE a.learnerUserId = :learnerUserId AND a.task.subLevel.id = :subLevelId " +
            "ORDER BY a.task.orderIndex ASC")
    List<SpeakingAssessment> findBestForLearnerAndSubLevel(String learnerUserId, Long subLevelId);
}
