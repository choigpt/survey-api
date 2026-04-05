package com.example.surveyapi.repository;

import com.example.surveyapi.entity.Survey;
import com.example.surveyapi.entity.SurveyStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SurveyRepository extends JpaRepository<Survey, Long> {

    List<Survey> findByStatus(SurveyStatus status);
}
