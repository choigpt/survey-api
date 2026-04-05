package com.example.surveyapi.dto.response;

import com.example.surveyapi.entity.Question;

import java.util.List;

public record QuestionResultResponse(
        Long questionId,
        String questionContent,
        String questionType,
        List<OptionCountResponse> optionCounts,
        Double averageRating,
        List<String> textAnswers
) {
    public static QuestionResultResponse ofChoice(Question q, List<OptionCountResponse> optionCounts) {
        return new QuestionResultResponse(q.getId(), q.getContent(), q.getType().name(), optionCounts, null, null);
    }

    public static QuestionResultResponse ofRating(Question q, Double averageRating) {
        return new QuestionResultResponse(q.getId(), q.getContent(), q.getType().name(), null, averageRating, null);
    }

    public static QuestionResultResponse ofText(Question q, List<String> textAnswers) {
        return new QuestionResultResponse(q.getId(), q.getContent(), q.getType().name(), null, null, textAnswers);
    }
}
