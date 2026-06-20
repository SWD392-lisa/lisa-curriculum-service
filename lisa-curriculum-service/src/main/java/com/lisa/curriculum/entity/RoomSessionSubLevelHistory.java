package com.lisa.curriculum.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "room_session_sub_level_history")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoomSessionSubLevelHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "room_session_id", nullable = false)
    private UUID roomSessionId;

    @Column(name = "from_sub_level_id")
    private Long fromSubLevelId;

    @Column(name = "to_sub_level_id", nullable = false)
    private Long toSubLevelId;

    @Column(name = "changed_by_user_id")
    private String changedByUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "change_source", nullable = false, length = 20)
    private SubLevelChangeSource changeSource;

    @Column(name = "note", length = 1000)
    private String note;

    @Builder.Default
    @Column(name = "changed_at", nullable = false)
    private Instant changedAt = Instant.now();
}
