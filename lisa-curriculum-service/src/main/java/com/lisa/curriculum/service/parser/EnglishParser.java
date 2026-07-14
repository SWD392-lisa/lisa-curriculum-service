package com.lisa.curriculum.service.parser;

import com.lisa.curriculum.entity.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.*;
import org.springframework.stereotype.Component;
import java.util.*;
import java.util.regex.*;

@Slf4j
@Component
public class EnglishParser implements LanguageParser {

    private static final Pattern LEVEL_PATTERN =
            Pattern.compile("LEVEL\\s+(\\d+)\\s*[–\\-—]\\s*(.+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern GROUP_PATTERN =
            Pattern.compile("LEVELS?\\s+\\d+[–\\-]\\d+\\s*[:–-]\\s*(.+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern SUBLEVEL_PATTERN =
            Pattern.compile("^(?:Sub-?level\\s*)?(\\d+)[:.)]\\s*(.+)", Pattern.CASE_INSENSITIVE);

    @Override
    public List<Level> parse(XWPFDocument document) {
        List<Level> levels = new ArrayList<>();
        Level currentLevel = null;
        SubLevel currentSubLevel = null;
        String currentGroup = "";
        int taskOrder = 0;

        for (XWPFParagraph para : document.getParagraphs()) {
            String raw = para.getText();
            if (raw.isEmpty()) continue;
            String text = raw.replaceAll("^[🔵🔹📘🎮⏱️🧩🎯✅\\*\\s]+", "").trim();
            if (text.isEmpty()) {
                if (currentSubLevel != null) {
                    currentSubLevel.addTask(SpeakingTask.builder()
                            .taskType(TaskType.BULLET).content("").orderIndex(taskOrder++).build());
                }
                continue;
            }
            boolean bold = isBold(para);

            Matcher gm = GROUP_PATTERN.matcher(text);
            if (bold && gm.find() && text.toUpperCase().contains("LEVEL")) {
                currentGroup = text;
                continue;
            }

            Matcher lm = LEVEL_PATTERN.matcher(text);
            if (bold && lm.find()) {
                int num = Integer.parseInt(lm.group(1));
                String title = clean(lm.group(2));
                currentLevel = Level.builder()
                        .language(Language.ENGLISH).stage(stageOf(num)).levelNumber(num)
                        .title(title.toUpperCase()).cefrTarget(cefrOf(num))
                        .durationMinutes(durationOf(num)).groupLabel(currentGroup).build();
                levels.add(currentLevel);
                currentSubLevel = null; taskOrder = 0;
                log.debug("[EN] Level {}: {}", num, title);
                continue;
            }

            if (currentLevel == null) continue;

            Matcher sm = SUBLEVEL_PATTERN.matcher(text);
            if (bold && sm.find()) {
                int subNum = Integer.parseInt(sm.group(1));
                String topic = clean(sm.group(2));
                if (subNum >= 1 && subNum <= 10) {
                    currentSubLevel = SubLevel.builder()
                            .subNumber(subNum).topic(topic)
                            .durationMinutes(subDurationOf(currentLevel.getLevelNumber())).build();
                    currentLevel.addSubLevel(currentSubLevel);
                    taskOrder = 0;
                    continue;
                }
            }

            if (currentSubLevel != null) {
                String c = clean(text.replaceAll("^[-•*–]+\\s*", ""));
                if (!c.isEmpty()) {
                    currentSubLevel.addTask(SpeakingTask.builder()
                            .taskType(TaskType.BULLET).content(c).orderIndex(taskOrder++).build());
                }
            }
        }

        log.info("[EN] Parsed {} levels", levels.size());
        return levels;
    }

    private boolean isBold(XWPFParagraph p) {
        return p.getRuns().stream().anyMatch(r -> Boolean.TRUE.equals(r.isBold()));
    }
    private String clean(String s) {
        return s.replaceAll("[*_]+", "").replaceAll("\\s+", " ").trim();
    }
    private int stageOf(int n) { return n <= 30 ? 1 : n <= 60 ? 2 : 3; }
    private String cefrOf(int n) {
        if (n <= 10) return "A1"; if (n <= 20) return "A1+"; if (n <= 30) return "A2";
        if (n <= 45) return "A2+"; if (n <= 60) return "B1-"; if (n <= 80) return "B1";
        return "B2";
    }
    private int durationOf(int n) { return n <= 30 ? 60 : n <= 60 ? 90 : 120; }
    private int subDurationOf(int n) { return n <= 30 ? 10 : n <= 60 ? 15 : 20; }
}
