package com.lisa.curriculum.controller;

import com.lisa.curriculum.dto.RecordingReadyEventDto;
import com.lisa.curriculum.service.RecordingWebhookService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/lms/internal/recordings")
@RequiredArgsConstructor
public class InternalRecordingWebhookController {
    private final RecordingWebhookService recordingWebhookService;

    @Value("${lms.security.jwt.secret:}")
    private String webhookSecret;

    @PostMapping("/events")
    public ResponseEntity<Void> receive(
            @RequestHeader(value = "X-LMS-Recording-Secret", required = false) String suppliedSecret,
            @RequestBody RecordingReadyEventDto event) {
        if (webhookSecret == null || webhookSecret.isBlank() || suppliedSecret == null || !webhookSecret.equals(suppliedSecret)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        recordingWebhookService.apply(event);
        return ResponseEntity.ok().build();
    }
}
