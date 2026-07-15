package com.lisa.curriculum.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class RecordingReadyEventDto {
    private String recordingId;
    private UUID sessionId;
    private String status;
    private String provider;
    private String storageObjectKey;
    private Integer durationSeconds;
    private String podcastId;
    private Instant startedAt;
    private Instant endedAt;
}
