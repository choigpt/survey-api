package com.example.surveyapi.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "answers", indexes = {
        @Index(name = "idx_answers_question_option", columnList = "question_id, selectedOptionId"),
        @Index(name = "idx_answers_question_text", columnList = "question_id, textValue(100)")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Answer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", nullable = false)
    private Question question;

    @Column(name = "survey_response_id")
    private Long surveyResponseId;

    private Long selectedOptionId;

    @Column(columnDefinition = "TEXT")
    private String textValue;

    @Builder
    private Answer(Question question, Long selectedOptionId, String textValue) {
        this.question = question;
        this.selectedOptionId = selectedOptionId;
        this.textValue = textValue;
    }
}
