package com.lisa.curriculum.entity;
import jakarta.persistence.*;
import lombok.*;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "levels",
       uniqueConstraints = @UniqueConstraint(columnNames = {"language","level_number"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Level {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING) @Column(nullable = false)
    private Language language;

    @Column(nullable = false) private int stage;
    @Column(name = "level_number", nullable = false) private int levelNumber;
    @Column(nullable = false) private String title;
    @Column(name = "cefr_target") private String cefrTarget;
    @Column(name = "duration_minutes") private int durationMinutes;
    @Column(name = "group_label") private String groupLabel;

    @OneToMany(mappedBy = "level", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("subNumber ASC") @Builder.Default
    private List<SubLevel> subLevels = new ArrayList<>();

    public void addSubLevel(SubLevel sub) { subLevels.add(sub); sub.setLevel(this); }
}
