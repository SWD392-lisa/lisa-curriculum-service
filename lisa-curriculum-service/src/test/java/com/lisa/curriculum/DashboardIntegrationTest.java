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
    private SpeakingTaskRepository taskRepo;

    @Autowired
    private SpeakingAssessmentRepository assessmentRepo;

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
    private Long testTaskId;
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
        assessmentRepo.deleteAll();
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

        SpeakingTask task = taskRepo.save(SpeakingTask.builder()
                .subLevel(sub)
                .taskType(TaskType.BULLET)
                .content("Describe your study place")
                .orderIndex(0)
                .build());
        testTaskId = task.getId();

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
        saveAssessment("LEARNER-123", 30, 80);

        LearnerProgressRequestDto req = LearnerProgressRequestDto.builder()
                .levelId(testLevelId)
                .subLevelId(testSubLevelId)
                .completed(true)
                .speakingSeconds(30)
                .idempotencyKey("key-1")
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
        req.setIdempotencyKey("key-2");
        mockMvc.perform(post("/api/lms/learner-progress")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.speakingSeconds").value(30));

        // 4. Retry the same request with the same idempotency key (should be no-op/idempotent)
        mockMvc.perform(post("/api/lms/learner-progress")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.speakingSeconds").value(30));

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
        saveAssessment("LEARNER-789", 120, 85);

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
                .andExpect(jsonPath("$.overview.activeSessions").value(1))
                .andExpect(jsonPath("$.overview.completedSessions").value(0))
                .andExpect(jsonPath("$.overview.totalLearners").value(1))
                .andExpect(jsonPath("$.overview.activeLearnersToday").value(1))
                .andExpect(jsonPath("$.attendance.averageAttendanceMinutes").exists())
                .andExpect(jsonPath("$.learning.completedSubLevels").value(1))
                .andExpect(jsonPath("$.learning.activeSubLevels").value(1))
                .andExpect(jsonPath("$.learning.progressByLevel[0].levelId").value(testLevelId))
                .andExpect(jsonPath("$.learning.progressByLevel[0].completedSubLevelsCount").value(1))
                .andExpect(jsonPath("$.learning.progressByLevel[0].averageSpeakingSeconds").value(120.0))
                .andExpect(jsonPath("$.liveLearning.currentRooms[0].sessionId").value(testSessionId.toString()))
                .andExpect(jsonPath("$.liveLearning.currentSubLevel.subLevelId").value(testSubLevelId))
                .andExpect(jsonPath("$.liveLearning.pinnedMaterials[0].title").value("Slide 1"))
                .andExpect(jsonPath("$.recordings.totalRecordings").value(0))
                .andExpect(jsonPath("$.recordings.playbackReadyCount").value(0))
                .andExpect(jsonPath("$.externalStatus.userProfileApiAvailable").value(false))
                .andExpect(jsonPath("$.externalStatus.realtimePresenceApiAvailable").value(false));

        // Verify dashboard is cached
        Cache cache = cacheManager.getCache("mentor_dashboard");
        assertThat(cache).isNotNull();
        assertThat(cache.get("MENTOR-456")).isNotNull();
    }

    @Test
    @DisplayName("English sub-level requires an AI speaking assessment before completion")
    void testEnglishCompletionRequiresSpeakingPractice() throws Exception {
        String token = generateToken("LEARNER-NO-PRACTICE", "student@lisa.com", "1");
        LearnerProgressRequestDto request = LearnerProgressRequestDto.builder()
                .levelId(testLevelId)
                .subLevelId(testSubLevelId)
                .completed(true)
                .speakingSeconds(30)
                .build();

        mockMvc.perform(post("/api/lms/learner-progress")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("SPEAKING_PRACTICE_REQUIRED"));
    }

    private void saveAssessment(String learnerId, int speakingSeconds, int score) {
        SpeakingTask task = taskRepo.findById(testTaskId).orElseThrow();
        assessmentRepo.save(SpeakingAssessment.builder()
                .learnerUserId(learnerId)
                .task(task)
                .transcript("I study in a quiet library near my home.")
                .overallScore(score)
                .relevanceScore(score)
                .grammarScore(score)
                .vocabularyScore(score)
                .feedback("Good answer")
                .suggestedAnswer("I usually study in a quiet library near my home.")
                .speakingSeconds(speakingSeconds)
                .build());
    }

    @Test
    @DisplayName("User who never joined cannot view room state")
    void testUserWhoNeverJoinedCannotViewRoomState() throws Exception {
        String token = generateToken("LEARNER-404", "student@lisa.com", "1");

        mockMvc.perform(get("/api/lms/room-sessions/" + testSessionId + "/state")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Learner can view lobby metadata before joining a room")
    void testLearnerCanViewLobbyBeforeJoining() throws Exception {
        String token = generateToken("LEARNER-404", "student@lisa.com", "1");

        mockMvc.perform(get("/api/lms/room-sessions/" + testSessionId + "/lobby")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value(testSessionId.toString()))
                .andExpect(jsonPath("$.status").value("LIVE"))
                .andExpect(jsonPath("$.levelSummary.id").value(testLevelId))
                .andExpect(jsonPath("$.currentSubLevel.id").value(testSubLevelId));
    }

    @Test
    @DisplayName("User who never joined cannot submit progress when sessionId present")
    void testUserWhoNeverJoinedCannotSubmitProgressForSession() throws Exception {
        String token = generateToken("LEARNER-404", "student@lisa.com", "1");

        LearnerProgressRequestDto req = LearnerProgressRequestDto.builder()
                .sessionId(testSessionId)
                .levelId(testLevelId)
                .subLevelId(testSubLevelId)
                .completed(true)
                .speakingSeconds(10)
                .build();

        mockMvc.perform(post("/api/lms/learner-progress")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Duplicate join does not create two active attendance rows")
    void testDuplicateJoinDoesNotCreateTwoActiveAttendanceRows() throws Exception {
        String token = generateToken("LEARNER-123", "student@lisa.com", "1");

        mockMvc.perform(post("/api/lms/room-sessions/" + testSessionId + "/attendance/join")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/lms/room-sessions/" + testSessionId + "/attendance/join")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        assertThat(attendanceRepo.findAll().stream()
                .filter(item -> item.getRoomSessionId().equals(testSessionId))
                .filter(item -> item.getLearnerUserId().equals("LEARNER-123"))
                .filter(item -> item.getLeftAt() == null)
                .count()).isEqualTo(1);
    }
}
