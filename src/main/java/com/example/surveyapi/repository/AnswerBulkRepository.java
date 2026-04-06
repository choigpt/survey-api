package com.example.surveyapi.repository;

import com.example.surveyapi.entity.Answer;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class AnswerBulkRepository {

    private final JdbcTemplate jdbcTemplate;

    public void saveAll(Long surveyResponseId, List<Answer> answers) {
        jdbcTemplate.batchUpdate(
                "INSERT INTO answers (question_id, selected_option_id, text_value, survey_response_id) VALUES (?, ?, ?, ?)",
                answers,
                answers.size(),
                (ps, answer) -> {
                    ps.setLong(1, answer.getQuestion().getId());
                    if (answer.getSelectedOptionId() != null) {
                        ps.setLong(2, answer.getSelectedOptionId());
                    } else {
                        ps.setNull(2, java.sql.Types.BIGINT);
                    }
                    ps.setString(3, answer.getTextValue());
                    ps.setLong(4, surveyResponseId);
                }
        );
    }
}
