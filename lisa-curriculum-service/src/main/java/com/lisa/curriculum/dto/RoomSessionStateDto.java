package com.lisa.curriculum.dto;

import lombok.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoomSessionStateDto {
    private UUID sessionId;
    private String channelName;
    private String status;
    private LevelSummaryDto levelSummary;
    private SubLevelDto currentSubLevel;
    private Instant subLevelStartedAt;
    private long secondsRemaining;
    private boolean autoSwitchEnabled;
    private List<PinnedMaterialDto> pinnedMaterials;
    private Map<String, Object> realtime;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class LevelSummaryDto {
        private Long id;
        private String language;
        private int stage;
        private int levelNumber;
        private String title;
        private String cefrTarget;
        private int durationMinutes;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PinnedMaterialDto {
        private Long id;
        private String title;
        private String materialType;
        private String url;
        private Integer displayOrder;
        private boolean active;
        private String pinnedByUserId;
        private Instant pinnedAt;
    }
}
