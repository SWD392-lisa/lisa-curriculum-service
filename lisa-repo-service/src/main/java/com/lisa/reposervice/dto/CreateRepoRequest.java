package com.lisa.reposervice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import java.util.List;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
@Schema(description = "Request tạo repo tùy chỉnh")
public class CreateRepoRequest {

    @Schema(description = "Tên repo (không có space)", example = "lisa-notification-service", required = true)
    private String name;

    @Schema(description = "Mô tả repo", example = "Push notification service for LISA app")
    private String description;

    @Schema(description = "true = private repo", example = "true", defaultValue = "true")
    private boolean privateRepo;

    @Schema(description = "Topics/tags cho repo", example = "[\"java\", \"spring-boot\"]")
    private List<String> topics;

    @Schema(description = "Platform: GITHUB | GITLAB | BOTH", example = "BOTH",
            allowableValues = {"GITHUB", "GITLAB", "BOTH"}, defaultValue = "BOTH")
    private String platform;

    @Schema(description = "true = tự tạo README.md", example = "true", defaultValue = "true")
    private boolean initReadme;
}
