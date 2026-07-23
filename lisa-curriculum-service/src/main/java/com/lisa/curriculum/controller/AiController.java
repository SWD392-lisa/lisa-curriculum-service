package com.lisa.curriculum.controller;

import com.lisa.curriculum.dto.AiSuggestionRequestDto;
import com.lisa.curriculum.dto.AiSuggestionResponseDto;
import com.lisa.curriculum.dto.SpeakingAssessmentRequestDto;
import com.lisa.curriculum.dto.SpeakingAssessmentResponseDto;
import com.lisa.curriculum.config.MimoProperties;
import com.lisa.curriculum.security.CurrentUserHelper;
import com.lisa.curriculum.security.LmsUserPrincipal;
import com.lisa.curriculum.service.MimoClient;
import com.lisa.curriculum.service.SpeakingAssessmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiController {
    private final MimoClient mimoClient;
    private final MimoProperties mimoProperties;
    private final SpeakingAssessmentService speakingAssessmentService;

    @PostMapping("/suggestions")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<AiSuggestionResponseDto> suggestions(@Valid @RequestBody AiSuggestionRequestDto request) {
        return ResponseEntity.ok(AiSuggestionResponseDto.builder()
                .provider("MIMO")
                .model(mimoProperties.getModel())
                .suggestions(mimoClient.suggest(request))
                .build());
    }

    @PostMapping("/speaking-assessments")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<SpeakingAssessmentResponseDto> assessSpeaking(
            @Valid @RequestBody SpeakingAssessmentRequestDto request) {
        LmsUserPrincipal currentUser = CurrentUserHelper.getCurrentUser();
        String learnerId = currentUser != null ? currentUser.getUserId() : "SYSTEM";
        return ResponseEntity.ok(speakingAssessmentService.assess(learnerId, request));
    }

    @GetMapping("/speaking-assessments")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<SpeakingAssessmentResponseDto>> getSpeakingAssessments(
            @RequestParam Long subLevelId) {
        LmsUserPrincipal currentUser = CurrentUserHelper.getCurrentUser();
        String learnerId = currentUser != null ? currentUser.getUserId() : "SYSTEM";
        return ResponseEntity.ok(speakingAssessmentService.getBestForSubLevel(learnerId, subLevelId));
    }
}
