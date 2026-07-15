package com.lisa.curriculum.service;

import com.lisa.curriculum.dto.RecordingReadyEventDto;
import com.lisa.curriculum.entity.RoomLearningSession;
import com.lisa.curriculum.entity.SessionRecording;
import com.lisa.curriculum.entity.SessionRecordingStatus;
import com.lisa.curriculum.exception.ResourceNotFoundException;
import com.lisa.curriculum.repository.RoomLearningSessionRepository;
import com.lisa.curriculum.repository.SessionRecordingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RecordingWebhookService {
    private final SessionRecordingRepository recordingRepository;
    private final RoomLearningSessionRepository sessionRepository;

    @Transactional
    @CacheEvict(value = {"recordings", "mentor_dashboard", "room_state"}, allEntries = true)
    public void apply(RecordingReadyEventDto event) {
        RoomLearningSession session = sessionRepository.findById(event.getSessionId())
                .orElseThrow(() -> new ResourceNotFoundException("Session not found: " + event.getSessionId()));
        SessionRecording recording = recordingRepository.findById(event.getRecordingId())
                .orElseGet(() -> SessionRecording.builder()
                        .recordingId(event.getRecordingId())
                        .roomSessionId(session.getId())
                        .build());
        recording.setRoomSessionId(session.getId());
        recording.setProvider(event.getProvider());
        recording.setStorageObjectKey(event.getStorageObjectKey());
        recording.setPodcastId(event.getPodcastId());
        recording.setDurationSeconds(event.getDurationSeconds());
        recording.setStartedAt(event.getStartedAt());
        recording.setEndedAt(event.getEndedAt());
        recording.setStatus(parseStatus(event.getStatus()));
        recordingRepository.save(recording);
    }

    private SessionRecordingStatus parseStatus(String status) {
        try {
            return SessionRecordingStatus.valueOf(status == null ? "MISSING_CONTRACT" : status.toUpperCase());
        } catch (IllegalArgumentException ex) {
            return SessionRecordingStatus.MISSING_CONTRACT;
        }
    }
}
