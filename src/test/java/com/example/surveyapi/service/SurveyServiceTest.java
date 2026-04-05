package com.example.surveyapi.service;

import com.example.surveyapi.dto.request.*;
import com.example.surveyapi.dto.response.SurveyResponse;
import com.example.surveyapi.entity.*;
import com.example.surveyapi.repository.SurveyRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SurveyServiceTest {

    @Mock
    private SurveyRepository surveyRepository;

    @InjectMocks
    private SurveyService surveyService;

    @Test
    @DisplayName("설문을 생성하면 질문과 선택지가 함께 저장된다")
    void createSurvey_Success() {
        CreateSurveyRequest request = new CreateSurveyRequest(
                "개발자 만족도 조사",
                "개발 환경에 대한 만족도를 조사합니다.",
                LocalDate.of(2026, 4, 1),
                LocalDate.of(2026, 4, 30),
                List.of(new QuestionRequest(
                        "사용하는 주 언어는?",
                        QuestionType.SINGLE_CHOICE, 1, true,
                        List.of(new OptionRequest("Java", 1), new OptionRequest("Python", 2))
                ))
        );

        given(surveyRepository.save(any(Survey.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        SurveyResponse response = surveyService.createSurvey(request);

        assertThat(response.title()).isEqualTo("개발자 만족도 조사");
        assertThat(response.status()).isEqualTo(SurveyStatus.DRAFT);
        assertThat(response.questions()).hasSize(1);
        assertThat(response.questions().get(0).options()).hasSize(2);
        verify(surveyRepository).save(any(Survey.class));
    }

    @Test
    @DisplayName("존재하지 않는 설문 조회 시 예외가 발생한다")
    void getSurvey_NotFound() {
        given(surveyRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> surveyService.getSurvey(999L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("설문을 찾을 수 없습니다");
    }

    @Test
    @DisplayName("DRAFT 상태의 설문에 응답하면 예외가 발생한다")
    void submitResponse_NotActive() {
        given(surveyRepository.findById(1L)).willReturn(Optional.of(buildSurvey(SurveyStatus.DRAFT)));

        SubmitResponseRequest request = new SubmitResponseRequest(
                "홍길동", List.of(new AnswerRequest(1L, null, "답변")));

        assertThatThrownBy(() -> surveyService.submitResponse(1L, request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("활성 상태의 설문에만 응답할 수 있습니다");
    }

    @Test
    @DisplayName("필수 질문에 답변하지 않으면 예외가 발생한다")
    void submitResponse_MissingRequiredAnswer() {
        Survey survey = buildActiveSurveyWithQuestion();
        given(surveyRepository.findById(1L)).willReturn(Optional.of(survey));

        SubmitResponseRequest request = new SubmitResponseRequest(
                "홍길동", List.of(new AnswerRequest(999L, null, "답변")));

        assertThatThrownBy(() -> surveyService.submitResponse(1L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("필수 질문에 대한 답변이 없습니다");
    }

    @Test
    @DisplayName("설문 상태를 ACTIVE로 변경할 수 있다")
    void updateSurveyStatus_Success() {
        given(surveyRepository.findById(1L)).willReturn(Optional.of(buildSurvey(SurveyStatus.DRAFT)));

        SurveyResponse response = surveyService.updateSurveyStatus(1L, SurveyStatus.ACTIVE);

        assertThat(response.status()).isEqualTo(SurveyStatus.ACTIVE);
    }

    @Test
    @DisplayName("전체 설문 목록을 조회할 수 있다")
    void getAllSurveys_Success() {
        given(surveyRepository.findAll()).willReturn(List.of(
                buildSurvey(SurveyStatus.ACTIVE), buildSurvey(SurveyStatus.DRAFT)));

        List<SurveyResponse> responses = surveyService.getAllSurveys();

        assertThat(responses).hasSize(2);
    }

    @Test
    @DisplayName("상태별 설문 목록을 조회할 수 있다")
    void getSurveysByStatus_Success() {
        given(surveyRepository.findByStatus(SurveyStatus.ACTIVE))
                .willReturn(List.of(buildSurvey(SurveyStatus.ACTIVE)));

        List<SurveyResponse> responses = surveyService.getSurveysByStatus(SurveyStatus.ACTIVE);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).status()).isEqualTo(SurveyStatus.ACTIVE);
    }

    private Survey buildSurvey(SurveyStatus status) {
        return Survey.builder()
                .title("테스트 설문")
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusDays(7))
                .status(status)
                .build();
    }

    private Survey buildActiveSurveyWithQuestion() {
        Survey survey = buildSurvey(SurveyStatus.ACTIVE);
        Question question = Question.builder()
                .id(1L)
                .content("필수 질문")
                .type(QuestionType.TEXT)
                .orderIndex(1)
                .required(true)
                .build();
        survey.addQuestion(question);
        return survey;
    }
}
