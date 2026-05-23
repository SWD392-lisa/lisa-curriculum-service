package com.lisa.curriculum.repository;
import com.lisa.curriculum.entity.Language;
import com.lisa.curriculum.entity.Level;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface LevelRepository extends JpaRepository<Level, Long> {
    List<Level> findByLanguageOrderByLevelNumberAsc(Language language);
    List<Level> findByLanguageAndStageOrderByLevelNumberAsc(Language language, int stage);
    Optional<Level> findByLanguageAndLevelNumber(Language language, int levelNumber);
    boolean existsByLanguageAndLevelNumber(Language language, int levelNumber);
    long countByLanguage(Language language);
    void deleteByLanguage(Language language);
}
