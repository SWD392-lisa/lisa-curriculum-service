package com.lisa.reposervice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
@Schema(description = "Kết quả tạo repo trên 1 platform")
public class RepoResult {

    @Schema(description = "Platform", example = "GITHUB", allowableValues = {"GITHUB","GITLAB"})
    private String platform;

    @Schema(description = "Tên repo", example = "lisa-lms-service")
    private String repoName;

    @Schema(description = "URL trang web repo", example = "https://github.com/org/lisa-lms-service")
    private String repoUrl;

    @Schema(description = "Clone URL (HTTPS)", example = "https://github.com/org/lisa-lms-service.git")
    private String cloneUrlHttps;

    @Schema(description = "Clone URL (SSH)", example = "git@github.com:org/lisa-lms-service.git")
    private String cloneUrlSsh;

    @Schema(description = "true = tạo thành công", example = "true")
    private boolean success;

    @Schema(description = "Thông báo lỗi (null nếu success)", example = "null")
    private String errorMessage;
}
