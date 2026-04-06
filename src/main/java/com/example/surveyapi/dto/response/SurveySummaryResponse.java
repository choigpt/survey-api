package com.example.surveyapi.dto.response;

import com.example.surveyapi.entity.Survey;
import com.example.surveyapi.entity.SurveyStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record SurveySummaryResponse(
        Long id,
        String title,
        String description,
        LocalDate startDate,
        LocalDate endDate,
        SurveyStatus status,
        LocalDateTime createdAt
) {
    public static SurveySummaryResponse from(Survey survey) {
        return new SurveySummaryResponse(
                survey.getId(),
                survey.getTitle(),
                survey.getDescription(),
                survey.getStartDate(),
                survey.getEndDate(),
                survey.getStatus(),
                survey.getCreatedAt()
        );
    }
}
