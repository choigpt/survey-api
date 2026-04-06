package com.example.surveyapi.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "questions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Question {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 500)
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private QuestionType type;

    @Column(nullable = false)
    private Integer orderIndex;

    @Column(nullable = false)
    private Boolean required;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "question_id", nullable = false)
    private List<QuestionOption> options = new ArrayList<>();

    @Builder
    private Question(Long id, String content, QuestionType type, Integer orderIndex, Boolean required) {
        this.id = id;
        this.content = content;
        this.type = type;
        this.orderIndex = orderIndex;
        this.required = required;
    }

    public void addOption(QuestionOption option) {
        this.options.add(option);
    }
}
