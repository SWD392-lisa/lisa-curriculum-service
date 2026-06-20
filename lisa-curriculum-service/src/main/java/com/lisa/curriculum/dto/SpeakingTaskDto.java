package com.lisa.curriculum.dto;

import com.lisa.curriculum.entity.TaskType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

@Data @Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Một speaking task trong sub-level")
public class SpeakingTaskDto {

    @Schema(description = "ID trong database", example = "101")
    private Long id;

    @Schema(description = "Loại task", example = "QUESTION",
            allowableValues = {"QUESTION", "ANSWER", "BULLET"})
    private TaskType taskType;

    @Schema(description = "Nội dung task", example = "What do you usually do in the morning?")
    private String content;

    @Schema(description = "Phiên âm (pinyin cho tiếng Trung, romaji cho tiếng Nhật)",
            example = "nǐ zǎoshang tōngcháng zuò shénme?")
    private String pronunciation;

    @Schema(description = "Thứ tự hiển thị", example = "0")
    private int orderIndex;
}
