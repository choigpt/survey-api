package com.example.surveyapi.repository;

import com.example.surveyapi.dto.response.OptionCountResponse;
import com.example.surveyapi.entity.Answer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AnswerRepository extends JpaRepository<Answer, Long> {

    @Query("SELECT new com.example.surveyapi.dto.response.OptionCountResponse(a.selectedOptionId, '', COUNT(a)) " +
            "FROM Answer a WHERE a.question.id = :questionId AND a.selectedOptionId IS NOT NULL " +
            "GROUP BY a.selectedOptionId")
    List<OptionCountResponse> countByQuestionIdGroupByOption(@Param("questionId") Long questionId);

    @Query("SELECT a.textValue FROM Answer a " +
            "WHERE a.question.id = :questionId AND a.textValue IS NOT NULL")
    List<String> findTextValuesByQuestionId(@Param("questionId") Long questionId);
}
