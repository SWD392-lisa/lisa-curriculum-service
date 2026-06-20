package com.lisa.curriculum.dto;

import lombok.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MentorDashboardResponseDto {
    private String mentorId;
    private OverviewDto overview;
    private AttendanceDto attendance;
    private LearningDto learning;
    private LiveLearningDto liveLearning;
    private RecordingsDto recordings;
    private ExternalStatusDto externalStatus;

    // Direct root fields for alignment
    private long activeRoomCount;
    private long totalSessions;
    private long learnersToday;
    private double averageAttendanceMinutes;
    private long completedSubLevels;
    private List<MentorSessionDashboardDto> currentSessions;
    private List<LevelProgressSummaryDto> progressSummaryByLevel;
    private long pinnedMaterialCount;
    private MissingExternalDataDto missingExternalData;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MissingExternalDataDto {
        private boolean userProfileApi;
        private boolean realtimePresenceApi;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OverviewDto {
        private long totalLearners;
        private long activeLearnersToday;
        private long activeSessions;
        private long completedSessions;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AttendanceDto {
        private double averageAttendanceMinutes;
        private double attendanceRate;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LearningDto {
        private long completedSubLevels;
        private long activeSubLevels;
        @Builder.Default
        private List<LevelProgressSummaryDto> progressByLevel = new ArrayList<>();
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LiveLearningDto {
        @Builder.Default
        private List<MentorSessionDashboardDto> currentRooms = new ArrayList<>();
        private CurrentSubLevelDto currentSubLevel;
        @Builder.Default
        private List<RoomSessionStateDto.PinnedMaterialDto> pinnedMaterials = new ArrayList<>();
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CurrentSubLevelDto {
        private UUID sessionId;
        private Long subLevelId;
        private String topic;
        private Integer durationMinutes;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecordingsDto {
        private long totalRecordings;
        @Builder.Default
        private List<SessionRecordingDto> latestRecordings = new ArrayList<>();
        private long playbackReadyCount;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExternalStatusDto {
        private boolean userProfileApiAvailable;
        private boolean realtimePresenceApiAvailable;
    }
}
