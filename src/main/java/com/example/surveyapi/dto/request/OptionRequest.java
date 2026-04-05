package com.example.surveyapi.dto.request;

import com.example.surveyapi.entity.QuestionOption;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record OptionRequest(
        @NotBlank(message = "선택지 내용은 필수입니다") String content,
        @NotNull(message = "선택지 순서는 필수입니다") Integer orderIndex
) {
    public QuestionOption toEntity() {
        return QuestionOption.builder()
                .content(content)
                .orderIndex(orderIndex)
                .build();
    }
}
