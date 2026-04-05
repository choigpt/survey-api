package com.example.surveyapi.dto.response;

import com.example.surveyapi.entity.QuestionOption;

public record OptionResponse(
        Long id,
        String content,
        Integer orderIndex
) {
    public static OptionResponse from(QuestionOption option) {
        return new OptionResponse(option.getId(), option.getContent(), option.getOrderIndex());
    }
}
