package com.lisa.curriculum.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "speaking_assessments",
        uniqueConstraints = @UniqueConstraint(columnNames = {"learner_user_id", "task_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SpeakingAssessment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "learner_user_id", nullable = false)
    private String learnerUserId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id", nullable = false)
    private SpeakingTask task;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String transcript;

    @Column(name = "overall_score", nullable = false)
    private int overallScore;

    @Column(name = "relevance_score", nullable = false)
    private int relevanceScore;

    @Column(name = "grammar_score", nullable = false)
    private int grammarScore;

    @Column(name = "vocabulary_score", nullable = false)
    private int vocabularyScore;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String feedback;

    @Column(name = "suggested_answer", columnDefinition = "TEXT", nullable = false)
    private String suggestedAnswer;

    @Column(name = "speaking_seconds", nullable = false)
    private int speakingSeconds;

    @Builder.Default
    @Column(name = "assessed_at", nullable = false)
    private Instant assessedAt = Instant.now();
}
