package com.lisa.curriculum.service;

import com.lisa.curriculum.dto.*;
import com.lisa.curriculum.entity.*;
import com.lisa.curriculum.exception.*;
import com.lisa.curriculum.repository.*;
import com.lisa.curriculum.service.parser.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CurriculumService {

    private final LevelRepository levelRepo;
    private final SubLevelRepository subLevelRepo;
    private final CurriculumMapper mapper;
    private final EnglishParser englishParser;
    private final ChineseParser chineseParser;
    private final JapaneseParser japaneseParser;

    // ─── IMPORT ─────────────────────────────────────────────

    @Transactional
    public ImportResultDto importFile(MultipartFile file, Language language, boolean overwrite) {
        long start = System.currentTimeMillis();
        List<String> warnings = new ArrayList<>();

        try (XWPFDocument doc = new XWPFDocument(file.getInputStream())) {
            LanguageParser parser = resolveParser(language);
            List<Level> parsed = parser.parse(doc);

            if (parsed.isEmpty()) {
                throw new ParseException("No levels found in file: " + file.getOriginalFilename());
            }

            int levelsImported = 0, subLevelsImported = 0, tasksImported = 0;

            for (Level level : parsed) {
                if (levelRepo.existsByLanguageAndLevelNumber(language, level.getLevelNumber())) {
                    if (overwrite) {
                        levelRepo.findByLanguageAndLevelNumber(language, level.getLevelNumber())
                                .ifPresent(existing -> levelRepo.deleteById(existing.getId()));
                        warnings.add("Overwritten: Level " + level.getLevelNumber());
                    } else {
                        warnings.add("Skipped (already exists): Level " + level.getLevelNumber());
                        continue;
                    }
                }

                Level saved = levelRepo.save(level);
                levelsImported++;
                subLevelsImported += saved.getSubLevels().size();
                tasksImported += saved.getSubLevels().stream()
                        .mapToInt(s -> s.getTasks().size()).sum();
            }

            long elapsed = System.currentTimeMillis() - start;
            log.info("[Import] {} → {} levels, {} sub-levels, {} tasks in {}ms",
                    language, levelsImported, subLevelsImported, tasksImported, elapsed);

            return ImportResultDto.builder()
                    .language(language.name())
                    .levelsImported(levelsImported)
                    .subLevelsImported(subLevelsImported)
                    .tasksImported(tasksImported)
                    .warnings(warnings)
                    .durationMs(elapsed)
                    .build();

        } catch (IOException e) {
            throw new ParseException("Failed to read file: " + file.getOriginalFilename(), e);
        }
    }

    // ─── READ ────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<LevelDto> getLevels(Language language, Integer stage) {
        List<Level> levels = (stage != null)
                ? levelRepo.findByLanguageAndStageOrderByLevelNumberAsc(language, stage)
                : levelRepo.findByLanguageOrderByLevelNumberAsc(language);
        return levels.stream().map(mapper::toDto).collect(Collectors.toList());
    }

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

    @Transactional(readOnly = true)
    public Map<String, Long> getStats() {
        Map<String, Long> stats = new LinkedHashMap<>();
        for (Language lang : Language.values()) {
            stats.put(lang.name().toLowerCase() + "_levels", levelRepo.countByLanguage(lang));
        }
        return stats;
    }

    // ─── DELETE ─────────────────────────────────────────────

    @Transactional
    public void deleteByLanguage(Language language) {
        levelRepo.deleteByLanguage(language);
        log.info("[Delete] Cleared all {} levels", language);
    }

    // ─── PRIVATE ─────────────────────────────────────────────

    private LanguageParser resolveParser(Language language) {
        return switch (language) {
            case ENGLISH  -> englishParser;
            case CHINESE  -> chineseParser;
            case JAPANESE -> japaneseParser;
        };
    }
}
