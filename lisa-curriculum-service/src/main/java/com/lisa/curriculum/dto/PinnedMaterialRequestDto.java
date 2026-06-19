package com.lisa.curriculum.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PinnedMaterialRequestDto {
    @NotBlank
    private String title;

    @NotBlank
    private String materialType; // e.g. SLIDE, PDF, DOC, LINK, IMAGE, OTHER

    @NotBlank
    private String url;

    private Integer displayOrder;
}
