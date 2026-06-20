package com.lisa.curriculum;

import com.lisa.curriculum.entity.*;
import com.lisa.curriculum.repository.*;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = {"spring.cache.type=simple"})
@AutoConfigureMockMvc
@Transactional
class HardenImportIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private LevelRepository levelRepo;

    @Autowired
    private SubLevelRepository subLevelRepo;

    @Autowired
    private CurriculumImportRepository importRepo;

    @Autowired
    private CacheManager cacheManager;

    private static final String SECRET = "8vOvSRycvAEBW/EDujcErz8UmfN70KGZZGKqfSuX7goQ1db0sPBwU1ICfogYXuy8QdOKRih5DEYOvMq1PL4Acg==";
    private static final String ISSUER = "ProjectLucy.API";
    private static final String AUDIENCE = "ProjectLucy.Client";

    private String generateToken(String roleId) {
        Key key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .claim("sub", "TEST-USER-ID")
                .claim("http://schemas.xmlsoap.org/ws/2005/05/identity/claims/nameidentifier", "TEST-USER-ID")
                .claim("email", "creator@lisa.com")
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
        importRepo.deleteAll();
        subLevelRepo.deleteAll();
        levelRepo.deleteAll();
        
        Cache cache = cacheManager.getCache("curriculum");
        if (cache != null) {
            cache.clear();
        }
    }

    private byte[] createMockDocx(int levelNumber, int subLevelCount, boolean addEmptyTask) throws IOException {
        try (XWPFDocument doc = new XWPFDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            // Level header (must be bold)
            XWPFParagraph pLevel = doc.createParagraph();
            XWPFRun rLevel = pLevel.createRun();
            rLevel.setBold(true);
            rLevel.setText("LEVEL " + levelNumber + " - TEST TITLE");

            for (int i = 1; i <= subLevelCount; i++) {
                // Sublevel (must be bold)
                XWPFParagraph pSub = doc.createParagraph();
                XWPFRun rSub = pSub.createRun();
                rSub.setBold(true);
                rSub.setText("Sub-level " + i + ": Sub Topic " + i);

                // Task (normal text)
                XWPFParagraph pTask = doc.createParagraph();
                XWPFRun rTask = pTask.createRun();
                if (addEmptyTask && i == 1) {
                    rTask.setText(" "); // Empty or whitespace task content
                } else {
                    rTask.setText("Task content for sub " + i);
                }
            }

            doc.write(out);
            return out.toByteArray();
        }
    }

    @Test
    @DisplayName("Should successfully import a valid Stage 1 English docx file and create success audit log")
    void testSuccessfulImportStage1() throws Exception {
        byte[] docxBytes = createMockDocx(1, 6, false); // Valid Stage 1: level 1, 6 sublevels
        MockMultipartFile file = new MockMultipartFile("file", "valid_stage1.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", docxBytes);

        String token = generateToken("3"); // Creator role (ROLE_CREATOR)

        mockMvc.perform(multipart("/api/curriculum/import")
                        .file(file)
                        .param("language", "ENGLISH")
                        .param("overwrite", "false")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.levelsImported").value(1))
                .andExpect(jsonPath("$.subLevelsImported").value(6))
                .andExpect(jsonPath("$.tasksImported").value(6));

        // Check DB levels saved
        List<Level> levels = levelRepo.findByLanguageOrderByLevelNumberAsc(Language.ENGLISH);
        assertThat(levels).hasSize(1);
        assertThat(levels.get(0).getLevelNumber()).isEqualTo(1);
        assertThat(levels.get(0).getSubLevels()).hasSize(6);

        // Check audit log saved as SUCCESS
        List<CurriculumImport> imports = importRepo.findAllByOrderByImportedAtDesc();
        assertThat(imports).hasSize(1);
        assertThat(imports.get(0).getStatus()).isEqualTo(ImportStatus.SUCCESS);
        assertThat(imports.get(0).getLevelsImported()).isEqualTo(1);
        assertThat(imports.get(0).getStage()).isEqualTo(1);
        assertThat(imports.get(0).getImportedByUserId()).isEqualTo("TEST-USER-ID");
    }

    @Test
    @DisplayName("Should reject Stage 1 level that does not have exactly 6 sublevels and create failed audit log")
    void testStage1ValidationFailsForSublevelCount() throws Exception {
        byte[] docxBytes = createMockDocx(1, 5, false); // Stage 1 level 1 with only 5 sublevels (Invalid!)
        MockMultipartFile file = new MockMultipartFile("file", "invalid_sublevel_count.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", docxBytes);

        String token = generateToken("3");

        mockMvc.perform(multipart("/api/curriculum/import")
                        .file(file)
                        .param("language", "ENGLISH")
                        .param("overwrite", "false")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Level 1 must have exactly 6 sub-levels. Found: 5"));

        // Verify no levels were saved to DB
        assertThat(levelRepo.findAll()).isEmpty();

        // Verify audit log has a FAILED entry with errorMessage
        List<CurriculumImport> imports = importRepo.findAllByOrderByImportedAtDesc();
        assertThat(imports).hasSize(1);
        assertThat(imports.get(0).getStatus()).isEqualTo(ImportStatus.FAILED);
        assertThat(imports.get(0).getErrorMessage()).contains("must have exactly 6 sub-levels");
    }

    @Test
    @DisplayName("Should reject import with duplicate levelNumber (if overwrite=false) and create failed audit log")
    void testDuplicateLevelNumberRejection() throws Exception {
        // Pre-save level 1 in DB
        Level level = Level.builder()
                .language(Language.ENGLISH)
                .stage(1)
                .levelNumber(1)
                .title("EXISTING LEVEL")
                .durationMinutes(60)
                .build();
        levelRepo.save(level);

        byte[] docxBytes = createMockDocx(1, 6, false);
        MockMultipartFile file = new MockMultipartFile("file", "duplicate_level.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", docxBytes);

        String token = generateToken("3");

        mockMvc.perform(multipart("/api/curriculum/import")
                        .file(file)
                        .param("language", "ENGLISH")
                        .param("overwrite", "false")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Level number 1 already exists for language ENGLISH"));

        // Check audit log
        List<CurriculumImport> imports = importRepo.findAllByOrderByImportedAtDesc();
        assertThat(imports).hasSize(1);
        assertThat(imports.get(0).getStatus()).isEqualTo(ImportStatus.FAILED);
    }

    @Test
    @DisplayName("Should reject import with duplicate SHA-256 hash (if overwrite=false)")
    void testIdempotentFileHashDuplicateRejection() throws Exception {
        byte[] docxBytes = createMockDocx(1, 6, false);
        MockMultipartFile file = new MockMultipartFile("file", "same_file.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", docxBytes);

        String token = generateToken("3");

        // 1. First import (SUCCESS)
        mockMvc.perform(multipart("/api/curriculum/import")
                        .file(file)
                        .param("language", "ENGLISH")
                        .param("overwrite", "false")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // 2. Second import with same file and overwrite=false (Should fail)
        mockMvc.perform(multipart("/api/curriculum/import")
                        .file(file)
                        .param("language", "ENGLISH")
                        .param("overwrite", "false")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("This file has already been imported successfully."));
    }

    @Test
    @DisplayName("Should reject import with empty task content")
    void testEmptyTaskContentValidation() throws Exception {
        byte[] docxBytes = createMockDocx(1, 6, true); // true: adds an empty task content
        MockMultipartFile file = new MockMultipartFile("file", "empty_task.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", docxBytes);

        String token = generateToken("3");

        mockMvc.perform(multipart("/api/curriculum/import")
                        .file(file)
                        .param("language", "ENGLISH")
                        .param("overwrite", "false")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Level 1 Sub-level 1 has a task with empty content."));
    }

    @Test
    @DisplayName("Should cache getLevels results and evict cache on import and delete")
    void testCacheOperations() throws Exception {
        // Pre-save level to DB
        Level level = Level.builder()
                .language(Language.ENGLISH)
                .stage(1)
                .levelNumber(2)
                .title("LEVEL TWO")
                .durationMinutes(60)
                .build();
        levelRepo.save(level);

        String token = generateToken("3");
        Cache cache = cacheManager.getCache("curriculum");
        assertThat(cache).isNotNull();
        cache.clear();

        // 1. Call GET /api/curriculum/levels
        mockMvc.perform(get("/api/curriculum/levels")
                        .param("language", "ENGLISH")
                        .param("stage", "1"))
                .andExpect(status().isOk());

        // Verify it is cached
        Object cachedObj = cache.get("levels:ENGLISH:1");
        assertThat(cachedObj).isNotNull();

        // 2. Perform import (should clear cache)
        byte[] docxBytes = createMockDocx(5, 6, false); // Level 5, Stage 1
        MockMultipartFile file = new MockMultipartFile("file", "import_evicts.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", docxBytes);

        mockMvc.perform(multipart("/api/curriculum/import")
                        .file(file)
                        .param("language", "ENGLISH")
                        .param("overwrite", "false")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // Verify cache is evicted (empty)
        assertThat(cache.get("levels:ENGLISH:1")).isNull();

        // 3. Populate cache again
        mockMvc.perform(get("/api/curriculum/levels")
                        .param("language", "ENGLISH")
                        .param("stage", "1"))
                .andExpect(status().isOk());
        assertThat(cache.get("levels:ENGLISH:1")).isNotNull();

        // 4. Perform DELETE (should clear cache)
        mockMvc.perform(delete("/api/curriculum")
                        .param("language", "ENGLISH")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        // Verify cache is evicted again
        assertThat(cache.get("levels:ENGLISH:1")).isNull();
    }

    @Test
    @DisplayName("Should successfully retrieve import history and detailed reports")
    void testQueryImportAudits() throws Exception {
        String token = generateToken("3");

        // 1. History is initially empty
        mockMvc.perform(get("/api/curriculum/imports")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());

        // 2. Insert direct audit log in repo
        CurriculumImport imp = CurriculumImport.builder()
                .fileName("manual_log.docx")
                .fileHash("hash123")
                .language(Language.ENGLISH)
                .stage(1)
                .status(ImportStatus.SUCCESS)
                .levelsImported(1)
                .subLevelsImported(6)
                .tasksImported(6)
                .importedByUserId("MANUAL")
                .build();
        imp = importRepo.save(imp);
        UUID impId = imp.getId();

        // 3. Query all imports
        mockMvc.perform(get("/api/curriculum/imports")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].fileName").value("manual_log.docx"))
                .andExpect(jsonPath("$[0].status").value("SUCCESS"));

        // 4. Query single import
        mockMvc.perform(get("/api/curriculum/imports/" + impId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(impId.toString()))
                .andExpect(jsonPath("$.fileName").value("manual_log.docx"));
        
        // 5. Query non-existent single import
        mockMvc.perform(get("/api/curriculum/imports/" + UUID.randomUUID())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

}
