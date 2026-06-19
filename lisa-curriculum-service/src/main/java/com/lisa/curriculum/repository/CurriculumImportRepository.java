package com.lisa.curriculum.repository;

import com.lisa.curriculum.entity.CurriculumImport;
import com.lisa.curriculum.entity.ImportStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CurriculumImportRepository extends JpaRepository<CurriculumImport, UUID> {
    boolean existsByFileHashAndStatus(String fileHash, ImportStatus status);
    List<CurriculumImport> findAllByOrderByImportedAtDesc();
}
