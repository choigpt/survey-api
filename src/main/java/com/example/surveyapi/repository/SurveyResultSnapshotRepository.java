package com.example.surveyapi.repository;

import com.example.surveyapi.entity.SurveyResultSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SurveyResultSnapshotRepository extends JpaRepository<SurveyResultSnapshot, Long> {

    Optional<SurveyResultSnapshot> findBySurveyId(Long surveyId);
}
