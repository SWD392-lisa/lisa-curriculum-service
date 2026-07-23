package com.lisa.curriculum.controller;

import com.lisa.curriculum.config.MimoProperties;
import com.lisa.curriculum.dto.SupportAiRequestDto;
import com.lisa.curriculum.dto.SupportAiResponseDto;
import com.lisa.curriculum.security.LmsUserPrincipal;
import com.lisa.curriculum.service.MimoClient;
import com.lisa.curriculum.service.SupportKnowledgeService;
import com.lisa.curriculum.service.SupportRateLimiter;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;

@RestController @RequestMapping("/api/ai/support") @RequiredArgsConstructor
public class SupportAiController {
    private final MimoClient mimo;
    private final MimoProperties properties;
    private final SupportKnowledgeService knowledge;
    private final SupportRateLimiter rateLimiter;

    @PostMapping
    public ResponseEntity<SupportAiResponseDto> answer(@Valid @RequestBody SupportAiRequestDto request, Authentication authentication) {
        LmsUserPrincipal principal = (LmsUserPrincipal) authentication.getPrincipal();
        rateLimiter.check(principal.getUserId());
        SupportAiResponseDto result = mimo.answerSupport(request.getQuestion(), request.getLocale(), knowledge.content());
        result.setProvider("MIMO");
        result.setModel(properties.getModel());
        result.setAnsweredAt(Instant.now());
        return ResponseEntity.ok(result);
    }
}
