package com.lisa.curriculum.dto;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LearnerProgressRequestDto {
    private java.util.UUID sessionId;
    private Long levelId;
    private Long subLevelId;
    private boolean completed;
    private int speakingSeconds;
    private String idempotencyKey;
}
