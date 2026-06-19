package com.lisa.curriculum.service;

import com.lisa.curriculum.dto.PinnedMaterialRequestDto;
import com.lisa.curriculum.dto.RoomSessionStateDto.PinnedMaterialDto;
import com.lisa.curriculum.entity.*;
import com.lisa.curriculum.exception.ResourceNotFoundException;
import com.lisa.curriculum.repository.PinnedMaterialRepository;
import com.lisa.curriculum.repository.RoomLearningSessionRepository;
import com.lisa.curriculum.security.CurrentUserHelper;
import com.lisa.curriculum.security.LmsUserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PinnedMaterialService {

    private final PinnedMaterialRepository pinnedMaterialRepo;
    private final RoomLearningSessionRepository sessionRepo;
    private final LmsCacheService cacheService;

    @Transactional
    public PinnedMaterialDto pinMaterial(UUID sessionId, PinnedMaterialRequestDto request) {
        RoomLearningSession session = sessionRepo.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found: " + sessionId));

        if (session.getStatus() != SessionStatus.WAITING && session.getStatus() != SessionStatus.LIVE) {
            throw new IllegalStateException("Can only pin materials when session is WAITING or LIVE");
        }

        checkMentorPermission(session);

        LmsUserPrincipal currentUser = CurrentUserHelper.getCurrentUser();
        String userId = currentUser != null ? currentUser.getUserId() : "SYSTEM";

        MaterialType materialType;
        try {
            materialType = MaterialType.valueOf(request.getMaterialType().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid material type: " + request.getMaterialType());
        }

        PinnedMaterial material = PinnedMaterial.builder()
                .roomSessionId(sessionId)
                .title(request.getTitle())
                .materialType(materialType)
                .url(request.getUrl())
                .displayOrder(request.getDisplayOrder() != null ? request.getDisplayOrder() : 0)
                .active(true)
                .pinnedByUserId(userId)
                .pinnedAt(Instant.now())
                .build();

        material = pinnedMaterialRepo.save(material);
        cacheService.evictMentorDashboard(session.getMentorUserId());
        cacheService.evictRoomState(sessionId);
        return mapToDto(material);
    }

    @Transactional
    public void unpinMaterial(Long materialId) {
        PinnedMaterial material = pinnedMaterialRepo.findById(materialId)
                .orElseThrow(() -> new ResourceNotFoundException("Pinned material not found: " + materialId));

        RoomLearningSession session = sessionRepo.findById(material.getRoomSessionId())
                .orElseThrow(() -> new ResourceNotFoundException("Session not found: " + material.getRoomSessionId()));

        checkMentorPermission(session);

        material.setActive(false);
        pinnedMaterialRepo.save(material);
        cacheService.evictMentorDashboard(session.getMentorUserId());
        cacheService.evictRoomState(session.getId());
    }

    @Transactional(readOnly = true)
    public List<PinnedMaterialDto> listActiveMaterials(UUID sessionId) {
        if (!sessionRepo.existsById(sessionId)) {
            throw new ResourceNotFoundException("Session not found: " + sessionId);
        }
        return pinnedMaterialRepo.findByRoomSessionIdAndActiveTrueOrderByDisplayOrderAsc(sessionId)
                .stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    private void checkMentorPermission(RoomLearningSession session) {
        LmsUserPrincipal currentUser = CurrentUserHelper.getCurrentUser();
        if (currentUser == null) {
            return;
        }
        boolean isAdmin = currentUser.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (!isAdmin && !session.getMentorUserId().equals(currentUser.getUserId())) {
            throw new AccessDeniedException("Mentor is only allowed to manage their own sessions");
        }
    }

    private PinnedMaterialDto mapToDto(PinnedMaterial m) {
        return PinnedMaterialDto.builder()
                .id(m.getId())
                .title(m.getTitle())
                .materialType(m.getMaterialType().name())
                .url(m.getUrl())
                .displayOrder(m.getDisplayOrder())
                .active(m.isActive())
                .pinnedByUserId(m.getPinnedByUserId())
                .pinnedAt(m.getPinnedAt())
                .build();
    }
}
