package com.lisa.curriculum.controller;

import com.lisa.curriculum.dto.*;
import com.lisa.curriculum.security.CurrentUserHelper;
import com.lisa.curriculum.security.LmsUserPrincipal;
import com.lisa.curriculum.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/lms")
@RequiredArgsConstructor
@Tag(name = "Dashboard & Progress", description = "Dashboard and attendance progress tracking API")
public class DashboardController {

    private final DashboardService dashboardService;

    @PostMapping("/room-sessions/{sessionId}/attendance/join")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Ghi nhận học viên tham gia phòng học")
    public ResponseEntity<Void> joinSession(@PathVariable UUID sessionId) {
        LmsUserPrincipal currentUser = CurrentUserHelper.getCurrentUser();
        String learnerId = currentUser != null ? currentUser.getUserId() : "SYSTEM";
        dashboardService.recordAttendanceJoin(sessionId, learnerId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/room-sessions/{sessionId}/attendance/leave")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Ghi nhận học viên rời khỏi phòng học")
    public ResponseEntity<Void> leaveSession(@PathVariable UUID sessionId) {
        LmsUserPrincipal currentUser = CurrentUserHelper.getCurrentUser();
        String learnerId = currentUser != null ? currentUser.getUserId() : "SYSTEM";
        dashboardService.recordAttendanceLeave(sessionId, learnerId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/learner-progress")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Cập nhật tiến độ của học viên")
    public ResponseEntity<LearnerProgressResponseDto> saveProgress(@RequestBody LearnerProgressRequestDto request) {
        LmsUserPrincipal currentUser = CurrentUserHelper.getCurrentUser();
        String learnerId = currentUser != null ? currentUser.getUserId() : "SYSTEM";
        return ResponseEntity.ok(dashboardService.saveLearnerProgress(learnerId, request));
    }

    @GetMapping("/me/progress")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Lấy tiến độ học tập của bản thân")
    public ResponseEntity<List<LearnerProgressResponseDto>> getMyProgress() {
        LmsUserPrincipal currentUser = CurrentUserHelper.getCurrentUser();
        String learnerId = currentUser != null ? currentUser.getUserId() : "SYSTEM";
        return ResponseEntity.ok(dashboardService.getLearnerProgress(learnerId));
    }

    @GetMapping("/mentor/dashboard")
    @PreAuthorize("hasAnyRole('MENTOR', 'CREATOR')")
    @Operation(summary = "Lấy dashboard của mentor")
    public ResponseEntity<MentorDashboardResponseDto> getMentorDashboard(
            @RequestParam(required = false) String mentorId) {
        return ResponseEntity.ok(dashboardService.getMentorDashboard(resolveMentorId(mentorId)));
    }

    @GetMapping("/mentor/dashboard/learners")
    @PreAuthorize("hasAnyRole('MENTOR', 'CREATOR')")
    @Operation(summary = "Lấy danh sách learner progress cho mentor")
    public ResponseEntity<List<MentorLearnerProgressDto>> getMentorLearners(
            @RequestParam(required = false) String mentorId,
            @RequestParam(required = false) UUID sessionId) {
        return ResponseEntity.ok(dashboardService.getMentorLearners(resolveMentorId(mentorId), sessionId));
    }

    @GetMapping("/mentor/dashboard/sessions")
    @PreAuthorize("hasAnyRole('MENTOR', 'CREATOR')")
    @Operation(summary = "Lấy danh sách sessions cho mentor")
    public ResponseEntity<List<MentorSessionDashboardDto>> getMentorSessions(
            @RequestParam(required = false) String mentorId) {
        return ResponseEntity.ok(dashboardService.getMentorSessions(resolveMentorId(mentorId)));
    }

    @GetMapping("/mentor/dashboard/recordings")
    @PreAuthorize("hasAnyRole('MENTOR', 'CREATOR')")
    @Operation(summary = "Lấy danh sách recordings cho mentor")
    public ResponseEntity<List<SessionRecordingDto>> getMentorRecordings(
            @RequestParam(required = false) String mentorId) {
        return ResponseEntity.ok(dashboardService.getMentorRecordings(resolveMentorId(mentorId)));
    }

    private String resolveMentorId(String requestedMentorId) {
        LmsUserPrincipal currentUser = CurrentUserHelper.getCurrentUser();
        // SUPER (role_id=3) → ROLE_CREATOR có thể xem dashboard của bất kỳ mentor nào
        boolean isCreator = currentUser != null && currentUser.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_CREATOR"));
        if (isCreator && requestedMentorId != null && !requestedMentorId.isBlank()) {
            return requestedMentorId;
        }
        return currentUser != null ? currentUser.getUserId() : "SYSTEM";
    }
}
