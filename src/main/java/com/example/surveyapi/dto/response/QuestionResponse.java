package com.example.surveyapi.dto.response;

import com.example.surveyapi.entity.Question;
import com.example.surveyapi.entity.QuestionType;

import java.util.List;

public record QuestionResponse(
        Long id,
        String content,
        QuestionType type,
        Integer orderIndex,
        Boolean required,
        List<OptionResponse> options
) {
    public static QuestionResponse from(Question question) {
        return new QuestionResponse(
                question.getId(),
                question.getContent(),
                question.getType(),
                question.getOrderIndex(),
                question.getRequired(),
                question.getOptions().stream().map(OptionResponse::from).toList()
        );
    }
}
