package com.lisa.curriculum.service.parser;

import com.lisa.curriculum.entity.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.*;
import org.springframework.stereotype.Component;
import java.util.*;
import java.util.regex.*;

/**
 * Parses Japanese Stage 1 (Level 1-30), Stage 2 (Level 31-60), Stage 3 (Level 61-100) docx files.
 *
 * Format characteristics:
 *  - Level header: "🔹 レベル1 – 自己紹介" or "🔵 レベル31 – 私の日課"  (bold)
 *  - Group header: "🔵 レベル1–5：サバイバルスピーキング"
 *  - Sub-level content: bullet list under each level (Japanese characters)
 *  - Some files have direct sub-level bullets, some have Q&A format
 */
@Slf4j
@Component
public class JapaneseParser implements LanguageParser {

    // "レベル1 – 自己紹介" or "レベル 31 – ..." (with emoji prefix stripped)
    private static final Pattern LEVEL_PATTERN =
            Pattern.compile("レベル\\s*(\\d+)\\s*[–\\-—―]\\s*(.+)");

    // Group: "レベル1–5：サバイバルスピーキング"
    private static final Pattern GROUP_PATTERN =
            Pattern.compile("レベル\\s*\\d+[–\\-]\\d+\\s*[：:].+");

    // Sub-level numeric prefix: "1. 名前" or "1：テーマ"
    private static final Pattern SUBLEVEL_NUM =
            Pattern.compile("^(\\d+)[.．：:]\\s*(.+)");

    @Override
    public List<Level> parse(XWPFDocument document) {
        List<Level> levels = new ArrayList<>();
        Level currentLevel = null;
        SubLevel currentSubLevel = null;
        String currentGroup = "";
        int taskOrder = 0;
        int syntheticSubNum = 0;

        for (XWPFParagraph para : document.getParagraphs()) {
            String raw = para.getText().trim();
            if (raw.isEmpty()) continue;

            // Strip emoji + decoration
            String text = raw.replaceAll("[🔵🔹📘🎮✅⏱️🧩🎯\\s]+", " ").trim();
            if (text.isEmpty()) continue;
            boolean bold = isBold(para);

            // GROUP header
            if (bold && GROUP_PATTERN.matcher(text).find()) {
                currentGroup = raw;
                log.debug("[JP] Group: {}", raw);
                continue;
            }

            // LEVEL header
            Matcher lm = LEVEL_PATTERN.matcher(text);
            if (bold && lm.find()) {
                int num = Integer.parseInt(lm.group(1));
                String title = cleanJp(lm.group(2));
                currentLevel = Level.builder()
                        .language(Language.JAPANESE).stage(stageOf(num)).levelNumber(num)
                        .title(title).cefrTarget(jlptOf(num))
                        .durationMinutes(durationOf(num)).groupLabel(currentGroup).build();
                levels.add(currentLevel);
                currentSubLevel = null; syntheticSubNum = 0; taskOrder = 0;
                log.debug("[JP] Level {}: {}", num, title);
                continue;
            }

            if (currentLevel == null) continue;

            // Numbered sub-level: "1. 名前"
            Matcher sm = SUBLEVEL_NUM.matcher(text);
            if (bold && sm.find()) {
                int subNum = Integer.parseInt(sm.group(1));
                String topic = cleanJp(sm.group(2));
                if (subNum >= 1 && subNum <= 10) {
                    currentSubLevel = SubLevel.builder()
                            .subNumber(subNum).topic(topic)
                            .durationMinutes(subDurationOf(currentLevel.getLevelNumber())).build();
                    currentLevel.addSubLevel(currentSubLevel);
                    taskOrder = 0;
                    log.debug("[JP]   Sub {}: {}", subNum, topic);
                    continue;
                }
            }

            // If no numbered sub-level detected but we have bold text → new sub-level
            if (bold && currentSubLevel == null && currentLevel != null) {
                syntheticSubNum++;
                currentSubLevel = SubLevel.builder()
                        .subNumber(syntheticSubNum).topic(cleanJp(text))
                        .durationMinutes(subDurationOf(currentLevel.getLevelNumber())).build();
                currentLevel.addSubLevel(currentSubLevel);
                taskOrder = 0;
                continue;
            }

            // BULLET content (non-bold under a sub-level)
            if (!bold && currentSubLevel != null) {
                String c = cleanJp(text.replaceAll("^[-•・\\-]+\\s*", ""));
                if (!c.isEmpty()) {
                    currentSubLevel.addTask(SpeakingTask.builder()
                            .taskType(TaskType.BULLET).content(c).orderIndex(taskOrder++).build());
                }
            }

            // Bold continuation inside sub-level (topic detail)
            if (bold && currentSubLevel != null) {
                String c = cleanJp(text);
                if (!c.isEmpty() && !LEVEL_PATTERN.matcher(text).find()
                        && !GROUP_PATTERN.matcher(text).find()) {
                    currentSubLevel.addTask(SpeakingTask.builder()
                            .taskType(TaskType.BULLET).content(c).orderIndex(taskOrder++).build());
                }
            }
        }

        log.info("[JP] Parsed {} levels", levels.size());
        return levels;
    }

    private boolean isBold(XWPFParagraph p) {
        return p.getRuns().stream().anyMatch(r -> Boolean.TRUE.equals(r.isBold()));
    }

    private String cleanJp(String s) {
        return s.replaceAll("[*_【】「」]+", "").replaceAll("\\s+", " ").trim();
    }

    private int stageOf(int n) { return n <= 30 ? 1 : n <= 60 ? 2 : 3; }

    private String jlptOf(int n) {
        if (n <= 15) return "N5"; if (n <= 30) return "N5+"; if (n <= 45) return "N4";
        if (n <= 60) return "N4+"; if (n <= 80) return "N3"; return "N3+";
    }

    private int durationOf(int n) { return n <= 30 ? 60 : n <= 60 ? 90 : 120; }
    private int subDurationOf(int n) { return n <= 30 ? 10 : n <= 60 ? 15 : 20; }
}
