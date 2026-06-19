package com.lisa.curriculum.dto;

import lombok.*;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoomSessionResponseDto {
    private UUID sessionId;
    private String channelName;
    private String status;
    private Long levelId;
    private Long currentSubLevelId;
}
