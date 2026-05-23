package com.lisa.curriculum.entity;
import jakarta.persistence.*;
import lombok.*;
import java.util.ArrayList;
import java.util.List;

@Entity @Table(name = "sub_levels")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SubLevel {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "level_id", nullable = false)
    private Level level;

    @Column(name = "sub_number", nullable = false) private int subNumber;
    @Column(nullable = false) private String topic;
    @Column(name = "duration_minutes") private int durationMinutes;

    @OneToMany(mappedBy = "subLevel", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("orderIndex ASC") @Builder.Default
    private List<SpeakingTask> tasks = new ArrayList<>();

    public void addTask(SpeakingTask task) { tasks.add(task); task.setSubLevel(this); }
}
