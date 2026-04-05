package com.example.surveyapi.dto.response;

import java.util.List;

public record SurveyResultResponse(
        Long surveyId,
        String surveyTitle,
        Long totalResponses,
        List<QuestionResultResponse> questionResults
) {}
