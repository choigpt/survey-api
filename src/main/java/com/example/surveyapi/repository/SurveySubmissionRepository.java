package com.example.surveyapi.repository;

import com.example.surveyapi.entity.SurveySubmission;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SurveySubmissionRepository extends JpaRepository<SurveySubmission, Long> {

    long countBySurveyId(Long surveyId);
}
