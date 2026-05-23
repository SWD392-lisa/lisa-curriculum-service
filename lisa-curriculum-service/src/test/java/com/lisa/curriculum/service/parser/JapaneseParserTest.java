package com.lisa.curriculum.service.parser;

import com.lisa.curriculum.entity.*;
import org.apache.poi.xwpf.usermodel.*;
import org.junit.jupiter.api.*;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class JapaneseParserTest {

    private JapaneseParser parser;
    private XWPFDocument doc;

    @BeforeEach void setUp() { parser = new JapaneseParser(); doc = new XWPFDocument(); }

    private void addBold(String text) {
        XWPFParagraph p = doc.createParagraph();
        XWPFRun r = p.createRun(); r.setBold(true); r.setText(text);
    }
    private void addNormal(String text) {
        doc.createParagraph().createRun().setText(text);
    }

    @Test
    @DisplayName("Should parse Japanese level with sub-levels")
    void testParseJapaneseLevel() {
        addBold("🔹 レベル1 – 自己紹介");
        addBold("1. 名前");
        addNormal("フルネーム／ニックネーム");
        addBold("2. 出身");
        addNormal("国・都市");

        List<Level> levels = parser.parse(doc);
        assertThat(levels).hasSize(1);
        Level l = levels.get(0);
        assertThat(l.getLevelNumber()).isEqualTo(1);
        assertThat(l.getLanguage()).isEqualTo(Language.JAPANESE);
        assertThat(l.getStage()).isEqualTo(1);
        assertThat(l.getSubLevels()).hasSizeGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("Should assign correct JLPT level")
    void testJlptMapping() {
        addBold("🔹 レベル5 – テスト");
        addBold("🔹 レベル35 – テスト");
        addBold("🔹 レベル65 – テスト");

        List<Level> levels = parser.parse(doc);
        assertThat(levels).hasSize(3);
        assertThat(levels.get(0).getCefrTarget()).isEqualTo("N5");
        assertThat(levels.get(1).getCefrTarget()).isEqualTo("N4");
        assertThat(levels.get(2).getCefrTarget()).isEqualTo("N3");
    }

    @Test
    @DisplayName("Should parse Stage 3 level (61-100)")
    void testStage3() {
        addBold("🔹 レベル75 – 上級トピック");
        List<Level> levels = parser.parse(doc);
        assertThat(levels).hasSize(1);
        assertThat(levels.get(0).getStage()).isEqualTo(3);
        assertThat(levels.get(0).getDurationMinutes()).isEqualTo(120);
    }
}
