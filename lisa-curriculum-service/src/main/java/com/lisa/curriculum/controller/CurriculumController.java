package com.lisa.curriculum.controller;

import com.lisa.curriculum.dto.*;
import com.lisa.curriculum.entity.Language;
import com.lisa.curriculum.service.CurriculumService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

@RestController
@RequestMapping("/api/curriculum")
@RequiredArgsConstructor
public class CurriculumController {

    private final CurriculumService curriculumService;

    // ─── IMPORT ─────────────────────────────────────────────────────

    @Tag(name = "Import")
    @Operation(
        summary = "Import file Word (.docx) vào database",
        description = """
            Parse 1 file .docx curriculum và lưu vào DB.
            
            **Các file hỗ trợ:**
            - `Eng_-_STAGE_1__LEVELS_1-30_.docx` → language=ENGLISH
            - `Eng_-_STAGE_2__LEVEL_31-60_.docx` → language=ENGLISH, overwrite=false
            - `Chinese_-_level_1-30.docx` → language=CHINESE
            - `Chinese_-_level_31-60.docx` → language=CHINESE, overwrite=false
            - `Janpanes_-_stage1_level1-30.docx` → language=JAPANESE
            - v.v...
            
            **overwrite=true** sẽ xóa level đã tồn tại trước khi import lại.
            """
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Import thành công"),
        @ApiResponse(responseCode = "400", description = "File lỗi hoặc parser không đọc được")
    })
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImportResultDto> importFile(
            @Parameter(description = "File .docx cần import", required = true)
            @RequestParam("file") MultipartFile file,

            @Parameter(description = "Ngôn ngữ của file", required = true,
                       schema = @Schema(allowableValues = {"ENGLISH", "CHINESE", "JAPANESE"}))
            @RequestParam Language language,

            @Parameter(description = "true = ghi đè level đã tồn tại")
            @RequestParam(defaultValue = "false") boolean overwrite) {

        return ResponseEntity.ok(curriculumService.importFile(file, language, overwrite));
    }

    // ─── QUERY LEVELS ────────────────────────────────────────────────

    @Tag(name = "Levels")
    @Operation(
        summary = "Lấy danh sách levels",
        description = "Lấy tất cả levels theo ngôn ngữ. Có thể lọc thêm theo stage (1/2/3)."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Danh sách levels"),
        @ApiResponse(responseCode = "400", description = "language không hợp lệ")
    })
    @GetMapping("/levels")
    public ResponseEntity<List<LevelDto>> getLevels(
            @Parameter(description = "Ngôn ngữ", required = true,
                       schema = @Schema(allowableValues = {"ENGLISH","CHINESE","JAPANESE"}))
            @RequestParam Language language,

            @Parameter(description = "Stage (1=beginner, 2=intermediate, 3=advanced). Bỏ trống = lấy hết")
            @RequestParam(required = false) Integer stage) {

        return ResponseEntity.ok(curriculumService.getLevels(language, stage));
    }

    @Tag(name = "Levels")
    @Operation(summary = "Chi tiết 1 level theo ID (kèm sub-levels + tasks)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Level detail"),
        @ApiResponse(responseCode = "404", description = "Level không tồn tại")
    })
    @GetMapping("/levels/{id}")
    public ResponseEntity<LevelDto> getLevelById(
            @Parameter(description = "ID của level trong DB") @PathVariable Long id) {

        return ResponseEntity.ok(curriculumService.getLevelById(id));
    }

    @Tag(name = "Levels")
    @Operation(
        summary = "Tìm level theo ngôn ngữ + số level",
        description = "Ví dụ: language=ENGLISH&levelNumber=5 → Level 5 tiếng Anh"
    )
    @ApiResponse(responseCode = "200", description = "Level detail")
    @GetMapping("/levels/by-number")
    public ResponseEntity<LevelDto> getLevelByNumber(
            @Parameter(description = "Ngôn ngữ", required = true,
                       schema = @Schema(allowableValues = {"ENGLISH","CHINESE","JAPANESE"}))
            @RequestParam Language language,

            @Parameter(description = "Số level (1–100)", required = true, example = "5")
            @RequestParam int levelNumber) {

        return ResponseEntity.ok(curriculumService.getLevelByNumber(language, levelNumber));
    }

    @Tag(name = "Levels")
    @Operation(summary = "Lấy sub-levels của 1 level")
    @GetMapping("/levels/{id}/sub-levels")
    public ResponseEntity<List<SubLevelDto>> getSubLevels(
            @Parameter(description = "ID của level") @PathVariable Long id) {

        return ResponseEntity.ok(curriculumService.getSubLevels(id));
    }

    // ─── STATS & DELETE ──────────────────────────────────────────────

    @Tag(name = "Stats")
    @Operation(
        summary = "Thống kê số levels đã import",
        description = "Trả về số levels đã có trong DB cho từng ngôn ngữ."
    )
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Long>> getStats() {
        return ResponseEntity.ok(curriculumService.getStats());
    }

    @Tag(name = "Import")
    @Operation(
        summary = "Xóa toàn bộ data của 1 ngôn ngữ",
        description = "⚠️ Cẩn thận: xóa hết levels + sub-levels + tasks của ngôn ngữ đó."
    )
    @ApiResponse(responseCode = "204", description = "Đã xóa")
    @DeleteMapping
    public ResponseEntity<Void> deleteByLanguage(
            @Parameter(description = "Ngôn ngữ cần xóa", required = true,
                       schema = @Schema(allowableValues = {"ENGLISH","CHINESE","JAPANESE"}))
            @RequestParam Language language) {

        curriculumService.deleteByLanguage(language);
        return ResponseEntity.noContent().build();
    }
}
