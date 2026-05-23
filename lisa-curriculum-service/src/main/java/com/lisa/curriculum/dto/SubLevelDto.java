package com.lisa.curriculum.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data @Builder
@Schema(description = "Sub-level (10–20 phút) trong 1 Level")
public class SubLevelDto {

    @Schema(description = "ID trong database", example = "12")
    private Long id;

    @Schema(description = "Số thứ tự sub-level trong level", example = "2")
    private int subNumber;

    @Schema(description = "Chủ đề sub-level", example = "My typical morning")
    private String topic;

    @Schema(description = "Thời gian (phút)", example = "10")
    private int durationMinutes;

    @Schema(description = "Danh sách speaking tasks (questions/answers/bullets)")
    private List<SpeakingTaskDto> tasks;
}
