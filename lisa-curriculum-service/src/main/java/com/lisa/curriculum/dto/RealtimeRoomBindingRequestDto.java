package com.lisa.curriculum.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RealtimeRoomBindingRequestDto {
    @NotBlank
    private String realtimeRoomId;

    private String realtimeAgoraChannelName;
}
