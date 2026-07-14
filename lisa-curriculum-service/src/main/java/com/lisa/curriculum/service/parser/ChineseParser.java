package com.lisa.curriculum.service.parser;

import com.lisa.curriculum.entity.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.*;
import org.springframework.stereotype.Component;
import java.util.*;
import java.util.regex.*;

/**
 * Parses Chinese Level 1-30 and 31-60 docx files.
 * Format: numbered topics (bold) → Q lines (bold, ends ？) → pinyin (italic) → 👉 answers → pinyin
 */
@Slf4j
@Component
public class ChineseParser implements LanguageParser {

    private static final Pattern LEVEL_PATTERN = Pattern.compile("^(\\d+)[.．。、]\\s*(.+)");
    private static final Pattern Q_PREFIX = Pattern.compile("^Q\\d+\\s*[：:]\\s*(.+)");

    @Override
    public List<Level> parse(XWPFDocument document) {
        List<Level> levels = new ArrayList<>();
        Level currentLevel = null;
        SubLevel currentSubLevel = null;
        int taskOrder = 0, subCounter = 0;
        String pendingQ = null;
        boolean expectQPinyin = false;

        for (XWPFParagraph para : document.getParagraphs()) {
            String raw = para.getText().trim();
            if (raw.isEmpty()) continue;
            boolean bold = isBold(para);
            boolean italic = isItalic(para);

            // LEVEL header: "1. 介绍"
            Matcher lm = LEVEL_PATTERN.matcher(raw);
            if (bold && lm.find() && !raw.startsWith("👉")) {
                int num = Integer.parseInt(lm.group(1));
                String title = lm.group(2).trim();
                currentLevel = Level.builder()
                        .language(Language.CHINESE).stage(stageOf(num)).levelNumber(num)
                        .title(title).cefrTarget(hskOf(num)).durationMinutes(durationOf(num)).build();
                levels.add(currentLevel);
                currentSubLevel = null; subCounter = 0; taskOrder = 0; pendingQ = null;
                log.debug("[CN] Level {}: {}", num, title);
                continue;
            }

            if (currentLevel == null) continue;

            // ANSWER: starts with 👉
            if (raw.startsWith("👉")) {
                String answer = raw.substring(2).trim();
                if (currentSubLevel == null) {
                    // create unnamed sub-level
                    subCounter++;
                    currentSubLevel = SubLevel.builder()
                            .subNumber(subCounter).topic("Section " + subCounter)
                            .durationMinutes(10).build();
                    currentLevel.addSubLevel(currentSubLevel);
                    taskOrder = 0;
                }
                if (pendingQ != null) {
                    currentSubLevel.addTask(SpeakingTask.builder()
                            .taskType(TaskType.QUESTION).content(pendingQ).orderIndex(taskOrder++).build());
                    pendingQ = null;
                    expectQPinyin = false;
                }
                currentSubLevel.addTask(SpeakingTask.builder()
                        .taskType(TaskType.ANSWER).content(answer).orderIndex(taskOrder++).build());
                expectQPinyin = false;
                continue;
            }

            // Pinyin (italic) right after a question
            if (italic && expectQPinyin && pendingQ != null) {
                if (currentSubLevel != null) {
                    currentSubLevel.addTask(SpeakingTask.builder()
                            .taskType(TaskType.QUESTION).content(pendingQ)
                            .pronunciation(raw).orderIndex(taskOrder++).build());
                }
                pendingQ = null; expectQPinyin = false;
                continue;
            }

            // Pinyin (italic) right after an answer
            if (italic && !expectQPinyin && currentSubLevel != null) {
                List<SpeakingTask> tasks = currentSubLevel.getTasks();
                if (!tasks.isEmpty()) {
                    SpeakingTask last = tasks.get(tasks.size() - 1);
                    if (last.getTaskType() == TaskType.ANSWER && last.getPronunciation() == null) {
                        last.setPronunciation(raw);
                    }
                }
                continue;
            }

            // Save pending Q without pinyin
            if (pendingQ != null && !expectQPinyin && currentSubLevel != null) {
                currentSubLevel.addTask(SpeakingTask.builder()
                        .taskType(TaskType.QUESTION).content(pendingQ).orderIndex(taskOrder++).build());
                pendingQ = null;
            }

            // QUESTION: bold + ends with ？ or Q-prefix
            String qText = extractQuestion(raw);
            if (qText != null && bold) {
                // new sub-level for each distinct Q-block (Q1 starts a new group)
                if (!raw.matches("^Q[2-9]\\d*[：:].+")) {
                    subCounter++;
                    currentSubLevel = SubLevel.builder()
                            .subNumber(subCounter).topic(qText).durationMinutes(10).build();
                    currentLevel.addSubLevel(currentSubLevel);
                    taskOrder = 0;
                }
                pendingQ = qText;
                expectQPinyin = true;
                continue;
            }

            // Fallback bold non-question content as sub-level topic header
            if (bold && currentLevel != null && !raw.startsWith("👉")) {
                subCounter++;
                currentSubLevel = SubLevel.builder()
                        .subNumber(subCounter).topic(raw).durationMinutes(10).build();
                currentLevel.addSubLevel(currentSubLevel);
                taskOrder = 0;
            }
        }

        log.info("[CN] Parsed {} levels", levels.size());
        return levels;
    }

    private String extractQuestion(String text) {
        Matcher qm = Q_PREFIX.matcher(text);
        if (qm.find()) return qm.group(1);
        if (text.endsWith("？") || text.endsWith("?")) return text;
        return null;
    }

    private boolean isBold(XWPFParagraph p) {
        return p.getRuns().stream().anyMatch(r -> Boolean.TRUE.equals(r.isBold()));
    }
    private boolean isItalic(XWPFParagraph p) {
        return p.getRuns().stream().anyMatch(r -> Boolean.TRUE.equals(r.isItalic()));
    }
    private int stageOf(int n) { return n <= 30 ? 1 : n <= 60 ? 2 : 3; }
    private String hskOf(int n) {
        if (n <= 15) return "HSK1"; if (n <= 30) return "HSK2";
        if (n <= 50) return "HSK3"; return "HSK4";
    }
    private int durationOf(int n) { return n <= 30 ? 60 : 90; }
}
