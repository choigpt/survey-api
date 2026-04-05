package com.example.surveyapi.dto.request;

import com.example.surveyapi.entity.Question;
import com.example.surveyapi.entity.QuestionType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record QuestionRequest(
        @NotBlank(message = "질문 내용은 필수입니다") String content,
        @NotNull(message = "질문 유형은 필수입니다") QuestionType type,
        @NotNull(message = "질문 순서는 필수입니다") Integer orderIndex,
        @NotNull(message = "필수 여부는 필수입니다") Boolean required,
        @Valid List<OptionRequest> options
) {
    public Question toEntity() {
        Question question = Question.builder()
                .content(content)
                .type(type)
                .orderIndex(orderIndex)
                .required(required)
                .build();
        if (options != null) {
            options.forEach(or -> question.addOption(or.toEntity()));
        }
        return question;
    }
}
