package com.lisa.curriculum.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data @Builder
@Schema(description = "Kết quả import file Word vào database")
public class ImportResultDto {

    @Schema(description = "Ngôn ngữ đã import", example = "ENGLISH")
    private String language;

    @Schema(description = "Số levels đã import thành công", example = "30")
    private int levelsImported;

    @Schema(description = "Tổng số sub-levels", example = "180")
    private int subLevelsImported;

    @Schema(description = "Tổng số speaking tasks", example = "720")
    private int tasksImported;

    @Schema(description = "Danh sách cảnh báo (level bị skip hoặc overwrite)")
    private List<String> warnings;

    @Schema(description = "Thời gian xử lý (ms)", example = "1240")
    private long durationMs;
}
