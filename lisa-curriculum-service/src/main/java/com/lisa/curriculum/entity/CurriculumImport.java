package com.lisa.curriculum.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "curriculum_imports")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CurriculumImport {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "file_hash", nullable = false)
    private String fileHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Language language;

    @Column(nullable = false)
    private int stage;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ImportStatus status;

    @Column(name = "levels_imported")
    private Integer levelsImported;

    @Column(name = "sub_levels_imported")
    private Integer subLevelsImported;

    @Column(name = "tasks_imported")
    private Integer tasksImported;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "imported_by_user_id")
    private String importedByUserId;

    @Builder.Default
    @Column(name = "imported_at", nullable = false)
    private Instant importedAt = Instant.now();

    @Column(name = "duration_ms")
    private Long durationMs;
}
