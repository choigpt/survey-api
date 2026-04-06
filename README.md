# Survey API

Spring Boot 기반 설문조사 REST API.
설문 생성, 응답 제출, 결과 집계 기능을 제공합니다.

## 기술 스택

- Java 21, Spring Boot 3.4.3
- Spring Data JPA, MySQL, H2 (테스트)
- Lombok, Jakarta Validation
- Spring Actuator, Micrometer Prometheus
- JUnit 5, Mockito, k6 (부하 테스트)

## 프로젝트 구조

```
com.example.surveyapi
├── controller              # REST 컨트롤러
├── service                 # 비즈니스 로직
│   ├── SurveyService       # 설문 CRUD, 응답 제출
│   ├── SurveyResultService # 결과 집계 (스냅샷 우선 → 실시간 fallback)
│   └── SurveyResultScheduler # 배치 집계 (60초 주기)
├── repository              # 데이터 접근 계층
├── entity                  # JPA 엔티티
├── dto
│   ├── request             # 요청 DTO (record + validation + toEntity)
│   └── response            # 응답 DTO (record + 팩토리 메서드)
└── config                  # 예외 핸들러, JPA Auditing, 스케줄링
```

## API 명세

| Method | Endpoint | 설명 |
|--------|----------|------|
| `POST` | `/api/surveys` | 설문 생성 (질문 + 선택지 포함) |
| `GET` | `/api/surveys?page=0&size=20` | 설문 목록 페이징 조회 |
| `GET` | `/api/surveys?status=ACTIVE&page=0&size=20` | 상태별 필터링 (페이징) |
| `GET` | `/api/surveys/{id}` | 설문 상세 조회 (질문/선택지 포함) |
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

## 실행

### 사전 요구사항

- Java 21
- MySQL 8 (localhost:3306)

### 설정

```bash
mysql -u root -e "CREATE DATABASE IF NOT EXISTS survey_db"
```

`src/main/resources/application.yml`에서 DB 접속 정보 수정.

### 빌드 & 실행

```bash
./gradlew bootRun
```

### 테스트

```bash
./gradlew test
```

## 테스트 구성

### 단위 테스트

| 클래스 | 테스트 수 | 범위 |
|--------|-----------|------|
| SurveyServiceTest | 7 | 생성, 조회, 응답 제출, 상태 변경, 페이징 |
| SurveyResultServiceTest | 5 | 스냅샷 조회, 실시간 집계, 선택형/별점/텍스트 |
| SurveyControllerTest | 5 | API 엔드포인트, 유효성 검증, 에러 응답 |
| SurveyServiceConcurrencyTest | 2 | 50스레드 동시 응답, 동시 설문 생성 |
| SurveyApiApplicationTests | 1 | 컨텍스트 로드 |

### 부하 테스트 (k6)

| 스크립트 | 목적 | 최대 VU |
|----------|------|---------|
| load-test.js | 종합 부하 (읽기/쓰기/집계/동시성/스파이크) | 850 |
| breakpoint-test.js | 한계점 탐색 (VU 단계 증가) | 500 |
| soak-test.js | 내구성 (10분 지속) | 50 |
| user-scenario-test.js | 실제 사용자 흐름 (목록→상세→응답) | 80 |
| aggregation-stress-test.js | 집계 쿼리 집중 | 50 |
| write-stress-test.js | 쓰기(INSERT) 집중 | 100 |

```bash
# 시드 데이터 (설문 1,000개 x 응답 2,000개 = 200만건)
mysql -u root --default-character-set=utf8mb4 survey_db < load-test/seed-bulk.sql

# 부하 테스트 (Docker)
docker run --rm -i --add-host=host.docker.internal:host-gateway \
  -v "%cd%/load-test:/scripts" grafana/k6 \
  run -e BASE_URL=http://host.docker.internal:8080 /scripts/load-test.js
```

자세한 테스트 결과는 [load-test/RESULT.md](load-test/RESULT.md) 참고.

## 성능 최적화

### 최종 성능 (최적화 전 → 후)

| 지표 | Before | After |
|------|--------|-------|
| 처리량 | 39 req/s | **294 req/s** (7.5배) |
| 집계 p95 | >2,000ms | **23ms** (99% 감소) |
| 응답 제출 p95 | >1,000ms | **391ms** (61% 감소) |
| 에러율 | >5% | **0%** |
| 메모리 | 1,015MB | **264MB** (74% 감소) |

### 적용한 최적화

| 항목 | 내용 |
|------|------|
| 커넥션 풀 | HikariCP max-pool-size 50, connection-timeout 5s |
| 스레드 풀 | Tomcat max-threads 300 |
| 페이징 | 목록 조회 `Page<SurveySummaryResponse>` (질문/선택지 미포함) |
| 인덱스 | `answers(question_id, selected_option_id)`, `answers(question_id, text_value)`, `surveys(status)` |
| 배치 집계 | 60초 주기 스냅샷 저장 → 조회 시 JSON 1건 SELECT |
| 벌크 INSERT | JdbcTemplate batchUpdate + rewriteBatchedStatements |
| N+1 완화 | `default_batch_fetch_size: 100` |

### 모니터링

```
GET /actuator/health
GET /actuator/metrics/{metricName}
GET /actuator/prometheus
```

주요 지표: HikariCP 커넥션 풀, JVM 메모리/스레드, HTTP 요청 통계.

## ERD

```
surveys 1──N questions 1──N question_options
surveys 1──N survey_responses 1──N answers N──1 questions
surveys 1──1 survey_result_snapshots
```
