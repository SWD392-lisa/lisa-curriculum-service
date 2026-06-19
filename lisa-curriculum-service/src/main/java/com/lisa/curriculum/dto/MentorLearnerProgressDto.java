package com.lisa.curriculum.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MentorLearnerProgressDto {
    private String learnerUserId;
    private long totalSessionsJoined;
    private long completedSubLevels;
    private long totalSpeakingSeconds;
    private double totalAttendanceMinutes;
    private boolean activeToday;
}
