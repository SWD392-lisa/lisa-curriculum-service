package com.lisa.curriculum.dto;

import lombok.*;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LearnerProgressResponseDto {
    private String learnerUserId;
    private Long levelId;
    private Long subLevelId;
    private boolean completed;
    private Instant completedAt;
    private int speakingSeconds;
    private Instant updatedAt;
}
