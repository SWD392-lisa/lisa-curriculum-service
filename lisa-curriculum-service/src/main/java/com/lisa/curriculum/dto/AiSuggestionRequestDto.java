package com.lisa.curriculum.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AiSuggestionRequestDto {
    @NotBlank
    private String language;

    private Integer stage;
    private Long levelId;
    private Integer levelNumber;

    @NotBlank
    private String topic;

    @NotBlank
    private String task;

    @Min(1)
    @Max(5)
    private Integer count = 3;
}
