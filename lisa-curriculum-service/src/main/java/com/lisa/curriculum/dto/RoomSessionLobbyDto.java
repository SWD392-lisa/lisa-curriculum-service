package com.lisa.curriculum.dto;

import lombok.*;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoomSessionLobbyDto {
    private UUID sessionId;
    private String status;
    private RoomSessionStateDto.LevelSummaryDto levelSummary;
    private SubLevelDto currentSubLevel;
}
