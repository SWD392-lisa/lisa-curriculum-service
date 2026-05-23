package com.lisa.curriculum.entity;
import jakarta.persistence.*;
import lombok.*;

@Entity @Table(name = "speaking_tasks")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SpeakingTask {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sub_level_id", nullable = false)
    private SubLevel subLevel;

    @Enumerated(EnumType.STRING) @Column(name = "task_type", nullable = false)
    private TaskType taskType;

    @Column(columnDefinition = "TEXT", nullable = false) private String content;
    @Column(columnDefinition = "TEXT") private String pronunciation;
    @Column(name = "order_index") private int orderIndex;
}
