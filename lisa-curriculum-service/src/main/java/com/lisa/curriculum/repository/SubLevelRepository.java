package com.lisa.curriculum.repository;
import com.lisa.curriculum.entity.SubLevel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface SubLevelRepository extends JpaRepository<SubLevel, Long> {
    List<SubLevel> findByLevelIdOrderBySubNumberAsc(Long levelId);
}
