package com.lisa.curriculum.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecordingPlaybackResponseDto {
    private String recordingId;
    private String playbackUrl;
    private String playerUrl;
    private String hlsUrl;
    private String dashUrl;
    private String thumbnailUrl;
    private Integer expiresIn;
}
