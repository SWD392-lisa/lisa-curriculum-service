package com.lisa.curriculum.service;

import com.lisa.curriculum.dto.SpeakingAssessmentRequestDto;
import com.lisa.curriculum.dto.SpeakingAssessmentResponseDto;
import com.lisa.curriculum.entity.*;
import com.lisa.curriculum.repository.SpeakingAssessmentRepository;
import com.lisa.curriculum.repository.SpeakingTaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SpeakingAssessmentServiceTest {
    @Mock private SpeakingTaskRepository taskRepository;
    @Mock private SpeakingAssessmentRepository assessmentRepository;
    @Mock private MimoClient mimoClient;

    private SpeakingAssessmentService service;
    private SpeakingTask task;
    private SpeakingAssessmentRequestDto request;

    @BeforeEach
    void setUp() {
        service = new SpeakingAssessmentService(taskRepository, assessmentRepository, mimoClient);
        Level level = Level.builder().id(34L).language(Language.ENGLISH).levelNumber(34).title("Places").build();
        SubLevel subLevel = SubLevel.builder().id(199L).level(level).topic("Work/study places").build();
        task = SpeakingTask.builder().id(204L).subLevel(subLevel).content("Describe places where you study.").build();
        request = new SpeakingAssessmentRequestDto();
        request.setTaskId(task.getId());
        request.setTranscript("I study at the library.");
        request.setSpeakingSeconds(12);
        when(taskRepository.findById(task.getId())).thenReturn(Optional.of(task));
        when(taskRepository.findForAssessmentById(task.getId())).thenReturn(Optional.of(task));
    }

    @Test
    void savesFirstAssessmentAsPersonalBest() {
        when(mimoClient.assessSpeaking(any(), any(), anyInt(), any())).thenReturn(score(82));
        when(assessmentRepository.findByLearnerUserIdAndTaskId("learner", task.getId()))
                .thenReturn(Optional.empty());
        when(assessmentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        SpeakingAssessmentResponseDto response = service.assess("learner", request);

        assertThat(response.isPersonalBest()).isTrue();
        assertThat(response.getOverallScore()).isEqualTo(82);
        assertThat(response.getSpeakingSeconds()).isEqualTo(12);
        verify(assessmentRepository).save(any(SpeakingAssessment.class));
    }

    @Test
    void keepsStoredBestWhenNewScoreIsLower() {
        SpeakingAssessment stored = SpeakingAssessment.builder()
                .learnerUserId("learner").task(task).transcript("My stronger answer")
                .overallScore(90).relevanceScore(92).grammarScore(89).vocabularyScore(88)
                .feedback("Strong answer").suggestedAnswer("A polished answer")
                .speakingSeconds(20).assessedAt(Instant.now()).build();
        when(mimoClient.assessSpeaking(any(), any(), anyInt(), any())).thenReturn(score(65));
        when(assessmentRepository.findByLearnerUserIdAndTaskId("learner", task.getId()))
                .thenReturn(Optional.of(stored));

        SpeakingAssessmentResponseDto response = service.assess("learner", request);

        assertThat(response.isPersonalBest()).isFalse();
        assertThat(response.getOverallScore()).isEqualTo(90);
        assertThat(response.getTranscript()).isEqualTo("My stronger answer");
        verify(assessmentRepository, never()).save(any());
    }

    private SpeakingAssessmentResponseDto score(int overall) {
        return SpeakingAssessmentResponseDto.builder()
                .overallScore(overall).relevanceScore(overall).grammarScore(overall)
                .vocabularyScore(overall).feedback("Helpful feedback")
                .suggestedAnswer("A better answer").build();
    }
}
