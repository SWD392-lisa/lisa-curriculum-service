package com.lisa.curriculum.service;

import com.lisa.curriculum.dto.*;
import com.lisa.curriculum.entity.*;
import org.springframework.stereotype.Component;
import java.util.stream.Collectors;

@Component
public class CurriculumMapper {

    public LevelDto toDto(Level l) {
        return LevelDto.builder()
                .id(l.getId()).language(l.getLanguage()).stage(l.getStage())
                .levelNumber(l.getLevelNumber()).title(l.getTitle())
                .cefrTarget(l.getCefrTarget()).durationMinutes(l.getDurationMinutes())
                .groupLabel(l.getGroupLabel())
                .subLevels(l.getSubLevels().stream().map(this::toDto).collect(Collectors.toList()))
                .build();
    }

    public SubLevelDto toDto(SubLevel s) {
        return SubLevelDto.builder()
                .id(s.getId()).subNumber(s.getSubNumber()).topic(s.getTopic())
                .durationMinutes(s.getDurationMinutes())
                .tasks(s.getTasks().stream().map(this::toDto).collect(Collectors.toList()))
                .build();
    }

    public SpeakingTaskDto toDto(SpeakingTask t) {
        return SpeakingTaskDto.builder()
                .id(t.getId()).taskType(t.getTaskType()).content(t.getContent())
                .pronunciation(t.getPronunciation()).orderIndex(t.getOrderIndex())
                .build();
    }
}
