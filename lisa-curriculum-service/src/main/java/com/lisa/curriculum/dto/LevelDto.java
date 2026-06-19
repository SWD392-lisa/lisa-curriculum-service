package com.lisa.curriculum.dto;

import com.lisa.curriculum.entity.Language;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data @Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Một level trong hệ thống curriculum LISA")
public class LevelDto {

    @Schema(description = "ID trong database", example = "1")
    private Long id;

    @Schema(description = "Ngôn ngữ", example = "ENGLISH")
    private Language language;

    @Schema(description = "Stage (1=beginner, 2=intermediate, 3=advanced)", example = "1")
    private int stage;

    @Schema(description = "Số thứ tự level (1–100)", example = "5")
    private int levelNumber;

    @Schema(description = "Tiêu đề level", example = "SAYING WHO I AM")
    private String title;

    @Schema(description = "CEFR target", example = "A1")
    private String cefrTarget;

    @Schema(description = "Thời gian học (phút)", example = "60")
    private int durationMinutes;

    @Schema(description = "Nhóm level", example = "SURVIVAL SPEAKING (A1 – Absolute Beginner)")
    private String groupLabel;

    @Schema(description = "Danh sách sub-levels (chỉ có khi gọi GET /levels/{id})")
    private List<SubLevelDto> subLevels;
}
