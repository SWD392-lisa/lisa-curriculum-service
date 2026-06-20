package com.lisa.curriculum.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "learner_progress",
       uniqueConstraints = @UniqueConstraint(columnNames = {"learner_user_id", "level_id", "sub_level_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LearnerProgress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "learner_user_id", nullable = false)
    private String learnerUserId;

    @Column(name = "level_id", nullable = false)
    private Long levelId;

    @Column(name = "sub_level_id", nullable = false)
    private Long subLevelId;

    @Column(nullable = false)
    private boolean completed;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "speaking_seconds", nullable = false)
    private int speakingSeconds;

    @Column(name = "last_idempotency_key")
    private String lastIdempotencyKey;

    @Builder.Default
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
