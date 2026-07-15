package com.lisa.curriculum.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "session_recordings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SessionRecording {

    @Id
    @Column(name = "recording_id", nullable = false, updatable = false)
    private String recordingId;

    @Column(name = "room_session_id", nullable = false)
    private UUID roomSessionId;

    @Column(name = "playback_url", length = 2048)
    private String playbackUrl;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SessionRecordingStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "provider", length = 128)
    private String provider;

    @Column(name = "storage_object_key", length = 2048)
    private String storageObjectKey;

    @Column(name = "podcast_id", length = 128)
    private String podcastId;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
