package com.lisa.curriculum.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "room_learning_sessions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoomLearningSession {

    @Id
    private UUID id;

    @Column(name = "channel_name", nullable = false)
    private String channelName;

    @Column(name = "realtime_room_id")
    private String realtimeRoomId;

    @Column(name = "realtime_agora_channel_name")
    private String realtimeAgoraChannelName;

    @Column(name = "mentor_user_id", nullable = false)
    private String mentorUserId;

    @Column(name = "level_id", nullable = false)
    private Long levelId;

    @Column(name = "current_sub_level_id", nullable = false)
    private Long currentSubLevelId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SessionStatus status;

    @Column(name = "auto_switch_enabled", nullable = false)
    private boolean autoSwitchEnabled;

    @Column(name = "sub_level_started_at")
    private Instant subLevelStartedAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
