package com.lisa.curriculum.dto;

import lombok.*;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubLevelHistoryDto {
    private Long id;
    private java.util.UUID roomSessionId;
    private Long fromSubLevelId;
    private Long toSubLevelId;
    private String changedByUserId;
    private String changeSource;
    private String note;
    private Instant changedAt;
}
