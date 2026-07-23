package com.lisa.curriculum.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SupportAiRequestDto {
    @NotBlank
    @Size(max = 500)
    private String question;
    private String locale = "vi";
}
