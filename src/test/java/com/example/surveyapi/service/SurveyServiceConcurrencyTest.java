package com.example.surveyapi.service;

import com.example.surveyapi.dto.request.AnswerRequest;
import com.example.surveyapi.dto.request.CreateSurveyRequest;
import com.example.surveyapi.dto.request.OptionRequest;
import com.example.surveyapi.dto.request.QuestionRequest;
import com.example.surveyapi.dto.request.SubmitResponseRequest;
import com.example.surveyapi.dto.response.SurveyResponse;
import com.example.surveyapi.entity.QuestionType;
import com.example.surveyapi.entity.SurveyStatus;
import com.example.surveyapi.repository.SurveySubmissionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class SurveyServiceConcurrencyTest {

    @Autowired
    private SurveyService surveyService;

    @Autowired
    private SurveySubmissionRepository submissionRepository;

    @Test
    @DisplayName("50명이 동시에 같은 설문에 응답해도 모두 정상 저장된다")
    void concurrentSubmitResponse() throws InterruptedException {
        // given - 설문 생성 + 활성화
        SurveyResponse survey = surveyService.createSurvey(new CreateSurveyRequest(
                "동시성 테스트 설문", "테스트",
                LocalDate.now(), LocalDate.now().plusDays(7),
                List.of(
                        new QuestionRequest("언어 선택", QuestionType.SINGLE_CHOICE, 1, true,
                                List.of(new OptionRequest("Java", 1), new OptionRequest("Python", 2))),
                        new QuestionRequest("만족도", QuestionType.RATING, 2, true, null)
                )
        ));
        surveyService.updateSurveyStatus(survey.id(), SurveyStatus.ACTIVE);

        Long surveyId = survey.id();
        Long questionId1 = survey.questions().get(0).id();
        Long optionId = survey.questions().get(0).options().get(0).id();
        Long questionId2 = survey.questions().get(1).id();

        // when - 50개 스레드 동시 응답
        int threadCount = 50;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final int idx = i;
            executor.submit(() -> {
                try {
                    surveyService.submitResponse(surveyId, new SubmitResponseRequest(
                            "user_" + idx,
                            List.of(
                                    new AnswerRequest(questionId1, List.of(optionId), null),
                                    new AnswerRequest(questionId2, null, String.valueOf(idx % 5 + 1))
                            )
                    ));
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    System.err.println("Thread " + idx + " failed: " + e.getMessage());
                    failCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        // then - 50개 모두 성공
        assertThat(successCount.get()).isEqualTo(threadCount);
        assertThat(failCount.get()).isZero();
        assertThat(submissionRepository.countBySurveyId(surveyId)).isEqualTo(threadCount);
    }

    @Test
    @DisplayName("여러 스레드가 동시에 설문을 생성해도 모두 정상 저장된다")
    void concurrentCreateSurvey() throws InterruptedException {
        int threadCount = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final int idx = i;
            executor.submit(() -> {
                try {
                    surveyService.createSurvey(new CreateSurveyRequest(
                            "동시 생성 설문 " + idx, null,
                            LocalDate.now(), LocalDate.now().plusDays(7),
                            List.of(new QuestionRequest("질문", QuestionType.TEXT, 1, false, null))
                    ));
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    // fail
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        assertThat(successCount.get()).isEqualTo(threadCount);
    }
}
