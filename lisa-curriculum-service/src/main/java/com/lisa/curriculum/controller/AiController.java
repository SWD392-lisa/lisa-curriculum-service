package com.lisa.curriculum.controller;

import com.lisa.curriculum.dto.AiSuggestionRequestDto;
import com.lisa.curriculum.dto.AiSuggestionResponseDto;
import com.lisa.curriculum.config.MimoProperties;
import com.lisa.curriculum.service.MimoClient;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiController {
    private final MimoClient mimoClient;
    private final MimoProperties mimoProperties;

    @PostMapping("/suggestions")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<AiSuggestionResponseDto> suggestions(@Valid @RequestBody AiSuggestionRequestDto request) {
        return ResponseEntity.ok(AiSuggestionResponseDto.builder()
                .provider("MIMO")
                .model(mimoProperties.getModel())
                .suggestions(mimoClient.suggest(request))
                .build());
    }
}
