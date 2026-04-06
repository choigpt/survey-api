package com.example.surveyapi.dto.request;

import com.example.surveyapi.entity.Question;
import com.example.surveyapi.entity.QuestionType;
import com.example.surveyapi.repository.AnswerBulkRepository.AnswerRow;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record AnswerRequest(
        @NotNull(message = "질문 ID는 필수입니다") Long questionId,
        List<Long> selectedOptionIds,
        String textValue
) {
    public List<AnswerRow> toAnswerRows(Question question) {
        if (question.getType().isChoiceType()) {
            if (selectedOptionIds == null || selectedOptionIds.isEmpty()) {
                throw new IllegalArgumentException("선택 질문에 선택지가 필요합니다: " + question.getContent());
            }
            List<Long> ids = question.getType() == QuestionType.SINGLE_CHOICE
                    ? List.of(selectedOptionIds.get(0))
                    : selectedOptionIds;
            return ids.stream()
                    .map(optionId -> new AnswerRow(question.getId(), optionId, null))
                    .toList();
        }
        return List.of(new AnswerRow(question.getId(), null, textValue));
    }
}
