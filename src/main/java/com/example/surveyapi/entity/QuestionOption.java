package com.example.surveyapi.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "question_options")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QuestionOption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 300)
    private String content;

    @Column(nullable = false)
    private Integer orderIndex;

    @Builder
    private QuestionOption(Long id, String content, Integer orderIndex) {
        this.id = id;
        this.content = content;
        this.orderIndex = orderIndex;
    }
}
