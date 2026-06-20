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

    @Builder.Default
    private boolean autoSwitchEnabled = true;

    private String realtimeRoomId;

    private String realtimeAgoraChannelName;
}
