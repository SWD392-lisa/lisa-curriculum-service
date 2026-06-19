package com.lisa.curriculum.dto;

import lombok.*;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MentorDashboardResponseDto {
    private String mentorId;
    private long activeRoomCount;
    private long totalSessions;
    private long totalLearners;
    private long learnersToday;
    private double averageAttendanceMinutes;
    private long completedSubLevels;
    private List<RoomSessionResponseDto> currentSessions;
    private long pinnedMaterialCount;
    private List<LevelProgressSummaryDto> progressSummaryByLevel;

    @Builder.Default
    private MissingExternalDataDto missingExternalData = new MissingExternalDataDto();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MissingExternalDataDto {
        private boolean userProfileApi = true;
        private boolean realtimePresenceApi = true;
    }
}
