package com.lisa.curriculum.service.parser;

import com.lisa.curriculum.entity.*;
import org.apache.poi.xwpf.usermodel.*;
import org.junit.jupiter.api.*;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class EnglishParserTest {

    private EnglishParser parser;
    private XWPFDocument doc;

    @BeforeEach
    void setUp() {
        parser = new EnglishParser();
        doc = new XWPFDocument();
    }

    private XWPFParagraph addBoldPara(String text) {
        XWPFParagraph p = doc.createParagraph();
        XWPFRun run = p.createRun();
        run.setBold(true);
        run.setText(text);
        return p;
    }

    private XWPFParagraph addPara(String text) {
        XWPFParagraph p = doc.createParagraph();
        p.createRun().setText(text);
        return p;
    }

    @Test
    @DisplayName("Should parse a single level with sub-levels and bullets")
    void testParseSingleLevel() {
        addBoldPara("LEVEL 1 – SAYING WHO I AM");
        addBoldPara("Sub-level 1: My name");
        addPara("My full name");
        addPara("My nickname");
        addBoldPara("Sub-level 2: Where I'm from");
        addPara("Country");
        addPara("City");

        List<Level> levels = parser.parse(doc);

        assertThat(levels).hasSize(1);
        Level l = levels.get(0);
        assertThat(l.getLevelNumber()).isEqualTo(1);
        assertThat(l.getTitle()).isEqualTo("SAYING WHO I AM");
        assertThat(l.getLanguage()).isEqualTo(Language.ENGLISH);
        assertThat(l.getStage()).isEqualTo(1);
        assertThat(l.getSubLevels()).hasSize(2);

        SubLevel sub1 = l.getSubLevels().get(0);
        assertThat(sub1.getSubNumber()).isEqualTo(1);
        assertThat(sub1.getTopic()).isEqualTo("My name");
        assertThat(sub1.getTasks()).hasSize(2);
        assertThat(sub1.getTasks().get(0).getContent()).isEqualTo("My full name");
        assertThat(sub1.getTasks().get(0).getTaskType()).isEqualTo(TaskType.BULLET);
    }

    @Test
    @DisplayName("Should resolve correct CEFR for different levels")
    void testCefrMapping() {
        addBoldPara("LEVEL 5 – MY HOME");
        addBoldPara("LEVEL 35 – DAILY ROUTINE");
        addBoldPara("LEVEL 65 – ADVANCED TOPIC");

        List<Level> levels = parser.parse(doc);
        assertThat(levels).hasSize(3);
        assertThat(levels.get(0).getCefrTarget()).isEqualTo("A1");
        assertThat(levels.get(1).getCefrTarget()).isEqualTo("A2+");
        assertThat(levels.get(2).getCefrTarget()).isEqualTo("B1");
    }

    @Test
    @DisplayName("Should resolve correct stage")
    void testStageMapping() {
        addBoldPara("LEVEL 1 – TEST");
        addBoldPara("LEVEL 31 – TEST");
        addBoldPara("LEVEL 61 – TEST");

        List<Level> levels = parser.parse(doc);
        assertThat(levels.get(0).getStage()).isEqualTo(1);
        assertThat(levels.get(1).getStage()).isEqualTo(2);
        assertThat(levels.get(2).getStage()).isEqualTo(3);
    }

    @Test
    @DisplayName("Should handle empty document gracefully")
    void testEmptyDocument() {
        List<Level> levels = parser.parse(doc);
        assertThat(levels).isEmpty();
    }

    @Test
    @DisplayName("Should parse multiple levels")
    void testMultipleLevels() {
        addBoldPara("LEVEL 1 – SAYING WHO I AM");
        addBoldPara("Sub-level 1: My name");
        addPara("Full name");
        addBoldPara("LEVEL 2 – COUNTRIES AND LANGUAGES");
        addBoldPara("Sub-level 1: My country");
        addPara("Vietnam");

        List<Level> levels = parser.parse(doc);
        assertThat(levels).hasSize(2);
        assertThat(levels.get(0).getLevelNumber()).isEqualTo(1);
        assertThat(levels.get(1).getLevelNumber()).isEqualTo(2);
    }
}
