package com.lisa.curriculum.dto;

import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SwitchSubLevelRequestDto {
    @NotNull
    private Long subLevelId;
}
