# Survey API

Spring Boot 기반 설문조사 REST API.
설문 생성, 응답 제출, 결과 집계 기능을 제공합니다.

## 기술 스택

- Java 21, Spring Boot 3.4.3
- Spring Data JPA, MySQL
- Lombok, Jakarta Validation
- JUnit 5, Mockito

## 프로젝트 구조

```
com.example.surveyapi
├── controller          # REST 컨트롤러
├── service             # 비즈니스 로직
├── repository          # 데이터 접근 계층
├── entity              # JPA 엔티티
├── dto
│   ├── request         # 요청 DTO (record + validation)
│   └── response        # 응답 DTO (record + 팩토리 메서드)
└── config              # 설정 (예외 핸들러, JPA Auditing)
```

## API 명세

| Method | Endpoint | 설명 |
|--------|----------|------|
| `POST` | `/api/surveys` | 설문 생성 (질문 + 선택지 포함) |
| `GET` | `/api/surveys` | 전체 설문 목록 조회 |
| `GET` | `/api/surveys?status=ACTIVE` | 상태별 설문 필터링 |
| `GET` | `/api/surveys/{id}` | 설문 상세 조회 |
| `PATCH` | `/api/surveys/{id}/status?status=ACTIVE` | 설문 상태 변경 |
| `POST` | `/api/surveys/{id}/responses` | 설문 응답 제출 |
| `GET` | `/api/surveys/{id}/results` | 설문 결과 집계 조회 |

## 요청/응답 예시

### 설문 생성

```http
POST /api/surveys
Content-Type: application/json

{
  "title": "개발자 만족도 조사",
  "description": "개발 환경에 대한 만족도를 조사합니다.",
  "startDate": "2026-04-01",
  "endDate": "2026-04-30",
  "questions": [
    {
      "content": "사용하는 주 언어는?",
      "type": "SINGLE_CHOICE",
      "orderIndex": 1,
      "required": true,
      "options": [
        { "content": "Java", "orderIndex": 1 },
        { "content": "Python", "orderIndex": 2 }
      ]
    },
    {
      "content": "개선사항을 적어주세요",
      "type": "TEXT",
      "orderIndex": 2,
      "required": false
    }
  ]
}
```

### 응답 제출

```http
POST /api/surveys/1/responses
Content-Type: application/json

{
  "respondent": "홍길동",
  "answers": [
    { "questionId": 1, "selectedOptionIds": [1] },
    { "questionId": 2, "textValue": "IDE 지원 강화 희망" }
  ]
}
```

## 질문 유형

| 유형 | 설명 | 응답 필드 |
|------|------|----------|
| `SINGLE_CHOICE` | 단일 선택 | `selectedOptionIds` (1개) |
| `MULTI_CHOICE` | 복수 선택 | `selectedOptionIds` (N개) |
| `TEXT` | 서술형 | `textValue` |
| `RATING` | 별점 | `textValue` (숫자 문자열) |

## 설문 상태

`DRAFT` -> `ACTIVE` -> `CLOSED`

- `DRAFT`: 초안. 응답 불가.
- `ACTIVE`: 활성. 응답 가능.
- `CLOSED`: 마감. 응답 불가.

## 실행

### 사전 요구사항

- Java 21
- MySQL 8 (localhost:3306, DB: `survey_db`)

### 설정

`src/main/resources/application.yml`에서 DB 접속 정보 수정:

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/survey_db
    username: root
    password: root
```

### 빌드 & 실행

```bash
./gradlew bootRun
```

### 테스트

```bash
./gradlew test
```

## 테스트 구성

총 17개 테스트 (단위 테스트 + 슬라이스 테스트)

### SurveyServiceTest (단위 테스트, 7개)

| 테스트 | 검증 내용 |
|--------|----------|
| `createSurvey_Success` | 설문 생성 시 질문/선택지 함께 저장 |
| `getSurvey_NotFound` | 없는 설문 조회 시 예외 |
| `submitResponse_NotActive` | DRAFT 설문에 응답 시 예외 |
| `submitResponse_MissingRequiredAnswer` | 필수 질문 미답변 시 예외 |
| `updateSurveyStatus_Success` | 상태 변경 정상 동작 |
| `getAllSurveys_Success` | 전체 목록 조회 |
| `getSurveysByStatus_Success` | 상태별 필터링 조회 |

### SurveyResultServiceTest (단위 테스트, 4개)

| 테스트 | 검증 내용 |
|--------|----------|
| `getResults_ChoiceQuestion` | 선택형 질문 옵션별 카운트 집계 |
| `getResults_RatingQuestion` | 별점 평균 계산 |
| `getResults_TextQuestion` | 텍스트 응답 목록 반환 |
| `getResults_NotFound` | 없는 설문 결과 조회 시 예외 |

### SurveyControllerTest (슬라이스 테스트, 5개)

| 테스트 | 검증 내용 |
|--------|----------|
| `getAllSurveys` | `GET /api/surveys` 200 응답 + JSON 구조 |
| `getSurvey` | `GET /api/surveys/{id}` 200 응답 |
| `createSurvey` | `POST /api/surveys` 201 응답 |
| `createSurvey_ValidationFail` | 제목 누락 시 400 응답 |
| `getSurvey_NotFound` | 없는 설문 조회 시 400 + 에러 메시지 |

### SurveyApiApplicationTests (통합 테스트, 1개)

| 테스트 | 검증 내용 |
|--------|----------|
| `contextLoads` | Spring 컨텍스트 정상 로드 |

## ERD

```
surveys 1──N questions 1──N question_options
surveys 1──N survey_responses 1──N answers N──1 questions
```
