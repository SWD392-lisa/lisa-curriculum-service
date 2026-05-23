package com.lisa.curriculum.service;

import com.lisa.curriculum.dto.LevelDto;
import com.lisa.curriculum.entity.*;
import com.lisa.curriculum.exception.ResourceNotFoundException;
import com.lisa.curriculum.repository.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CurriculumServiceTest {

    @Mock LevelRepository levelRepo;
    @Mock SubLevelRepository subLevelRepo;
    @Mock CurriculumMapper mapper;
    @InjectMocks CurriculumService service;

    @Test
    @DisplayName("getLevels returns mapped DTOs for ENGLISH")
    void testGetLevels() {
        Level l1 = Level.builder().id(1L).language(Language.ENGLISH)
                .levelNumber(1).title("TEST").stage(1).build();
        LevelDto dto1 = LevelDto.builder().id(1L).language(Language.ENGLISH)
                .levelNumber(1).title("TEST").stage(1).build();

        when(levelRepo.findByLanguageOrderByLevelNumberAsc(Language.ENGLISH))
                .thenReturn(List.of(l1));
        when(mapper.toDto(l1)).thenReturn(dto1);

        List<LevelDto> result = service.getLevels(Language.ENGLISH, null);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTitle()).isEqualTo("TEST");
        verify(levelRepo).findByLanguageOrderByLevelNumberAsc(Language.ENGLISH);
    }

    @Test
    @DisplayName("getLevelById throws ResourceNotFoundException when not found")
    void testGetLevelByIdNotFound() {
        when(levelRepo.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getLevelById(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    @DisplayName("getStats returns count per language")
    void testGetStats() {
        when(levelRepo.countByLanguage(Language.ENGLISH)).thenReturn(30L);
        when(levelRepo.countByLanguage(Language.CHINESE)).thenReturn(60L);
        when(levelRepo.countByLanguage(Language.JAPANESE)).thenReturn(100L);

        Map<String, Long> stats = service.getStats();
        assertThat(stats.get("english_levels")).isEqualTo(30L);
        assertThat(stats.get("chinese_levels")).isEqualTo(60L);
        assertThat(stats.get("japanese_levels")).isEqualTo(100L);
    }

    @Test
    @DisplayName("deleteByLanguage calls repo delete")
    void testDeleteByLanguage() {
        service.deleteByLanguage(Language.JAPANESE);
        verify(levelRepo).deleteByLanguage(Language.JAPANESE);
    }
}
