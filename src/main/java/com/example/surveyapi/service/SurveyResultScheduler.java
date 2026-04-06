package com.example.surveyapi.service;

import com.example.surveyapi.entity.Survey;
import com.example.surveyapi.entity.SurveyResultSnapshot;
import com.example.surveyapi.entity.SurveyStatus;
import com.example.surveyapi.repository.SurveyRepository;
import com.example.surveyapi.repository.SurveyResultSnapshotRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class SurveyResultScheduler {

    private final SurveyRepository surveyRepository;
    private final SurveyResultSnapshotRepository snapshotRepository;
    private final SurveyResultService resultService;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedRate = 60_000)
    @Transactional
    public void aggregateResults() {
        Page<Survey> activeSurveys = surveyRepository.findByStatus(SurveyStatus.ACTIVE, PageRequest.of(0, 1000));
        int updated = 0;

        for (Survey survey : activeSurveys) {
            try {
                var result = resultService.aggregateLive(survey.getId());
                String json = objectMapper.writeValueAsString(result);

                SurveyResultSnapshot snapshot = snapshotRepository.findBySurveyId(survey.getId())
                        .orElse(SurveyResultSnapshot.builder().surveyId(survey.getId()).build());

                snapshot.update(result.totalResponses(), json);
                snapshotRepository.save(snapshot);
                updated++;
            } catch (Exception e) {
                log.warn("집계 실패: surveyId={}, error={}", survey.getId(), e.getMessage());
            }
        }

        log.info("배치 집계 완료: {}개 설문 갱신", updated);
    }
}
