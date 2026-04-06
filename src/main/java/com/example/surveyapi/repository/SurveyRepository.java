package com.example.surveyapi.repository;

import com.example.surveyapi.entity.Survey;
import com.example.surveyapi.entity.SurveyStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SurveyRepository extends JpaRepository<Survey, Long> {

    Page<Survey> findByStatus(SurveyStatus status, Pageable pageable);
}
