package com.lisa.curriculum.service.parser;
import com.lisa.curriculum.entity.Level;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import java.util.List;

public interface LanguageParser {
    List<Level> parse(XWPFDocument document);
}
