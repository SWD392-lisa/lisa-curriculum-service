package com.lisa.curriculum.repository;

import com.lisa.curriculum.entity.SpeakingTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.util.Optional;

@Repository
public interface SpeakingTaskRepository extends JpaRepository<SpeakingTask, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT task FROM SpeakingTask task WHERE task.id = :id")
    Optional<SpeakingTask> findForAssessmentById(Long id);
}
