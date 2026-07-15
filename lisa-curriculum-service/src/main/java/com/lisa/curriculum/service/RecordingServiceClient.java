package com.lisa.curriculum.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class RecordingServiceClient {

    private final RestTemplate restTemplate;

    @Value("${lms.integration.realtime-service-base-url:}")
    private String realtimeServiceBaseUrl;

    public RecordingRemoteModel startRecording(UUID sessionId, String authorizationHeader) {
        return exchange(
                path("/api/sessions/" + sessionId + "/recordings/start"),
                HttpMethod.POST,
                authorizationHeader,
                null,
                RecordingRemoteModel.class
        );
    }

    public StopRecordingRemoteResponse stopRecording(String recordingId, String authorizationHeader) {
        return exchange(
                path("/api/recordings/" + recordingId + "/stop"),
                HttpMethod.POST,
                authorizationHeader,
                null,
                StopRecordingRemoteResponse.class
        );
    }

    public List<RecordingRemoteModel> listSessionRecordings(UUID sessionId, String authorizationHeader) {
        RecordingRemoteModel[] response = exchange(
                path("/api/sessions/" + sessionId + "/recordings"),
                HttpMethod.GET,
                authorizationHeader,
                null,
                RecordingRemoteModel[].class
        );
        return Arrays.asList(Objects.requireNonNullElse(response, new RecordingRemoteModel[0]));
    }

    public RecordingRemoteModel getRecording(String recordingId, String authorizationHeader) {
        return exchange(
                path("/api/recordings/" + recordingId),
                HttpMethod.GET,
                authorizationHeader,
                null,
                RecordingRemoteModel.class
        );
    }

    public PlaybackRemoteResponse getPlaybackUrl(String recordingId, String authorizationHeader) {
        return exchange(
                path("/api/recordings/" + recordingId + "/playback-url"),
                HttpMethod.GET,
                authorizationHeader,
                null,
                PlaybackRemoteResponse.class
        );
    }

    private <T> T exchange(
            String url,
            HttpMethod method,
            String authorizationHeader,
            Object body,
            Class<T> responseType
    ) {
        try {
            HttpHeaders headers = new HttpHeaders();
            if (authorizationHeader != null && !authorizationHeader.isBlank()) {
                headers.set(HttpHeaders.AUTHORIZATION, authorizationHeader);
            }
            if (body != null) {
                headers.setContentType(MediaType.APPLICATION_JSON);
            }
            HttpEntity<?> entity = new HttpEntity<>(body, headers);
            ResponseEntity<T> response = restTemplate.exchange(url, method, entity, responseType);
            return response.getBody();
        } catch (HttpStatusCodeException e) {
            throw new ResponseStatusException(e.getStatusCode(), e.getResponseBodyAsString(), e);
        } catch (RestClientException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Realtime recording API call failed", e);
        }
    }

    private String path(String path) {
        String baseUrl = realtimeServiceBaseUrl == null ? "" : realtimeServiceBaseUrl.trim();
        if (baseUrl.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "MISSING_CONTRACT_REALTIME_SERVICE_BASE_URL");
        }
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        return baseUrl + path;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RecordingRemoteModel {
        public String recordingId;
        public String sessionId;
        public String roomId;
        public String provider;
        public String status;
        public String title;
        public Integer durationSeconds;
        public String cloudflareVideoUid;
        public Instant startedAt;
        public Instant endedAt;
        public Instant createdAt;
        public Instant updatedAt;
        public String storageObjectKey;
        public String podcastId;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class StopRecordingRemoteResponse {
        public RecordingRemoteModel processing;
        public RecordingRemoteModel ready;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PlaybackRemoteResponse {
        public String recordingId;
        public String playbackUrl;
        public String playerUrl;
        public String hlsUrl;
        public String dashUrl;
        public String thumbnailUrl;
        public Integer expiresIn;
    }
}
