package com.example.surveyapi.dto.request;

import com.example.surveyapi.entity.Answer;
import com.example.surveyapi.entity.Question;
import com.example.surveyapi.entity.Survey;
import com.example.surveyapi.entity.SurveySubmission;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public record SubmitResponseRequest(
        @NotBlank(message = "응답자 이름은 필수입니다") String respondent,
        @NotEmpty(message = "답변은 최소 1개 이상 필요합니다") @Valid List<AnswerRequest> answers
) {
    public SurveySubmission toSubmission(Survey survey) {
        return SurveySubmission.builder()
                .respondent(respondent)
                .survey(survey)
                .build();
    }

    public List<Answer> toAnswers(Survey survey) {
        Map<Long, Question> questionMap = survey.getQuestions().stream()
                .collect(Collectors.toMap(Question::getId, q -> q));

        List<Answer> result = new ArrayList<>();
        for (AnswerRequest ar : answers) {
            Question question = questionMap.get(ar.questionId());
            if (question == null) {
                throw new IllegalArgumentException("유효하지 않은 질문 ID: " + ar.questionId());
            }
            result.addAll(ar.toAnswers(question));
        }
        return result;
    }
}
