package com.lisa.curriculum.service;

import com.lisa.curriculum.dto.RecordingPlaybackResponseDto;
import com.lisa.curriculum.dto.SessionRecordingDto;
import com.lisa.curriculum.entity.RoomLearningSession;
import com.lisa.curriculum.entity.SessionRecording;
import com.lisa.curriculum.entity.SessionRecordingStatus;
import com.lisa.curriculum.exception.ResourceNotFoundException;
import com.lisa.curriculum.repository.RoomLearningSessionRepository;
import com.lisa.curriculum.repository.SessionRecordingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SessionRecordingService {

    private final RecordingServiceClient recordingServiceClient;
    private final SessionRecordingRepository sessionRecordingRepository;
    private final RoomLearningSessionRepository sessionRepository;
    private final LmsCacheService cacheService;

    @Transactional
    public SessionRecordingDto startRecording(UUID sessionId, String authorizationHeader) {
        RoomLearningSession session = getSession(sessionId);
        RecordingServiceClient.RecordingRemoteModel remote =
                recordingServiceClient.startRecording(sessionId, authorizationHeader);
        SessionRecording saved = saveRemoteRecording(remote, session.getId());
        evictRecordingCaches(session);
        return toDto(saved);
    }

    @Transactional
    public SessionRecordingDto stopRecording(String recordingId, String authorizationHeader) {
        SessionRecording local = sessionRecordingRepository.findById(recordingId)
                .orElseThrow(() -> new ResourceNotFoundException("Recording not found: " + recordingId));
        RecordingServiceClient.StopRecordingRemoteResponse remote =
                recordingServiceClient.stopRecording(recordingId, authorizationHeader);
        RecordingServiceClient.RecordingRemoteModel preferred =
                remote != null && remote.ready != null ? remote.ready :
                        remote != null ? remote.processing : null;
        if (preferred == null) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "MISSING_CONTRACT_RECORDING_STOP_RESPONSE");
        }
        SessionRecording saved = saveRemoteRecording(preferred, local.getRoomSessionId());
        evictRecordingCaches(getSession(local.getRoomSessionId()));
        return toDto(saved);
    }

    @Transactional
    @Cacheable(value = "recordings", key = "#sessionId")
    public List<SessionRecordingDto> listSessionRecordings(UUID sessionId, String authorizationHeader) {
        getSession(sessionId);
        return syncRemoteRecordings(sessionId, authorizationHeader).stream().map(this::toDto).toList();
    }

    @Transactional
    public SessionRecordingDto getRecording(String recordingId, String authorizationHeader) {
        RecordingServiceClient.RecordingRemoteModel remote =
                recordingServiceClient.getRecording(recordingId, authorizationHeader);
        SessionRecording saved = saveRemoteRecording(remote, resolveSessionId(remote, recordingId));
        return toDto(saved);
    }

    @Transactional
    public RecordingPlaybackResponseDto getPlayback(String recordingId, String authorizationHeader) {
        SessionRecording local = sessionRecordingRepository.findById(recordingId)
                .orElseGet(() -> {
                    RecordingServiceClient.RecordingRemoteModel remote =
                            recordingServiceClient.getRecording(recordingId, authorizationHeader);
                    return saveRemoteRecording(remote, resolveSessionId(remote, recordingId));
                });
        RecordingServiceClient.PlaybackRemoteResponse remote =
                recordingServiceClient.getPlaybackUrl(recordingId, authorizationHeader);
        local.setPlaybackUrl(remote.playbackUrl);
        sessionRecordingRepository.save(local);
        evictRecordingCaches(getSession(local.getRoomSessionId()));
        return RecordingPlaybackResponseDto.builder()
                .recordingId(remote.recordingId)
                .playbackUrl(remote.playbackUrl)
                .playerUrl(remote.playerUrl)
                .hlsUrl(remote.hlsUrl)
                .dashUrl(remote.dashUrl)
                .thumbnailUrl(remote.thumbnailUrl)
                .expiresIn(remote.expiresIn)
                .build();
    }

    @Transactional(readOnly = true)
    public List<SessionRecordingDto> getLatestRecordingsForMentor(String mentorId, int limit) {
        return sessionRecordingRepository.findLatestByMentorUserId(mentorId, PageRequest.of(0, limit))
                .stream()
                .map(this::toDto)
                .toList();
    }

    private List<SessionRecording> syncRemoteRecordings(UUID sessionId, String authorizationHeader) {
        List<RecordingServiceClient.RecordingRemoteModel> remoteRecordings =
                recordingServiceClient.listSessionRecordings(sessionId, authorizationHeader);
        return remoteRecordings.stream()
                .map(item -> saveRemoteRecording(item, sessionId))
                .toList();
    }

    private SessionRecording saveRemoteRecording(RecordingServiceClient.RecordingRemoteModel remote, UUID sessionId) {
        if (remote == null || remote.recordingId == null || remote.recordingId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "MISSING_CONTRACT_RECORDING_ID");
        }

        SessionRecording entity = sessionRecordingRepository.findById(remote.recordingId)
                .orElseGet(() -> SessionRecording.builder()
                        .recordingId(remote.recordingId)
                        .roomSessionId(sessionId)
                        .status(SessionRecordingStatus.REQUESTED)
                        .build());

        entity.setRoomSessionId(sessionId);
        entity.setProvider(remote.provider);
        entity.setStorageObjectKey(remote.storageObjectKey);
        entity.setPodcastId(remote.podcastId);
        entity.setStatus(parseStatus(remote.status));
        entity.setDurationSeconds(remote.durationSeconds);
        entity.setCreatedAt(remote.createdAt != null ? remote.createdAt : entity.getCreatedAt());
        entity.setStartedAt(remote.startedAt);
        entity.setEndedAt(remote.endedAt);
        return sessionRecordingRepository.save(entity);
    }

    private SessionRecordingStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return SessionRecordingStatus.MISSING_CONTRACT;
        }
        try {
            return SessionRecordingStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return SessionRecordingStatus.MISSING_CONTRACT;
        }
    }

    private SessionRecordingDto toDto(SessionRecording entity) {
        return SessionRecordingDto.builder()
                .recordingId(entity.getRecordingId())
                .roomSessionId(entity.getRoomSessionId())
                .playbackUrl(entity.getPlaybackUrl())
                .durationSeconds(entity.getDurationSeconds())
                .status(entity.getStatus().name())
                .createdAt(entity.getCreatedAt())
                .startedAt(entity.getStartedAt())
                .endedAt(entity.getEndedAt())
                .provider(entity.getProvider())
                .storageObjectKey(entity.getStorageObjectKey())
                .podcastId(entity.getPodcastId())
                .build();
    }

    private RoomLearningSession getSession(UUID sessionId) {
        return sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found: " + sessionId));
    }

    private UUID resolveSessionId(RecordingServiceClient.RecordingRemoteModel remote, String recordingId) {
        if (remote == null || remote.sessionId == null || remote.sessionId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "MISSING_CONTRACT_RECORDING_SESSION_ID");
        }
        try {
            return UUID.fromString(remote.sessionId);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "MISSING_CONTRACT_RECORDING_SESSION_ID_FORMAT for recording " + recordingId,
                    ex
            );
        }
    }

    private void evictRecordingCaches(RoomLearningSession session) {
        cacheService.evictRecordings(session.getId());
        cacheService.evictRoomState(session.getId());
        cacheService.evictMentorDashboard(session.getMentorUserId());
    }
}
