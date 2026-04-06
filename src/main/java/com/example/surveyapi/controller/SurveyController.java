package com.example.surveyapi.controller;

import com.example.surveyapi.dto.request.CreateSurveyRequest;
import com.example.surveyapi.dto.request.SubmitResponseRequest;
import com.example.surveyapi.dto.response.SurveyResponse;
import com.example.surveyapi.dto.response.SurveyResultResponse;
import com.example.surveyapi.dto.response.SurveySummaryResponse;
import com.example.surveyapi.entity.SurveyStatus;
import com.example.surveyapi.service.SurveyResultService;
import com.example.surveyapi.service.SurveyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/surveys")
@RequiredArgsConstructor
public class SurveyController {

    private final SurveyService surveyService;
    private final SurveyResultService surveyResultService;

    @PostMapping
    public ResponseEntity<SurveyResponse> createSurvey(@Valid @RequestBody CreateSurveyRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(surveyService.createSurvey(request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<SurveyResponse> getSurvey(@PathVariable Long id) {
        return ResponseEntity.ok(surveyService.getSurvey(id));
    }

    @GetMapping
    public ResponseEntity<Page<SurveySummaryResponse>> getAllSurveys(
            @RequestParam(required = false) SurveyStatus status,
            @PageableDefault(size = 20) Pageable pageable) {
        if (status != null) {
            return ResponseEntity.ok(surveyService.getSurveysByStatus(status, pageable));
        }
        return ResponseEntity.ok(surveyService.getAllSurveys(pageable));
    }

    @PostMapping("/{surveyId}/responses")
    public ResponseEntity<Void> submitResponse(
            @PathVariable Long surveyId,
            @Valid @RequestBody SubmitResponseRequest request) {
        surveyService.submitResponse(surveyId, request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @GetMapping("/{surveyId}/results")
    public ResponseEntity<SurveyResultResponse> getSurveyResults(@PathVariable Long surveyId) {
        return ResponseEntity.ok(surveyResultService.getResults(surveyId));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<SurveyResponse> updateStatus(
            @PathVariable Long id,
            @RequestParam SurveyStatus status) {
        return ResponseEntity.ok(surveyService.updateSurveyStatus(id, status));
    }
}
