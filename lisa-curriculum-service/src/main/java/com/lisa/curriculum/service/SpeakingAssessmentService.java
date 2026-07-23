package com.lisa.curriculum.service;

import com.lisa.curriculum.dto.SpeakingAssessmentRequestDto;
import com.lisa.curriculum.dto.SpeakingAssessmentResponseDto;
import com.lisa.curriculum.entity.Language;
import com.lisa.curriculum.entity.SpeakingAssessment;
import com.lisa.curriculum.entity.SpeakingTask;
import com.lisa.curriculum.exception.ResourceNotFoundException;
import com.lisa.curriculum.repository.SpeakingAssessmentRepository;
import com.lisa.curriculum.repository.SpeakingTaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SpeakingAssessmentService {
    private final SpeakingTaskRepository taskRepository;
    private final SpeakingAssessmentRepository assessmentRepository;
    private final MimoClient mimoClient;

    @Transactional
    public SpeakingAssessmentResponseDto assess(String learnerUserId, SpeakingAssessmentRequestDto request) {
        SpeakingTask task = taskRepository.findById(request.getTaskId())
                .orElseThrow(() -> new ResourceNotFoundException("Speaking task not found: " + request.getTaskId()));
        if (task.getSubLevel().getLevel().getLanguage() != Language.ENGLISH) {
            throw new IllegalArgumentException("AI speaking assessment currently supports English lessons only");
        }

        String transcript = request.getTranscript().trim();
        SpeakingAssessmentResponseDto score = mimoClient.assessSpeaking(
                task.getContent(),
                task.getSubLevel().getTopic(),
                task.getSubLevel().getLevel().getLevelNumber(),
                transcript);

        task = taskRepository.findForAssessmentById(task.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Speaking task not found: " + request.getTaskId()));

        SpeakingAssessment best = assessmentRepository
                .findByLearnerUserIdAndTaskId(learnerUserId, task.getId())
                .orElse(null);
        boolean personalBest = best == null || score.getOverallScore() > best.getOverallScore();
        if (personalBest) {
            if (best == null) {
                best = SpeakingAssessment.builder()
                        .learnerUserId(learnerUserId)
                        .task(task)
                        .build();
            }
            best.setTranscript(transcript);
            best.setOverallScore(score.getOverallScore());
            best.setRelevanceScore(score.getRelevanceScore());
            best.setGrammarScore(score.getGrammarScore());
            best.setVocabularyScore(score.getVocabularyScore());
            best.setFeedback(score.getFeedback());
            best.setSuggestedAnswer(score.getSuggestedAnswer());
            best.setSpeakingSeconds(request.getSpeakingSeconds());
            best.setAssessedAt(Instant.now());
            best = assessmentRepository.save(best);
        }
        return toResponse(best, personalBest);
    }

    @Transactional(readOnly = true)
    public List<SpeakingAssessmentResponseDto> getBestForSubLevel(String learnerUserId, Long subLevelId) {
        return assessmentRepository.findBestForLearnerAndSubLevel(learnerUserId, subLevelId).stream()
                .map(assessment -> toResponse(assessment, true))
                .toList();
    }

    private SpeakingAssessmentResponseDto toResponse(SpeakingAssessment assessment, boolean personalBest) {
        return SpeakingAssessmentResponseDto.builder()
                .taskId(assessment.getTask().getId())
                .transcript(assessment.getTranscript())
                .overallScore(assessment.getOverallScore())
                .relevanceScore(assessment.getRelevanceScore())
                .grammarScore(assessment.getGrammarScore())
                .vocabularyScore(assessment.getVocabularyScore())
                .feedback(assessment.getFeedback())
                .suggestedAnswer(assessment.getSuggestedAnswer())
                .personalBest(personalBest)
                .speakingSeconds(assessment.getSpeakingSeconds())
                .assessedAt(assessment.getAssessedAt())
                .build();
    }
}
