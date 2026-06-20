package com.lisa.curriculum.repository;

import com.lisa.curriculum.entity.RoomSessionSubLevelHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RoomSessionSubLevelHistoryRepository extends JpaRepository<RoomSessionSubLevelHistory, Long> {
    List<RoomSessionSubLevelHistory> findByRoomSessionIdOrderByChangedAtAscIdAsc(UUID roomSessionId);
}
