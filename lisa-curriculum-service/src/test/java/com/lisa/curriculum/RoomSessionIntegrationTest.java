package com.lisa.curriculum;

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
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"spring.cache.type=simple"})
@AutoConfigureMockMvc
class RoomSessionIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private LevelRepository levelRepo;

    @Autowired
    private SubLevelRepository subLevelRepo;

    @Autowired
    private RoomLearningSessionRepository sessionRepo;

    @Autowired
    private PinnedMaterialRepository pinnedMaterialRepo;

    @Autowired
    private com.lisa.curriculum.scheduler.RoomSessionAutoSwitchScheduler autoSwitchScheduler;

    @Autowired
    private CacheManager cacheManager;

    private Long testLevelId;
    private Long subLevel1Id;
    private Long subLevel2Id;

    private static final String SECRET = "8vOvSRycvAEBW/EDujcErz8UmfN70KGZZGKqfSuX7goQ1db0sPBwU1ICfogYXuy8QdOKRih5DEYOvMq1PL4Acg==";
    private static final String ISSUER = "ProjectLucy.API";
    private static final String AUDIENCE = "ProjectLucy.Client";

    private String generateToken(String userId, String email, String displayName, String roleId) {
        Key key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .claim("sub", userId)
                .claim("http://schemas.xmlsoap.org/ws/2005/05/identity/claims/nameidentifier", userId)
                .claim("email", email)
                .claim("http://schemas.xmlsoap.org/ws/2005/05/identity/claims/emailaddress", email)
                .claim("name", displayName)
                .claim("http://schemas.xmlsoap.org/ws/2005/05/identity/claims/name", displayName)
                .claim("role", roleId)
                .claim("http://schemas.microsoft.com/ws/2008/06/identity/claims/role", roleId)
                .setIssuer(ISSUER)
                .setAudience(AUDIENCE)
                .setExpiration(new Date(System.currentTimeMillis() + 1000 * 60 * 60))
                .signWith(key)
                .compact();
    }

    @BeforeEach
    void setupData() {
        pinnedMaterialRepo.deleteAll();
        sessionRepo.deleteAll();
        subLevelRepo.deleteAll();
        levelRepo.deleteAll();

        Level level = Level.builder()
                .language(Language.ENGLISH)
                .stage(1)
                .levelNumber(1)
                .title("ENGLISH LEVEL 1")
                .cefrTarget("A1")
                .durationMinutes(60)
                .build();
        level = levelRepo.save(level);
        testLevelId = level.getId();

        SubLevel sub1 = SubLevel.builder()
                .level(level)
                .subNumber(1)
                .topic("Intro Topic 1")
                .durationMinutes(10)
                .build();
        sub1 = subLevelRepo.save(sub1);
        subLevel1Id = sub1.getId();

        SubLevel sub2 = SubLevel.builder()
                .level(level)
                .subNumber(2)
                .topic("Advanced Topic 2")
                .durationMinutes(10)
                .build();
        sub2 = subLevelRepo.save(sub2);
        subLevel2Id = sub2.getId();
    }

    @Test
    @DisplayName("Mentor can create learning session successfully")
    void testCreateSessionByMentor() throws Exception {
        String token = generateToken("MENTOR-123", "mentor@lisa.com", "Mentor John", "2");

        String body = "{\"levelId\":" + testLevelId + ",\"autoSwitchEnabled\":true}";

        mockMvc.perform(post("/api/lms/room-sessions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sessionId").exists())
                .andExpect(jsonPath("$.status").value("WAITING"))
                .andExpect(jsonPath("$.levelId").value(testLevelId))
                .andExpect(jsonPath("$.currentSubLevelId").value(subLevel1Id));
    }

    @Test
    @DisplayName("Learner role ID 1 (Student) cannot create session and gets 403")
    void testCreateSessionByLearnerForbidden() throws Exception {
        String token = generateToken("STUDENT-123", "student@lisa.com", "Student Bob", "1");
        String body = "{\"levelId\":" + testLevelId + ",\"autoSwitchEnabled\":true}";

        mockMvc.perform(post("/api/lms/room-sessions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Start session transitions state to LIVE and sets time markers")
    void testStartSession() throws Exception {
        String token = generateToken("MENTOR-123", "mentor@lisa.com", "Mentor John", "2");

        // First pre-create session in database
        UUID sessionId = UUID.randomUUID();
        RoomLearningSession session = RoomLearningSession.builder()
                .id(sessionId)
                .channelName("lms-" + sessionId)
                .mentorUserId("MENTOR-123")
                .levelId(testLevelId)
                .currentSubLevelId(subLevel1Id)
                .status(SessionStatus.WAITING)
                .autoSwitchEnabled(true)
                .build();
        sessionRepo.save(session);

        mockMvc.perform(post("/api/lms/room-sessions/" + sessionId + "/start")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("LIVE"))
                .andExpect(jsonPath("$.currentSubLevelId").value(subLevel1Id));
    }

    @Test
    @DisplayName("Switch sublevel manually transitions to next sub-level")
    void testSwitchSubLevelNext() throws Exception {
        String token = generateToken("MENTOR-123", "mentor@lisa.com", "Mentor John", "2");

        UUID sessionId = UUID.randomUUID();
        RoomLearningSession session = RoomLearningSession.builder()
                .id(sessionId)
                .channelName("lms-" + sessionId)
                .mentorUserId("MENTOR-123")
                .levelId(testLevelId)
                .currentSubLevelId(subLevel1Id)
                .status(SessionStatus.LIVE)
                .autoSwitchEnabled(true)
                .build();
        sessionRepo.save(session);

        mockMvc.perform(post("/api/lms/room-sessions/" + sessionId + "/switch-next")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentSubLevelId").value(subLevel2Id));
    }

    @Test
    @DisplayName("Mentor can pin and unpin materials successfully")
    void testPinUnpinMaterials() throws Exception {
        String token = generateToken("MENTOR-123", "mentor@lisa.com", "Mentor John", "2");

        UUID sessionId = UUID.randomUUID();
        RoomLearningSession session = RoomLearningSession.builder()
                .id(sessionId)
                .channelName("lms-" + sessionId)
                .mentorUserId("MENTOR-123")
                .levelId(testLevelId)
                .currentSubLevelId(subLevel1Id)
                .status(SessionStatus.LIVE)
                .autoSwitchEnabled(true)
                .build();
        sessionRepo.save(session);

        String pinBody = "{\"title\":\"Lecture 1 Slide\",\"materialType\":\"SLIDE\",\"url\":\"http://lisa.com/slide1\",\"displayOrder\":1}";

        // Pin material
        mockMvc.perform(post("/api/lms/room-sessions/" + sessionId + "/pinned-materials")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(pinBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Lecture 1 Slide"))
                .andExpect(jsonPath("$.active").value(true));

        PinnedMaterial pinned = pinnedMaterialRepo.findByRoomSessionIdAndActiveTrueOrderByDisplayOrderAsc(sessionId).get(0);
        Long materialId = pinned.getId();

        // Get active materials
        mockMvc.perform(get("/api/lms/room-sessions/" + sessionId + "/pinned-materials")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Lecture 1 Slide"));

        // Unpin material
        mockMvc.perform(delete("/api/lms/pinned-materials/" + materialId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        // Verify active materials is now empty
        assertThat(pinnedMaterialRepo.findByRoomSessionIdAndActiveTrueOrderByDisplayOrderAsc(sessionId)).isEmpty();
    }

    @Test
    @DisplayName("Optimistic Locking prevents concurrent modifications")
    void testOptimisticLocking() {
        UUID sessionId = UUID.randomUUID();
        RoomLearningSession session = RoomLearningSession.builder()
                .id(sessionId)
                .channelName("lms-" + sessionId)
                .mentorUserId("MENTOR-123")
                .levelId(testLevelId)
                .currentSubLevelId(subLevel1Id)
                .status(SessionStatus.WAITING)
                .autoSwitchEnabled(true)
                .build();
        sessionRepo.save(session);

        // Fetch two distinct references to the same entity
        RoomLearningSession ref1 = sessionRepo.findById(sessionId).orElseThrow();
        RoomLearningSession ref2 = sessionRepo.findById(sessionId).orElseThrow();

        // Modify and save ref1 (increases version to 1)
        ref1.setStatus(SessionStatus.LIVE);
        sessionRepo.save(ref1);

        // Modify ref2 and attempt to save (should fail due to version mismatch)
        ref2.setStatus(SessionStatus.PAUSED);
        assertThatThrownBy(() -> sessionRepo.save(ref2))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    @Test
    @DisplayName("Auto-switch scheduler automatically transitions sub-level when duration is exceeded")
    void testAutoSwitchSchedulerTrigger() {
        RoomLearningSession session = RoomLearningSession.builder()
                .id(UUID.randomUUID())
                .channelName("lms-autoswitch")
                .mentorUserId("MENTOR-123")
                .levelId(testLevelId)
                .currentSubLevelId(subLevel1Id)
                .status(SessionStatus.LIVE)
                .autoSwitchEnabled(true)
                .subLevelStartedAt(Instant.now().minus(java.time.Duration.ofMinutes(11)))
                .build();
        session = sessionRepo.save(session);

        autoSwitchScheduler.scanAndAutoSwitchRooms();

        RoomLearningSession updated = sessionRepo.findById(session.getId()).orElseThrow();
        assertThat(updated.getCurrentSubLevelId()).isEqualTo(subLevel2Id);
    }

    @Test
    @DisplayName("Room state cache is evicted after session mutation")
    void testRoomStateCacheEvictedOnSwitch() throws Exception {
        String token = generateToken("MENTOR-123", "mentor@lisa.com", "Mentor John", "2");

        UUID sessionId = UUID.randomUUID();
        RoomLearningSession session = RoomLearningSession.builder()
                .id(sessionId)
                .channelName("lms-" + sessionId)
                .mentorUserId("MENTOR-123")
                .levelId(testLevelId)
                .currentSubLevelId(subLevel1Id)
                .status(SessionStatus.LIVE)
                .autoSwitchEnabled(true)
                .build();
        sessionRepo.save(session);

        mockMvc.perform(get("/api/lms/room-sessions/" + sessionId + "/state")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        Cache cache = cacheManager.getCache("room_state");
        assertThat(cache).isNotNull();
        assertThat(cache.get(sessionId)).isNotNull();

        mockMvc.perform(post("/api/lms/room-sessions/" + sessionId + "/switch-next")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        assertThat(cache.get(sessionId)).isNull();
    }
}
