package com.lisa.curriculum.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.Instant;
import java.util.List;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class SupportAiResponseDto {
    private String answer;
    private List<SupportLinkDto> suggestedLinks;
    private String provider;
    private String model;
    private Instant answeredAt;

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class SupportLinkDto {
        private String label;
        private String path;
    }
}
