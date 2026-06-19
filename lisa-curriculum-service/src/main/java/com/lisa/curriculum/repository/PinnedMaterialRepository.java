package com.lisa.curriculum.repository;

import com.lisa.curriculum.entity.PinnedMaterial;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Collection;
import java.util.UUID;

@Repository
public interface PinnedMaterialRepository extends JpaRepository<PinnedMaterial, Long> {
    List<PinnedMaterial> findByRoomSessionIdAndActiveTrueOrderByDisplayOrderAsc(UUID roomSessionId);
    List<PinnedMaterial> findByRoomSessionIdInAndActiveTrueOrderByPinnedAtDesc(Collection<UUID> roomSessionIds);

    @org.springframework.data.jpa.repository.Query("SELECT COUNT(pm) FROM PinnedMaterial pm WHERE pm.active = true AND pm.roomSessionId IN (SELECT s.id FROM RoomLearningSession s WHERE s.mentorUserId = :mentorId)")
    long countActivePinnedMaterialsForMentor(String mentorId);
}
