package com.lisa.curriculum.dto;

import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MentorSessionDashboardDto {
    private UUID sessionId;
    private String status;
    private String channelName;
    private String realtimeRoomId;
    private String realtimeAgoraChannelName;
    private Long levelId;
    private Long currentSubLevelId;
    private String currentSubLevelTopic;
    private Instant startedAt;
    private Instant endedAt;
    private long pinnedMaterialCount;
}
