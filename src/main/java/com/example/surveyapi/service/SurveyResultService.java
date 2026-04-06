package com.example.surveyapi.service;

import com.example.surveyapi.dto.response.OptionCountResponse;
import com.example.surveyapi.dto.response.QuestionResultResponse;
import com.example.surveyapi.dto.response.SurveyResultResponse;
import com.example.surveyapi.entity.*;
import com.example.surveyapi.repository.AnswerRepository;
import com.example.surveyapi.repository.SurveyRepository;
import com.example.surveyapi.repository.SurveyResultSnapshotRepository;
import com.example.surveyapi.repository.SurveySubmissionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SurveyResultService {

    private final SurveyRepository surveyRepository;
    private final SurveySubmissionRepository submissionRepository;
    private final SurveyResultSnapshotRepository snapshotRepository;
    private final AnswerRepository answerRepository;
    private final ObjectMapper objectMapper;

    public SurveyResultResponse getResults(Long surveyId) {
        return snapshotRepository.findBySurveyId(surveyId)
                .map(this::fromSnapshot)
                .orElseGet(() -> aggregateLive(surveyId));
    }

    public SurveyResultResponse aggregateLive(Long surveyId) {
        Survey survey = surveyRepository.findById(surveyId)
                .orElseThrow(() -> new IllegalArgumentException("설문을 찾을 수 없습니다. ID: " + surveyId));

        Map<Long, String> optionNames = buildOptionNameMap(survey);
        long totalResponses = submissionRepository.countBySurveyId(surveyId);

        log.info("실시간 집계: surveyId={}, 총 응답수={}", surveyId, totalResponses);

        return new SurveyResultResponse(
                surveyId, survey.getTitle(), totalResponses,
                survey.getQuestions().stream().map(q -> aggregate(q, optionNames)).toList());
    }

    private SurveyResultResponse fromSnapshot(SurveyResultSnapshot snapshot) {
        try {
            log.debug("스냅샷 조회: surveyId={}", snapshot.getSurveyId());
            return objectMapper.readValue(snapshot.getResultJson(), SurveyResultResponse.class);
        } catch (Exception e) {
            log.warn("스냅샷 파싱 실패, 실시간 집계로 전환: surveyId={}", snapshot.getSurveyId());
            return aggregateLive(snapshot.getSurveyId());
        }
    }

    private Map<Long, String> buildOptionNameMap(Survey survey) {
        return survey.getQuestions().stream()
                .flatMap(q -> q.getOptions().stream())
                .collect(Collectors.toMap(QuestionOption::getId, QuestionOption::getContent));
    }

    private QuestionResultResponse aggregate(Question q, Map<Long, String> optionNames) {
        if (q.getType().isChoiceType()) return QuestionResultResponse.ofChoice(q, countOptions(q.getId(), optionNames));
        if (q.getType() == QuestionType.RATING) return QuestionResultResponse.ofRating(q, calcAverage(q.getId()));
        return QuestionResultResponse.ofText(q, getTextAnswers(q.getId()));
    }

    private List<OptionCountResponse> countOptions(Long questionId, Map<Long, String> optionNames) {
        return answerRepository.countByQuestionIdGroupByOption(questionId).stream()
                .map(oc -> new OptionCountResponse(
                        oc.optionId(), optionNames.getOrDefault(oc.optionId(), "알 수 없음"), oc.count()))
                .toList();
    }

    private Double calcAverage(Long questionId) {
        double avg = getTextAnswers(questionId).stream()
                .mapToDouble(v -> { try { return Double.parseDouble(v); } catch (NumberFormatException e) { return 0.0; } })
                .average().orElse(0.0);
        return Math.round(avg * 100.0) / 100.0;
    }

    private List<String> getTextAnswers(Long questionId) {
        return answerRepository.findTextValuesByQuestionId(questionId);
    }
}
