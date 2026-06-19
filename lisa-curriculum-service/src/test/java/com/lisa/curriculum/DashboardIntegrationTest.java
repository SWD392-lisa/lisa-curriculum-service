package com.lisa.curriculum;

import com.lisa.curriculum.dto.LearnerProgressRequestDto;
import com.lisa.curriculum.entity.*;
import com.lisa.curriculum.repository.*;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"spring.cache.type=simple"})
@AutoConfigureMockMvc
class DashboardIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private LevelRepository levelRepo;

    @Autowired
    private SubLevelRepository subLevelRepo;

    @Autowired
    private RoomLearningSessionRepository sessionRepo;

    @Autowired
    private RoomAttendanceRepository attendanceRepo;

    @Autowired
    private LearnerProgressRepository progressRepo;

    @Autowired
    private PinnedMaterialRepository pinnedMaterialRepo;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String SECRET = "8vOvSRycvAEBW/EDujcErz8UmfN70KGZZGKqfSuX7goQ1db0sPBwU1ICfogYXuy8QdOKRih5DEYOvMq1PL4Acg==";
    private static final String ISSUER = "ProjectLucy.API";
    private static final String AUDIENCE = "ProjectLucy.Client";

    private Long testLevelId;
    private Long testSubLevelId;
    private UUID testSessionId;

    private String generateToken(String userId, String email, String roleId) {
        Key key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .claim("sub", userId)
                .claim("http://schemas.xmlsoap.org/ws/2005/05/identity/claims/nameidentifier", userId)
                .claim("email", email)
                .claim("role", roleId)
                .claim("http://schemas.microsoft.com/ws/2008/06/identity/claims/role", roleId)
                .setIssuer(ISSUER)
                .setAudience(AUDIENCE)
                .setExpiration(new Date(System.currentTimeMillis() + 1000 * 60 * 60))
                .signWith(key)
                .compact();
    }

    @BeforeEach
    void clearData() {
        pinnedMaterialRepo.deleteAll();
        attendanceRepo.deleteAll();
        sessionRepo.deleteAll();
        progressRepo.deleteAll();
        subLevelRepo.deleteAll();
        levelRepo.deleteAll();

        // Clear caches
        for (String cacheName : cacheManager.getCacheNames()) {
            Cache cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                cache.clear();
            }
        }

        // Set up test data
        Level level = Level.builder()
                .language(Language.ENGLISH)
                .stage(1)
                .levelNumber(1)
                .title("TEST LEVEL")
                .durationMinutes(60)
                .build();
        level = levelRepo.save(level);
        testLevelId = level.getId();

        SubLevel sub = SubLevel.builder()
                .level(level)
                .subNumber(1)
                .topic("TEST TOPIC")
                .durationMinutes(10)
                .build();
        sub = subLevelRepo.save(sub);
        testSubLevelId = sub.getId();

        RoomLearningSession session = RoomLearningSession.builder()
                .id(UUID.randomUUID())
                .channelName("lms-test-session")
                .mentorUserId("MENTOR-456")
                .levelId(testLevelId)
                .currentSubLevelId(testSubLevelId)
                .status(SessionStatus.LIVE)
                .autoSwitchEnabled(true)
                .build();
        session = sessionRepo.save(session);
        testSessionId = session.getId();
    }

    @Test
    @DisplayName("Should update and retrieve learner progress successfully with simple caching")
    void testLearnerProgressFlow() throws Exception {
        String token = generateToken("LEARNER-123", "student@lisa.com", "1"); // ROLE_USER

        LearnerProgressRequestDto req = LearnerProgressRequestDto.builder()
                .levelId(testLevelId)
                .subLevelId(testSubLevelId)
                .completed(true)
                .speakingSeconds(30)
                .build();

        // 1. Post progress
        mockMvc.perform(post("/api/lms/learner-progress")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.learnerUserId").value("LEARNER-123"))
                .andExpect(jsonPath("$.completed").value(true))
                .andExpect(jsonPath("$.speakingSeconds").value(30));

        // 2. Get progress
        mockMvc.perform(get("/api/lms/me/progress")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].learnerUserId").value("LEARNER-123"))
                .andExpect(jsonPath("$[0].completed").value(true))
                .andExpect(jsonPath("$[0].speakingSeconds").value(30));

        // Verify caching is populated
        Cache cache = cacheManager.getCache("learner_progress");
        assertThat(cache).isNotNull();
        assertThat(cache.get("LEARNER-123")).isNotNull();

        // 3. Post progress again (should accumulate seconds and evict cache)
        req.setSpeakingSeconds(15);
        mockMvc.perform(post("/api/lms/learner-progress")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.speakingSeconds").value(45)); // accumulated: 30 + 15 = 45

        // Verify cache evicted
        assertThat(cache.get("LEARNER-123")).isNull();
    }

    @Test
    @DisplayName("Should track session attendance join and leave events successfully")
    void testAttendanceTracking() throws Exception {
        String token = generateToken("LEARNER-123", "student@lisa.com", "1");

        // 1. Join session
        mockMvc.perform(post("/api/lms/room-sessions/" + testSessionId + "/attendance/join")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        List<RoomAttendance> attendances = attendanceRepo.findAll();
        assertThat(attendances).hasSize(1);
        assertThat(attendances.get(0).getRoomSessionId()).isEqualTo(testSessionId);
        assertThat(attendances.get(0).getLearnerUserId()).isEqualTo("LEARNER-123");
        assertThat(attendances.get(0).getLeftAt()).isNull();

        // Sleep briefly to accumulate elapsed time
        Thread.sleep(1000);

        // 2. Leave session
        mockMvc.perform(post("/api/lms/room-sessions/" + testSessionId + "/attendance/leave")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        attendances = attendanceRepo.findAll();
        assertThat(attendances).hasSize(1);
        assertThat(attendances.get(0).getLeftAt()).isNotNull();
        assertThat(attendances.get(0).getTotalSeconds()).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("Should render mentor dashboard statistics correctly with N+1 projection query optimizations")
    void testMentorDashboardStats() throws Exception {
        String mentorToken = generateToken("MENTOR-456", "mentor@lisa.com", "2"); // ROLE_MENTOR
        String learnerToken = generateToken("LEARNER-789", "student@lisa.com", "1");

        // 1. Record some attendance join/leave
        mockMvc.perform(post("/api/lms/room-sessions/" + testSessionId + "/attendance/join")
                        .header("Authorization", "Bearer " + learnerToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/lms/room-sessions/" + testSessionId + "/attendance/leave")
                        .header("Authorization", "Bearer " + learnerToken))
                .andExpect(status().isOk());

        // 2. Record some progress
        LearnerProgressRequestDto progressReq = LearnerProgressRequestDto.builder()
                .levelId(testLevelId)
                .subLevelId(testSubLevelId)
                .completed(true)
                .speakingSeconds(120)
                .build();
        mockMvc.perform(post("/api/lms/learner-progress")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(progressReq))
                        .header("Authorization", "Bearer " + learnerToken))
                .andExpect(status().isOk());

        // 3. Pin a material
        PinnedMaterial material = PinnedMaterial.builder()
                .roomSessionId(testSessionId)
                .title("Slide 1")
                .materialType(MaterialType.SLIDE)
                .url("http://slide.url")
                .active(true)
                .pinnedByUserId("MENTOR-456")
                .build();
        pinnedMaterialRepo.save(material);

        // 4. Retrieve dashboard
        mockMvc.perform(get("/api/lms/mentor/dashboard")
                        .header("Authorization", "Bearer " + mentorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mentorId").value("MENTOR-456"))
                .andExpect(jsonPath("$.activeRoomCount").value(1))
                .andExpect(jsonPath("$.totalSessions").value(1))
                .andExpect(jsonPath("$.totalLearners").value(1))
                .andExpect(jsonPath("$.learnersToday").value(1))
                .andExpect(jsonPath("$.completedSubLevels").value(1))
                .andExpect(jsonPath("$.pinnedMaterialCount").value(1))
                .andExpect(jsonPath("$.currentSessions[0].sessionId").value(testSessionId.toString()))
                .andExpect(jsonPath("$.progressSummaryByLevel[0].levelId").value(testLevelId))
                .andExpect(jsonPath("$.progressSummaryByLevel[0].completedSubLevelsCount").value(1))
                .andExpect(jsonPath("$.progressSummaryByLevel[0].averageSpeakingSeconds").value(120.0))
                .andExpect(jsonPath("$.missingExternalData.userProfileApi").value(true))
                .andExpect(jsonPath("$.missingExternalData.realtimePresenceApi").value(true));

        // Verify dashboard is cached
        Cache cache = cacheManager.getCache("mentor_dashboard");
        assertThat(cache).isNotNull();
        assertThat(cache.get("MENTOR-456")).isNotNull();
    }
}
