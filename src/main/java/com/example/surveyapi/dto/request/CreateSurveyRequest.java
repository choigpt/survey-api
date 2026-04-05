package com.example.surveyapi.dto.request;

import com.example.surveyapi.entity.Survey;
import com.example.surveyapi.entity.SurveyStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

public record CreateSurveyRequest(
        @NotBlank(message = "설문 제목은 필수입니다") String title,
        String description,
        @NotNull(message = "시작일은 필수입니다") LocalDate startDate,
        @NotNull(message = "마감일은 필수입니다") LocalDate endDate,
        @NotEmpty(message = "질문은 최소 1개 이상 필요합니다") @Valid List<QuestionRequest> questions
) {
    public Survey toEntity() {
        Survey survey = Survey.builder()
                .title(title)
                .description(description)
                .startDate(startDate)
                .endDate(endDate)
                .status(SurveyStatus.DRAFT)
                .build();
        questions.forEach(qr -> survey.addQuestion(qr.toEntity()));
        return survey;
    }
}
