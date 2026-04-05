package com.example.surveyapi.dto.response;

import com.example.surveyapi.entity.Survey;
import com.example.surveyapi.entity.SurveyStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record SurveyResponse(
        Long id,
        String title,
        String description,
        LocalDate startDate,
        LocalDate endDate,
        SurveyStatus status,
        LocalDateTime createdAt,
        List<QuestionResponse> questions
) {
    public static SurveyResponse from(Survey survey) {
        return new SurveyResponse(
                survey.getId(),
                survey.getTitle(),
                survey.getDescription(),
                survey.getStartDate(),
                survey.getEndDate(),
                survey.getStatus(),
                survey.getCreatedAt(),
                survey.getQuestions().stream().map(QuestionResponse::from).toList()
        );
    }
}
