package com.lisa.curriculum.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SpeakingAssessmentResponseDto {
    private Long taskId;
    private String transcript;
    private int overallScore;
    private int relevanceScore;
    private int grammarScore;
    private int vocabularyScore;
    private String feedback;
    private String suggestedAnswer;
    @JsonProperty("isPersonalBest")
    private boolean personalBest;
    private int speakingSeconds;
    private Instant assessedAt;
}
