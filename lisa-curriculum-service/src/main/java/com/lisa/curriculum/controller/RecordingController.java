package com.lisa.curriculum.controller;

import com.lisa.curriculum.dto.RecordingPlaybackResponseDto;
import com.lisa.curriculum.dto.SessionRecordingDto;
import com.lisa.curriculum.service.SessionRecordingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/lms")
@RequiredArgsConstructor
@Tag(name = "Recording Center", description = "LMS recording metadata and playback API")
public class RecordingController {

    private final SessionRecordingService sessionRecordingService;

    @PostMapping({"/sessions/{sessionId}/recordings/start", "/recordings/sessions/{sessionId}/start"})
    @PreAuthorize("hasAnyRole('ADMIN', 'CREATOR')")
    @Operation(summary = "Start recording via Realtime audited contract")
    public ResponseEntity<SessionRecordingDto> startRecording(
            @PathVariable UUID sessionId,
            HttpServletRequest request) {
        return ResponseEntity.ok(sessionRecordingService.startRecording(sessionId, request.getHeader("Authorization")));
    }

    @PostMapping("/recordings/{recordingId}/stop")
    @PreAuthorize("hasAnyRole('ADMIN', 'CREATOR')")
    @Operation(summary = "Stop recording via Realtime audited contract")
    public ResponseEntity<SessionRecordingDto> stopRecording(
            @PathVariable String recordingId,
            HttpServletRequest request) {
        return ResponseEntity.ok(sessionRecordingService.stopRecording(recordingId, request.getHeader("Authorization")));
    }

    @GetMapping({"/sessions/{sessionId}/recordings", "/recordings/sessions/{sessionId}"})
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "List recordings for a session via Realtime audited contract")
    public ResponseEntity<List<SessionRecordingDto>> listSessionRecordings(
            @PathVariable UUID sessionId,
            HttpServletRequest request) {
        return ResponseEntity.ok(sessionRecordingService.listSessionRecordings(sessionId, request.getHeader("Authorization")));
    }

    @GetMapping("/recordings/{recordingId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get recording metadata via Realtime audited contract")
    public ResponseEntity<SessionRecordingDto> getRecording(
            @PathVariable String recordingId,
            HttpServletRequest request) {
        return ResponseEntity.ok(sessionRecordingService.getRecording(recordingId, request.getHeader("Authorization")));
    }

    @GetMapping({"/recordings/{recordingId}/playback", "/recordings/{recordingId}/playback-url"})
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get playback URL via Realtime audited contract")
    public ResponseEntity<RecordingPlaybackResponseDto> getPlayback(
            @PathVariable String recordingId,
            HttpServletRequest request) {
        return ResponseEntity.ok(sessionRecordingService.getPlayback(recordingId, request.getHeader("Authorization")));
    }
}
