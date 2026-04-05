package com.example.surveyapi.dto.request;

import com.example.surveyapi.entity.Answer;
import com.example.surveyapi.entity.Question;
import com.example.surveyapi.entity.QuestionType;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record AnswerRequest(
        @NotNull(message = "질문 ID는 필수입니다") Long questionId,
        List<Long> selectedOptionIds,
        String textValue
) {
    public List<Answer> toAnswers(Question question) {
        if (question.getType().isChoiceType()) {
            if (selectedOptionIds == null || selectedOptionIds.isEmpty()) {
                throw new IllegalArgumentException("선택 질문에 선택지가 필요합니다: " + question.getContent());
            }
            List<Long> ids = question.getType() == QuestionType.SINGLE_CHOICE
                    ? List.of(selectedOptionIds.get(0))
                    : selectedOptionIds;
            return ids.stream()
                    .map(optionId -> Answer.builder().question(question).selectedOptionId(optionId).build())
                    .toList();
        }
        return List.of(Answer.builder().question(question).textValue(textValue).build());
    }
}
