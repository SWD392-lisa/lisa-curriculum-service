package com.lisa.curriculum.repository;
import com.lisa.curriculum.entity.SubLevel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface SubLevelRepository extends JpaRepository<SubLevel, Long> {
    List<SubLevel> findByLevelIdOrderBySubNumberAsc(Long levelId);
    boolean existsByIdAndLevelId(Long id, Long levelId);

    @Query("SELECT sl.id FROM SubLevel sl WHERE sl.level.id = :levelId ORDER BY sl.subNumber ASC")
    List<Long> findIdsByLevelIdOrderBySubNumberAsc(Long levelId);

    Optional<SubLevel> findFirstByLevelIdOrderBySubNumberAsc(Long levelId);
}
