package com.lisa.curriculum.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "pinned_materials")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PinnedMaterial {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "room_session_id", nullable = false)
    private UUID roomSessionId;

    @Column(nullable = false)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "material_type", nullable = false)
    private MaterialType materialType;

    @Column(nullable = false, length = 1024)
    private String url;

    @Column(name = "display_order")
    private Integer displayOrder;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "pinned_by_user_id", nullable = false)
    private String pinnedByUserId;

    @Column(name = "pinned_at", nullable = false)
    private Instant pinnedAt;

    @PrePersist
    protected void onCreate() {
        pinnedAt = Instant.now();
    }
}
