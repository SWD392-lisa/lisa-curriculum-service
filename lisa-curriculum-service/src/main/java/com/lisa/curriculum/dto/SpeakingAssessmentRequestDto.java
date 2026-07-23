package com.lisa.curriculum.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SpeakingAssessmentRequestDto {
    @NotNull
    private Long taskId;

    @NotBlank
    @Size(max = 4000)
    private String transcript;

    @Min(1)
    @Max(600)
    private int speakingSeconds;
}
