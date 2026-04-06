package com.example.surveyapi.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class AnswerBulkRepository {

    private final JdbcTemplate jdbcTemplate;

    public void saveAll(Long surveyResponseId, List<AnswerRow> rows) {
        jdbcTemplate.batchUpdate(
                "INSERT INTO answers (question_id, selected_option_id, text_value, survey_response_id) VALUES (?, ?, ?, ?)",
                rows,
                rows.size(),
                (ps, row) -> {
                    ps.setLong(1, row.questionId());
                    ps.setObject(2, row.selectedOptionId());
                    ps.setString(3, row.textValue());
                    ps.setLong(4, surveyResponseId);
                }
        );
    }

    public record AnswerRow(Long questionId, Long selectedOptionId, String textValue) {}
}
