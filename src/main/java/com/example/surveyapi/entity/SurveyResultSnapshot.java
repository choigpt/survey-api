package com.example.surveyapi.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "survey_result_snapshots", indexes = {
        @Index(name = "idx_snapshot_survey_id", columnList = "surveyId", unique = true)
})
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SurveyResultSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long surveyId;

    @Column(nullable = false)
    private Long totalResponses;

    @Column(columnDefinition = "MEDIUMTEXT", nullable = false)
    private String resultJson;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    @Builder
    private SurveyResultSnapshot(Long surveyId, Long totalResponses, String resultJson) {
        this.surveyId = surveyId;
        this.totalResponses = totalResponses;
        this.resultJson = resultJson;
    }

    public void update(Long totalResponses, String resultJson) {
        this.totalResponses = totalResponses;
        this.resultJson = resultJson;
    }
}
