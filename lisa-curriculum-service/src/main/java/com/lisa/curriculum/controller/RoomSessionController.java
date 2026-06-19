package com.lisa.curriculum.controller;

import com.lisa.curriculum.dto.*;
import com.lisa.curriculum.service.PinnedMaterialService;
import com.lisa.curriculum.service.RoomLearningSessionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/lms")
@RequiredArgsConstructor
public class RoomSessionController {

    private final RoomLearningSessionService sessionService;
    private final PinnedMaterialService pinnedMaterialService;

    @PostMapping("/room-sessions")
    @PreAuthorize("hasAnyRole('ADMIN', 'MENTOR', 'CREATOR')")
    public ResponseEntity<RoomSessionResponseDto> createSession(
            @Valid @RequestBody CreateRoomSessionRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(sessionService.createSession(request.getLevelId(), request.isAutoSwitchEnabled()));
    }

    @PostMapping("/room-sessions/{sessionId}/start")
    @PreAuthorize("hasAnyRole('ADMIN', 'MENTOR', 'CREATOR')")
    public ResponseEntity<RoomSessionResponseDto> startSession(@PathVariable UUID sessionId) {
        return ResponseEntity.ok(sessionService.startSession(sessionId));
    }

    @PostMapping("/room-sessions/{sessionId}/pause")
    @PreAuthorize("hasAnyRole('ADMIN', 'MENTOR', 'CREATOR')")
    public ResponseEntity<RoomSessionResponseDto> pauseSession(@PathVariable UUID sessionId) {
        return ResponseEntity.ok(sessionService.pauseSession(sessionId));
    }

    @PostMapping("/room-sessions/{sessionId}/end")
    @PreAuthorize("hasAnyRole('ADMIN', 'MENTOR', 'CREATOR')")
    public ResponseEntity<RoomSessionResponseDto> endSession(@PathVariable UUID sessionId) {
        return ResponseEntity.ok(sessionService.endSession(sessionId));
    }

    @GetMapping("/room-sessions/{sessionId}/state")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<RoomSessionStateDto> getState(@PathVariable UUID sessionId) {
        return ResponseEntity.ok(sessionService.getState(sessionId));
    }

    @PostMapping("/room-sessions/{sessionId}/switch-next")
    @PreAuthorize("hasAnyRole('ADMIN', 'MENTOR', 'CREATOR')")
    public ResponseEntity<RoomSessionResponseDto> switchNext(@PathVariable UUID sessionId) {
        return ResponseEntity.ok(sessionService.switchToNextSubLevel(sessionId, "Mentor manual next sub-level override"));
    }

    @PostMapping("/room-sessions/{sessionId}/switch-sub-level")
    @PreAuthorize("hasAnyRole('ADMIN', 'MENTOR', 'CREATOR')")
    public ResponseEntity<RoomSessionResponseDto> switchSubLevel(
            @PathVariable UUID sessionId,
            @Valid @RequestBody SwitchSubLevelRequestDto request) {
        return ResponseEntity.ok(sessionService.switchToSubLevel(sessionId, request.getSubLevelId(), "Mentor manual sub-level select override"));
    }

    @PostMapping("/room-sessions/{sessionId}/realtime-binding")
    @PreAuthorize("hasAnyRole('ADMIN', 'MENTOR', 'CREATOR')")
    public ResponseEntity<RoomSessionResponseDto> bindRealtimeRoom(
            @PathVariable UUID sessionId,
            @Valid @RequestBody RealtimeRoomBindingRequestDto request) {
        return ResponseEntity.ok(sessionService.bindRealtimeRoom(sessionId, request));
    }

    @DeleteMapping("/room-sessions/{sessionId}/realtime-binding")
    @PreAuthorize("hasAnyRole('ADMIN', 'MENTOR', 'CREATOR')")
    public ResponseEntity<RoomSessionResponseDto> unbindRealtimeRoom(@PathVariable UUID sessionId) {
        return ResponseEntity.ok(sessionService.unbindRealtimeRoom(sessionId));
    }

    @PostMapping("/room-sessions/{sessionId}/pinned-materials")
    @PreAuthorize("hasAnyRole('ADMIN', 'MENTOR', 'CREATOR')")
    public ResponseEntity<RoomSessionStateDto.PinnedMaterialDto> pinMaterial(
            @PathVariable UUID sessionId,
            @Valid @RequestBody PinnedMaterialRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(pinnedMaterialService.pinMaterial(sessionId, request));
    }

    @GetMapping("/room-sessions/{sessionId}/pinned-materials")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<RoomSessionStateDto.PinnedMaterialDto>> getPinnedMaterials(
            @PathVariable UUID sessionId) {
        return ResponseEntity.ok(pinnedMaterialService.listActiveMaterials(sessionId));
    }

    @DeleteMapping("/pinned-materials/{materialId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MENTOR', 'CREATOR')")
    public ResponseEntity<Void> unpinMaterial(@PathVariable Long materialId) {
        pinnedMaterialService.unpinMaterial(materialId);
        return ResponseEntity.noContent().build();
    }
}
