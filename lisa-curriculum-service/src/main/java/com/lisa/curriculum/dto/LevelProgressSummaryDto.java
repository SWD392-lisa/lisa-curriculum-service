package com.lisa.curriculum.dto;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
public class LevelProgressSummaryDto {
    private Long levelId;
    private int levelNumber;
    private String levelTitle;
    private long completedSubLevelsCount;
    private double averageSpeakingSeconds;

    public LevelProgressSummaryDto(Long levelId, int levelNumber, String levelTitle, Long completedSubLevelsCount, Double averageSpeakingSeconds) {
        this.levelId = levelId;
        this.levelNumber = levelNumber;
        this.levelTitle = levelTitle;
        this.completedSubLevelsCount = completedSubLevelsCount != null ? completedSubLevelsCount : 0L;
        this.averageSpeakingSeconds = averageSpeakingSeconds != null ? averageSpeakingSeconds : 0.0;
    }
}
