package com.example.surveyapi.service;

import com.example.surveyapi.dto.response.OptionCountResponse;
import com.example.surveyapi.dto.response.SurveyResultResponse;
import com.example.surveyapi.entity.*;
import com.example.surveyapi.repository.AnswerRepository;
import com.example.surveyapi.repository.SurveyRepository;
import com.example.surveyapi.repository.SurveyResultSnapshotRepository;
import com.example.surveyapi.repository.SurveySubmissionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class SurveyResultServiceTest {

    @Mock
    private SurveyRepository surveyRepository;

    @Mock
    private SurveySubmissionRepository submissionRepository;

    @Mock
    private SurveyResultSnapshotRepository snapshotRepository;

    @Mock
    private AnswerRepository answerRepository;

    @Spy
    private ObjectMapper objectMapper;

    @InjectMocks
    private SurveyResultService surveyResultService;

    @Test
    @DisplayName("스냅샷이 없으면 실시간 집계한다 - 선택형")
    void getResults_NoSnapshot_ChoiceQuestion() {
        given(snapshotRepository.findBySurveyId(1L)).willReturn(Optional.empty());

        Survey survey = buildSurveyWithQuestion(QuestionType.SINGLE_CHOICE);
        given(surveyRepository.findById(1L)).willReturn(Optional.of(survey));
        given(submissionRepository.countBySurveyId(1L)).willReturn(10L);
        given(answerRepository.countByQuestionIdGroupByOption(1L))
                .willReturn(List.of(
                        new OptionCountResponse(1L, "", 6L),
                        new OptionCountResponse(2L, "", 4L)));

        SurveyResultResponse result = surveyResultService.getResults(1L);

        assertThat(result.totalResponses()).isEqualTo(10L);
        assertThat(result.questionResults().get(0).optionCounts()).hasSize(2);
    }

    @Test
    @DisplayName("스냅샷이 있으면 스냅샷에서 반환한다")
    void getResults_WithSnapshot() throws Exception {
        SurveyResultResponse expected = new SurveyResultResponse(1L, "테스트", 5L, List.of());
        String json = objectMapper.writeValueAsString(expected);

        SurveyResultSnapshot snapshot = SurveyResultSnapshot.builder()
                .surveyId(1L).totalResponses(5L).resultJson(json).build();
        given(snapshotRepository.findBySurveyId(1L)).willReturn(Optional.of(snapshot));

        SurveyResultResponse result = surveyResultService.getResults(1L);

        assertThat(result.surveyId()).isEqualTo(1L);
        assertThat(result.totalResponses()).isEqualTo(5L);
    }

    @Test
    @DisplayName("별점 질문의 평균을 계산한다")
    void getResults_RatingQuestion() {
        given(snapshotRepository.findBySurveyId(1L)).willReturn(Optional.empty());

        Survey survey = buildSurveyWithQuestion(QuestionType.RATING);
        given(surveyRepository.findById(1L)).willReturn(Optional.of(survey));
        given(submissionRepository.countBySurveyId(1L)).willReturn(3L);
        given(answerRepository.findTextValuesByQuestionId(1L)).willReturn(List.of("4", "5", "3"));

        SurveyResultResponse result = surveyResultService.getResults(1L);

        assertThat(result.questionResults().get(0).averageRating()).isEqualTo(4.0);
    }

    @Test
    @DisplayName("텍스트 질문의 응답 목록을 반환한다")
    void getResults_TextQuestion() {
        given(snapshotRepository.findBySurveyId(1L)).willReturn(Optional.empty());

        Survey survey = buildSurveyWithQuestion(QuestionType.TEXT);
        given(surveyRepository.findById(1L)).willReturn(Optional.of(survey));
        given(submissionRepository.countBySurveyId(1L)).willReturn(2L);
        given(answerRepository.findTextValuesByQuestionId(1L)).willReturn(List.of("좋아요", "개선 필요"));

        SurveyResultResponse result = surveyResultService.getResults(1L);

        assertThat(result.questionResults().get(0).textAnswers()).containsExactly("좋아요", "개선 필요");
    }

    @Test
    @DisplayName("존재하지 않는 설문 결과 조회 시 예외가 발생한다")
    void getResults_NotFound() {
        given(snapshotRepository.findBySurveyId(999L)).willReturn(Optional.empty());
        given(surveyRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> surveyResultService.getResults(999L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("설문을 찾을 수 없습니다");
    }

    private Survey buildSurveyWithQuestion(QuestionType type) {
        Survey survey = Survey.builder()
                .id(1L).title("테스트 설문")
                .startDate(LocalDate.now()).endDate(LocalDate.now().plusDays(7))
                .status(SurveyStatus.ACTIVE).build();

        Question question = Question.builder()
                .id(1L).content("질문").type(type).orderIndex(1).required(true).build();

        if (type.isChoiceType()) {
            question.addOption(QuestionOption.builder().id(1L).content("선택1").orderIndex(1).build());
            question.addOption(QuestionOption.builder().id(2L).content("선택2").orderIndex(2).build());
        }

        survey.addQuestion(question);
        return survey;
    }
}
