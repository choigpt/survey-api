package com.example.surveyapi.service;

import com.example.surveyapi.dto.request.AnswerRequest;
import com.example.surveyapi.dto.request.CreateSurveyRequest;
import com.example.surveyapi.dto.request.SubmitResponseRequest;
import com.example.surveyapi.dto.response.SurveyResponse;
import com.example.surveyapi.dto.response.SurveySummaryResponse;
import com.example.surveyapi.entity.*;
import com.example.surveyapi.repository.SurveyRepository;
import com.example.surveyapi.repository.SurveySubmissionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SurveyService {

    private final SurveyRepository surveyRepository;
    private final SurveySubmissionRepository submissionRepository;

    @Transactional
    public SurveyResponse createSurvey(CreateSurveyRequest request) {
        SurveyResponse response = SurveyResponse.from(surveyRepository.save(request.toEntity()));
        log.info("설문 생성 완료: id={}, title={}", response.id(), response.title());
        return response;
    }

    public SurveyResponse getSurvey(Long id) {
        return SurveyResponse.from(findSurvey(id));
    }

    public Page<SurveySummaryResponse> getAllSurveys(Pageable pageable) {
        return surveyRepository.findAll(pageable).map(SurveySummaryResponse::from);
    }

    public Page<SurveySummaryResponse> getSurveysByStatus(SurveyStatus status, Pageable pageable) {
        return surveyRepository.findByStatus(status, pageable).map(SurveySummaryResponse::from);
    }

    @Transactional
    public void submitResponse(Long surveyId, SubmitResponseRequest request) {
        Survey survey = findSurvey(surveyId);
        validateActive(survey);
        validateRequiredAnswers(survey, request);
        submissionRepository.save(request.toEntity(survey));
        log.info("응답 제출 완료: surveyId={}, respondent={}", surveyId, request.respondent());
    }

    @Transactional
    public SurveyResponse updateSurveyStatus(Long id, SurveyStatus status) {
        Survey survey = findSurvey(id);
        log.info("설문 상태 변경: id={}, {} -> {}", id, survey.getStatus(), status);
        survey.updateStatus(status);
        return SurveyResponse.from(survey);
    }

    private Survey findSurvey(Long id) {
        return surveyRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("설문을 찾을 수 없습니다. ID: " + id));
    }

    private void validateActive(Survey survey) {
        if (survey.getStatus() != SurveyStatus.ACTIVE) {
            throw new IllegalStateException("활성 상태의 설문에만 응답할 수 있습니다. 현재 상태: " + survey.getStatus());
        }
    }

    private void validateRequiredAnswers(Survey survey, SubmitResponseRequest request) {
        var answeredIds = request.answers().stream()
                .map(AnswerRequest::questionId)
                .collect(Collectors.toSet());

        survey.getQuestions().stream()
                .filter(q -> q.getRequired() && !answeredIds.contains(q.getId()))
                .findFirst()
                .ifPresent(q -> { throw new IllegalArgumentException("필수 질문에 대한 답변이 없습니다: " + q.getContent()); });
    }
}
