package com.lisa.curriculum.service;

import com.lisa.curriculum.dto.*;
import com.lisa.curriculum.entity.*;
import com.lisa.curriculum.exception.*;
import com.lisa.curriculum.repository.*;
import com.lisa.curriculum.security.CurrentUserHelper;
import com.lisa.curriculum.security.LmsUserPrincipal;
import com.lisa.curriculum.service.parser.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CurriculumService {

    private final LevelRepository levelRepo;
    private final SubLevelRepository subLevelRepo;
    private final CurriculumImportRepository importRepo;
    private final CurriculumMapper mapper;
    private final EnglishParser englishParser;
    private final ChineseParser chineseParser;
    private final JapaneseParser japaneseParser;

    @Autowired
    @Lazy
    private CurriculumService self;

    // ─── IMPORT ─────────────────────────────────────────────

    public ImportResultDto importFile(MultipartFile file, Language language, boolean overwrite) {
        long start = System.currentTimeMillis();
        String fileHash = computeSha256(file);

        LmsUserPrincipal principal = CurrentUserHelper.getCurrentUser();
        String userId = principal != null ? principal.getUserId() : "SYSTEM";

        // 1. Check duplicate successful hash
        if (!overwrite && importRepo.existsByFileHashAndStatus(fileHash, ImportStatus.SUCCESS)) {
            throw new ImportValidationException("This file has already been imported successfully.");
        }

        List<Level> parsedLevels;
        int parsedStage = 1;
        try {
            // 2. Parse docx
            try (XWPFDocument doc = new XWPFDocument(file.getInputStream())) {
                LanguageParser parser = resolveParser(language);
                parsedLevels = parser.parse(doc);
            } catch (IOException e) {
                throw new ParseException("Failed to read file: " + file.getOriginalFilename(), e);
            }

            if (parsedLevels.isEmpty()) {
                throw new ImportValidationException("No levels found in file.");
            }

            parsedStage = parsedLevels.get(0).getStage();

            // 3. Static Validation Rules
            validateCurriculumRules(parsedLevels, language, parsedStage, overwrite);

            // 4. Save to Database (inside transaction, clear cache)
            return self.saveImportedCurriculum(parsedLevels, language, overwrite, file.getOriginalFilename(), fileHash, userId, parsedStage, start);

        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            // Save FAILED import report in an isolated transaction
            self.saveFailedImportReport(file.getOriginalFilename(), fileHash, language, parsedStage, userId, e.getMessage(), elapsed);
            throw e;
        }
    }

    @Transactional
    @CacheEvict(value = "curriculum", allEntries = true)
    public ImportResultDto saveImportedCurriculum(List<Level> parsedLevels, Language language, boolean overwrite,
                                                  String fileName, String fileHash, String userId, int stage, long startTime) {
        List<String> warnings = new ArrayList<>();
        int levelsImported = 0, subLevelsImported = 0, tasksImported = 0;

        if (overwrite) {
            // Delete only levels matching language and level number
            for (Level level : parsedLevels) {
                Optional<Level> existing = levelRepo.findByLanguageAndLevelNumber(language, level.getLevelNumber());
                if (existing.isPresent()) {
                    levelRepo.delete(existing.get());
                    warnings.add("Overwrote: Level " + level.getLevelNumber());
                }
            }
            levelRepo.flush();
        }

        List<Level> deduplicated = deduplicateLevels(parsedLevels);
        for (Level level : deduplicated) {
            if (!overwrite && levelRepo.existsByLanguageAndLevelNumber(language, level.getLevelNumber())) {
                warnings.add("Skipped (already exists): Level " + level.getLevelNumber());
                continue;
            }

            deduplicateSubLevels(level);

            // Ensure child entities are linked properly to parent
            for (SubLevel sl : level.getSubLevels()) {
                sl.setLevel(level);
                if (sl.getTasks() != null) {
                    for (SpeakingTask task : sl.getTasks()) {
                        task.setSubLevel(sl);
                    }
                }
            }

            Level saved = levelRepo.save(level);
            levelsImported++;
            subLevelsImported += saved.getSubLevels().size();
            tasksImported += saved.getSubLevels().stream()
                    .mapToInt(s -> s.getTasks().size()).sum();
        }

        long elapsed = System.currentTimeMillis() - startTime;

        CurriculumImport importReport = CurriculumImport.builder()
                .fileName(fileName)
                .fileHash(fileHash)
                .language(language)
                .stage(stage)
                .status(ImportStatus.SUCCESS)
                .levelsImported(levelsImported)
                .subLevelsImported(subLevelsImported)
                .tasksImported(tasksImported)
                .importedByUserId(userId)
                .durationMs(elapsed)
                .build();
        importRepo.save(importReport);

        return ImportResultDto.builder()
                .language(language.name())
                .levelsImported(levelsImported)
                .subLevelsImported(subLevelsImported)
                .tasksImported(tasksImported)
                .warnings(warnings)
                .durationMs(elapsed)
                .build();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveFailedImportReport(String fileName, String fileHash, Language language, int stage, String userId, String errorMessage, long durationMs) {
        CurriculumImport failedImport = CurriculumImport.builder()
                .fileName(fileName)
                .fileHash(fileHash)
                .language(language)
                .stage(stage)
                .status(ImportStatus.FAILED)
                .errorMessage(errorMessage)
                .importedByUserId(userId)
                .durationMs(durationMs)
                .build();
        importRepo.save(failedImport);
    }

    // ─── READ ────────────────────────────────────────────────

    @Cacheable(value = "curriculum", key = "'levels:' + #language.name() + ':' + (#stage != null ? #stage : 'all')")
    @Transactional(readOnly = true)
    public List<LevelDto> getLevels(Language language, Integer stage) {
        List<Level> levels = (stage != null)
                ? levelRepo.findByLanguageAndStageOrderByLevelNumberAsc(language, stage)
                : levelRepo.findByLanguageOrderByLevelNumberAsc(language);
        return levels.stream().map(mapper::toDto).collect(Collectors.toList());
    }

    @Cacheable(value = "curriculum", key = "'level:' + #id")
    @Transactional(readOnly = true)
    public LevelDto getLevelById(Long id) {
        return levelRepo.findById(id)
                .map(mapper::toDto)
                .orElseThrow(() -> new ResourceNotFoundException("Level not found: " + id));
    }

    @Transactional(readOnly = true)
    public LevelDto getLevelByNumber(Language language, int levelNumber) {
        return levelRepo.findByLanguageAndLevelNumber(language, levelNumber)
                .map(mapper::toDto)
                .orElseThrow(() -> new ResourceNotFoundException(
                        language + " Level " + levelNumber + " not found"));
    }

    @Transactional(readOnly = true)
    public List<SubLevelDto> getSubLevels(Long levelId) {
        if (!levelRepo.existsById(levelId))
            throw new ResourceNotFoundException("Level not found: " + levelId);
        return subLevelRepo.findByLevelIdOrderBySubNumberAsc(levelId)
                .stream().map(mapper::toDto).collect(Collectors.toList());
    }

    @Cacheable(value = "curriculum", key = "'stats'")
    @Transactional(readOnly = true)
    public Map<String, Long> getStats() {
        Map<String, Long> stats = new LinkedHashMap<>();
        for (Language lang : Language.values()) {
            stats.put(lang.name().toLowerCase() + "_levels", levelRepo.countByLanguage(lang));
        }
        return stats;
    }

    // ─── IMPORT AUDIT READ ──────────────────────────────────

    @Transactional(readOnly = true)
    public List<CurriculumImport> getAllImports() {
        return importRepo.findAllByOrderByImportedAtDesc();
    }

    @Transactional(readOnly = true)
    public CurriculumImport getImportById(UUID id) {
        return importRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Import report not found: " + id));
    }

    // ─── DELETE ─────────────────────────────────────────────

    @Transactional
    @CacheEvict(value = "curriculum", allEntries = true)
    public void deleteByLanguage(Language language) {
        levelRepo.deleteByLanguage(language);
        log.info("[Delete] Cleared all {} levels", language);
    }

    // ─── PRIVATE ─────────────────────────────────────────────

    private String computeSha256(MultipartFile file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(file.getBytes());
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new ParseException("Failed to compute SHA-256 hash of file", e);
        }
    }

    private void validateCurriculumRules(List<Level> levels, Language language, int stage, boolean overwrite) {
        Set<Integer> fileLevelNumbers = new HashSet<>();

        for (Level level : levels) {
            // Check duplicate levelNumber within the file itself
            if (!fileLevelNumbers.add(level.getLevelNumber())) {
                throw new ImportValidationException("Duplicate level number found in file: " + level.getLevelNumber());
            }

            // Check duplicate levelNumber against existing DB records (if overwrite is false)
            if (!overwrite && levelRepo.existsByLanguageAndLevelNumber(language, level.getLevelNumber())) {
                throw new ImportValidationException("Level number " + level.getLevelNumber() + " already exists for language " + language);
            }

            // Validation per stage
            if (level.getStage() == 1) {
                // Range check: 1 to 30
                if (level.getLevelNumber() < 1 || level.getLevelNumber() > 30) {
                    throw new ImportValidationException("Stage 1 level number must be between 1 and 30. Found: " + level.getLevelNumber());
                }
                // Exactly 6 sub-levels
                if (level.getSubLevels().size() != 6) {
                    throw new ImportValidationException("Level " + level.getLevelNumber() + " must have exactly 6 sub-levels. Found: " + level.getSubLevels().size());
                }
                // Duration 60 minutes
                if (level.getDurationMinutes() != 60) {
                    throw new ImportValidationException("Level " + level.getLevelNumber() + " duration must be 60 minutes. Found: " + level.getDurationMinutes());
                }
                // Each sub-level 10 minutes
                for (SubLevel sl : level.getSubLevels()) {
                    if (sl.getDurationMinutes() != 10) {
                        throw new ImportValidationException("Level " + level.getLevelNumber() + " Sub-level " + sl.getSubNumber() + " duration must be 10 minutes. Found: " + sl.getDurationMinutes());
                    }
                }
            } else if (level.getStage() == 2) {
                // Range check: 31 to 60
                if (level.getLevelNumber() < 31 || level.getLevelNumber() > 60) {
                    throw new ImportValidationException("Stage 2 level number must be between 31 and 60. Found: " + level.getLevelNumber());
                }
            }

            // Verify tasks have non-empty content
            for (SubLevel sl : level.getSubLevels()) {
                if (sl.getDurationMinutes() < 10 || sl.getDurationMinutes() > 20) {
                    throw new ImportValidationException("Level " + level.getLevelNumber() + " Sub-level " + sl.getSubNumber() + " duration_minutes must be between 10 and 20. Found: " + sl.getDurationMinutes());
                }
                if (sl.getTasks() != null) {
                    for (SpeakingTask task : sl.getTasks()) {
                        if (task.getContent() == null || task.getContent().trim().isEmpty()) {
                            throw new ImportValidationException("Level " + level.getLevelNumber() + " Sub-level " + sl.getSubNumber() + " has a task with empty content.");
                        }
                    }
                }
            }
        }
    }

    private List<Level> deduplicateLevels(List<Level> levels) {
        Map<Integer, Level> seen = new LinkedHashMap<>();
        for (Level l : levels) {
            seen.putIfAbsent(l.getLevelNumber(), l);
        }
        if (seen.size() < levels.size()) {
            log.warn("[Import] Removed {} duplicate levels", levels.size() - seen.size());
        }
        return new ArrayList<>(seen.values());
    }

    private void deduplicateSubLevels(Level level) {
        Map<Integer, SubLevel> seen = new LinkedHashMap<>();
        for (SubLevel sl : level.getSubLevels()) {
            seen.putIfAbsent(sl.getSubNumber(), sl);
        }
        if (seen.size() < level.getSubLevels().size()) {
            int removed = level.getSubLevels().size() - seen.size();
            log.warn("[Import] Level {}: removed {} duplicate sub-levels",
                    level.getLevelNumber(), removed);
            level.getSubLevels().clear();
            seen.values().forEach(sl -> level.getSubLevels().add(sl));
        }
    }

    private LanguageParser resolveParser(Language language) {
        return switch (language) {
            case ENGLISH -> englishParser;
            case CHINESE -> chineseParser;
            case JAPANESE -> japaneseParser;
        };
    }
}
