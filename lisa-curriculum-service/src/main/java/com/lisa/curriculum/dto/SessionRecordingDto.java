package com.lisa.curriculum.dto;

import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SessionRecordingDto {
    private String recordingId;
    private UUID roomSessionId;
    private String playbackUrl;
    private Integer durationSeconds;
    private String status;
    private Instant createdAt;
    private Instant startedAt;
    private Instant endedAt;
    private String provider;
}
