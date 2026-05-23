package com.lisa.curriculum.service.parser;

import com.lisa.curriculum.entity.*;
import org.apache.poi.xwpf.usermodel.*;
import org.junit.jupiter.api.*;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class ChineseParserTest {

    private ChineseParser parser;
    private XWPFDocument doc;

    @BeforeEach void setUp() { parser = new ChineseParser(); doc = new XWPFDocument(); }

    private void addBold(String text) {
        XWPFParagraph p = doc.createParagraph();
        XWPFRun r = p.createRun(); r.setBold(true); r.setText(text);
    }
    private void addItalic(String text) {
        XWPFParagraph p = doc.createParagraph();
        XWPFRun r = p.createRun(); r.setItalic(true); r.setText(text);
    }
    private void addNormal(String text) {
        doc.createParagraph().createRun().setText(text);
    }

    @Test
    @DisplayName("Should parse Chinese level with Q&A and pinyin")
    void testParseLevelWithQA() {
        addBold("1. 介绍");
        addBold("你叫什么名字？");
        addItalic("nǐ jiào shénme míngzi?");
        addNormal("👉 我叫……");
        addItalic("wǒ jiào ……");

        List<Level> levels = parser.parse(doc);
        assertThat(levels).hasSize(1);
        Level l = levels.get(0);
        assertThat(l.getLevelNumber()).isEqualTo(1);
        assertThat(l.getTitle()).isEqualTo("介绍");
        assertThat(l.getLanguage()).isEqualTo(Language.CHINESE);
        assertThat(l.getSubLevels()).isNotEmpty();

        SubLevel sub = l.getSubLevels().get(0);
        assertThat(sub.getTasks()).isNotEmpty();

        // Should have QUESTION with pinyin
        SpeakingTask question = sub.getTasks().stream()
                .filter(t -> t.getTaskType() == TaskType.QUESTION).findFirst().orElse(null);
        assertThat(question).isNotNull();
        assertThat(question.getContent()).isEqualTo("你叫什么名字？");
        assertThat(question.getPronunciation()).isEqualTo("nǐ jiào shénme míngzi?");

        // Should have ANSWER
        SpeakingTask answer = sub.getTasks().stream()
                .filter(t -> t.getTaskType() == TaskType.ANSWER).findFirst().orElse(null);
        assertThat(answer).isNotNull();
        assertThat(answer.getContent()).isEqualTo("我叫……");
    }

    @Test
    @DisplayName("Should assign correct HSK level")
    void testHskMapping() {
        addBold("5. 我的朋友");
        addBold("35. 我的学习计划");

        List<Level> levels = parser.parse(doc);
        assertThat(levels.get(0).getCefrTarget()).isEqualTo("HSK1");
        assertThat(levels.get(1).getCefrTarget()).isEqualTo("HSK3");
    }
}
