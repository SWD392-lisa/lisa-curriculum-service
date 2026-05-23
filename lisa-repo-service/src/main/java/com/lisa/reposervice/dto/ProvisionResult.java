package com.lisa.reposervice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import java.util.List;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
@Schema(description = "Kết quả provision toàn bộ repos")
public class ProvisionResult {

    @Schema(description = "Tổng số repo được yêu cầu tạo", example = "12")
    private int totalRequested;

    @Schema(description = "Số repo tạo thành công", example = "12")
    private int totalSuccess;

    @Schema(description = "Số repo thất bại (đã tồn tại hoặc lỗi API)", example = "0")
    private int totalFailed;

    @Schema(description = "Chi tiết kết quả từng repo")
    private List<RepoResult> results;
}
