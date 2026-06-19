package com.lisa.curriculum;

import com.lisa.curriculum.entity.*;
import com.lisa.curriculum.repository.LevelRepository;
import com.lisa.curriculum.repository.RoomLearningSessionRepository;
import com.lisa.curriculum.repository.SessionRecordingRepository;
import com.lisa.curriculum.repository.SubLevelRepository;
import com.lisa.curriculum.service.RecordingServiceClient;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"spring.cache.type=simple"})
@AutoConfigureMockMvc
class RecordingIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private LevelRepository levelRepository;

    @Autowired
    private SubLevelRepository subLevelRepository;

    @Autowired
    private RoomLearningSessionRepository sessionRepository;

    @Autowired
    private SessionRecordingRepository sessionRecordingRepository;

    @Autowired
    private CacheManager cacheManager;

    @MockBean
    private RecordingServiceClient recordingServiceClient;

    private static final String SECRET = "8vOvSRycvAEBW/EDujcErz8UmfN70KGZZGKqfSuX7goQ1db0sPBwU1ICfogYXuy8QdOKRih5DEYOvMq1PL4Acg==";
    private static final String ISSUER = "ProjectLucy.API";
    private static final String AUDIENCE = "ProjectLucy.Client";

    private UUID sessionId;
    private String creatorToken;

    @BeforeEach
    void setUp() {
        sessionRecordingRepository.deleteAll();
        sessionRepository.deleteAll();
        subLevelRepository.deleteAll();
        levelRepository.deleteAll();

        Level level = levelRepository.save(Level.builder()
                .language(Language.ENGLISH)
                .stage(1)
                .levelNumber(1)
                .title("Recording Level")
                .durationMinutes(60)
                .build());
        SubLevel subLevel = subLevelRepository.save(SubLevel.builder()
                .level(level)
                .subNumber(1)
                .topic("Recording Topic")
                .durationMinutes(10)
                .build());

        sessionId = UUID.randomUUID();
        sessionRepository.save(RoomLearningSession.builder()
                .id(sessionId)
                .channelName("lms-" + sessionId)
                .mentorUserId("CREATOR-001")
                .levelId(level.getId())
                .currentSubLevelId(subLevel.getId())
                .status(SessionStatus.LIVE)
                .autoSwitchEnabled(true)
                .build());

        creatorToken = generateToken("CREATOR-001", "creator@lisa.com", "3");
    }

    @Test
    @DisplayName("Should start recording and persist metadata from Realtime response")
    void shouldStartRecording() throws Exception {
        RecordingServiceClient.RecordingRemoteModel remote = remoteRecording("rec-001", "RECORDING", 12);
        when(recordingServiceClient.startRecording(ArgumentMatchers.eq(sessionId), ArgumentMatchers.anyString()))
                .thenReturn(remote);

        mockMvc.perform(post("/api/lms/sessions/" + sessionId + "/recordings/start")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + creatorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recordingId").value("rec-001"))
                .andExpect(jsonPath("$.status").value("RECORDING"));

        SessionRecording saved = sessionRecordingRepository.findById("rec-001").orElseThrow();
        assertThat(saved.getRoomSessionId()).isEqualTo(sessionId);
        assertThat(saved.getStatus()).isEqualTo(SessionRecordingStatus.RECORDING);
    }

    @Test
    @DisplayName("Should stop recording and update persisted metadata")
    void shouldStopRecording() throws Exception {
        sessionRecordingRepository.save(SessionRecording.builder()
                .recordingId("rec-002")
                .roomSessionId(sessionId)
                .status(SessionRecordingStatus.RECORDING)
                .createdAt(Instant.now())
                .build());

        RecordingServiceClient.StopRecordingRemoteResponse stopResponse = new RecordingServiceClient.StopRecordingRemoteResponse();
        stopResponse.ready = remoteRecording("rec-002", "READY", 300);
        when(recordingServiceClient.stopRecording(ArgumentMatchers.eq("rec-002"), ArgumentMatchers.anyString()))
                .thenReturn(stopResponse);

        mockMvc.perform(post("/api/lms/recordings/rec-002/stop")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + creatorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recordingId").value("rec-002"))
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.durationSeconds").value(300));
    }

    @Test
    @DisplayName("Should list recordings and get playback URL")
    void shouldListAndGetPlayback() throws Exception {
        RecordingServiceClient.RecordingRemoteModel remote = remoteRecording("rec-003", "READY", 180);
        when(recordingServiceClient.listSessionRecordings(ArgumentMatchers.eq(sessionId), ArgumentMatchers.anyString()))
                .thenReturn(java.util.List.of(remote));
        when(recordingServiceClient.getPlaybackUrl(ArgumentMatchers.eq("rec-003"), ArgumentMatchers.anyString()))
                .thenReturn(playback("rec-003"));

        mockMvc.perform(get("/api/lms/sessions/" + sessionId + "/recordings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + creatorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].recordingId").value("rec-003"))
                .andExpect(jsonPath("$[0].status").value("READY"));

        mockMvc.perform(get("/api/lms/recordings/rec-003/playback")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + creatorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recordingId").value("rec-003"))
                .andExpect(jsonPath("$.playbackUrl").value("https://playback.example/rec-003"));

        SessionRecording saved = sessionRecordingRepository.findById("rec-003").orElseThrow();
        assertThat(saved.getPlaybackUrl()).isEqualTo("https://playback.example/rec-003");
    }

    @Test
    @DisplayName("Should evict recordings cache when recording state changes")
    void shouldEvictRecordingsCache() throws Exception {
        RecordingServiceClient.RecordingRemoteModel remote = remoteRecording("rec-004", "RECORDING", 20);
        when(recordingServiceClient.listSessionRecordings(ArgumentMatchers.eq(sessionId), ArgumentMatchers.anyString()))
                .thenReturn(java.util.List.of(remote));

        mockMvc.perform(get("/api/lms/sessions/" + sessionId + "/recordings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + creatorToken))
                .andExpect(status().isOk());

        Cache cache = cacheManager.getCache("recordings");
        assertThat(cache).isNotNull();
        assertThat(cache.get(sessionId)).isNotNull();

        sessionRecordingRepository.save(SessionRecording.builder()
                .recordingId("rec-004")
                .roomSessionId(sessionId)
                .status(SessionRecordingStatus.RECORDING)
                .createdAt(Instant.now())
                .build());

        RecordingServiceClient.StopRecordingRemoteResponse stopResponse = new RecordingServiceClient.StopRecordingRemoteResponse();
        stopResponse.ready = remoteRecording("rec-004", "READY", 99);
        when(recordingServiceClient.stopRecording(ArgumentMatchers.eq("rec-004"), ArgumentMatchers.anyString()))
                .thenReturn(stopResponse);

        mockMvc.perform(post("/api/lms/recordings/rec-004/stop")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + creatorToken))
                .andExpect(status().isOk());

        assertThat(cache.get(sessionId)).isNull();
    }

    private String generateToken(String userId, String email, String roleId) {
        Key key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .claim("sub", userId)
                .claim("http://schemas.xmlsoap.org/ws/2005/05/identity/claims/nameidentifier", userId)
                .claim("email", email)
                .claim("http://schemas.xmlsoap.org/ws/2005/05/identity/claims/emailaddress", email)
                .claim("role", roleId)
                .claim("http://schemas.microsoft.com/ws/2008/06/identity/claims/role", roleId)
                .setIssuer(ISSUER)
                .setAudience(AUDIENCE)
                .setExpiration(new Date(System.currentTimeMillis() + 1000 * 60 * 60))
                .signWith(key)
                .compact();
    }

    private RecordingServiceClient.RecordingRemoteModel remoteRecording(String recordingId, String status, Integer durationSeconds) {
        RecordingServiceClient.RecordingRemoteModel remote = new RecordingServiceClient.RecordingRemoteModel();
        remote.recordingId = recordingId;
        remote.sessionId = sessionId.toString();
        remote.status = status;
        remote.durationSeconds = durationSeconds;
        remote.provider = "AGORA_CLOUD_RECORDING";
        remote.createdAt = Instant.now();
        remote.startedAt = Instant.now();
        remote.endedAt = Instant.now();
        return remote;
    }

    private RecordingServiceClient.PlaybackRemoteResponse playback(String recordingId) {
        RecordingServiceClient.PlaybackRemoteResponse response = new RecordingServiceClient.PlaybackRemoteResponse();
        response.recordingId = recordingId;
        response.playbackUrl = "https://playback.example/" + recordingId;
        response.playerUrl = response.playbackUrl;
        response.hlsUrl = "https://playback.example/" + recordingId + ".m3u8";
        response.expiresIn = 3600;
        return response;
    }
}
