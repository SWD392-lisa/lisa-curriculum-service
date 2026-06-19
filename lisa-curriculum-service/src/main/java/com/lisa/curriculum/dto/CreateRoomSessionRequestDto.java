package com.lisa.curriculum.dto;

import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateRoomSessionRequestDto {
    @NotNull
    private Long levelId;

    private boolean autoSwitchEnabled = true;
}
