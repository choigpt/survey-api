package com.example.surveyapi.controller;

import com.example.surveyapi.dto.response.SurveyResponse;
import com.example.surveyapi.entity.SurveyStatus;
import com.example.surveyapi.service.SurveyResultService;
import com.example.surveyapi.service.SurveyService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SurveyController.class)
class SurveyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SurveyService surveyService;

    @MockitoBean
    private SurveyResultService surveyResultService;

    @Test
    @DisplayName("GET /api/surveys - 전체 설문 목록을 조회한다")
    void getAllSurveys() throws Exception {
        // given
        SurveyResponse response = new SurveyResponse(
                1L, "만족도 조사", "서비스 만족도를 조사합니다.",
                LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30),
                SurveyStatus.ACTIVE, LocalDateTime.now(),
                Collections.emptyList()
        );

        given(surveyService.getAllSurveys()).willReturn(List.of(response));

        // when & then
        mockMvc.perform(get("/api/surveys"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].title").value("만족도 조사"))
                .andExpect(jsonPath("$[0].status").value("ACTIVE"));
    }

    @Test
    @DisplayName("GET /api/surveys/{id} - 설문 상세를 조회한다")
    void getSurvey() throws Exception {
        // given
        SurveyResponse response = new SurveyResponse(
                1L, "개발 환경 조사", null,
                LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30),
                SurveyStatus.ACTIVE, null,
                Collections.emptyList()
        );

        given(surveyService.getSurvey(1L)).willReturn(response);

        // when & then
        mockMvc.perform(get("/api/surveys/1"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.title").value("개발 환경 조사"));
    }

    @Test
    @DisplayName("POST /api/surveys - 설문을 생성한다")
    void createSurvey() throws Exception {
        // given
        String requestJson = """
                {
                    "title": "신규 설문",
                    "description": "테스트 설문입니다",
                    "startDate": "2026-04-01",
                    "endDate": "2026-04-30",
                    "questions": [
                        {
                            "content": "좋아하는 언어는?",
                            "type": "SINGLE_CHOICE",
                            "orderIndex": 1,
                            "required": true,
                            "options": [
                                {"content": "Java", "orderIndex": 1},
                                {"content": "Python", "orderIndex": 2}
                            ]
                        }
                    ]
                }
                """;

        SurveyResponse response = new SurveyResponse(
                1L, "신규 설문", null,
                LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30),
                SurveyStatus.DRAFT, null,
                Collections.emptyList()
        );

        given(surveyService.createSurvey(any())).willReturn(response);

        // when & then
        mockMvc.perform(post("/api/surveys")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andDo(print())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("신규 설문"))
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    @DisplayName("POST /api/surveys - 제목 없이 설문 생성 시 400 에러를 반환한다")
    void createSurvey_ValidationFail() throws Exception {
        // given
        String invalidJson = """
                {
                    "description": "제목이 없는 설문",
                    "startDate": "2026-04-01",
                    "endDate": "2026-04-30",
                    "questions": [
                        {
                            "content": "질문",
                            "type": "TEXT",
                            "orderIndex": 1,
                            "required": false
                        }
                    ]
                }
                """;

        // when & then
        mockMvc.perform(post("/api/surveys")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andDo(print())
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /api/surveys/{id} - 존재하지 않는 설문 조회 시 400 에러를 반환한다")
    void getSurvey_NotFound() throws Exception {
        // given
        given(surveyService.getSurvey(999L))
                .willThrow(new IllegalArgumentException("설문을 찾을 수 없습니다. ID: 999"));

        // when & then
        mockMvc.perform(get("/api/surveys/999"))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("설문을 찾을 수 없습니다. ID: 999"));
    }
}
