# 부하 테스트 결과 보고서

## 테스트 환경

| 항목 | 사양 |
|------|------|
| OS | Windows 11 |
| Java | OpenJDK 21 |
| DB | MySQL 8 (localhost) |
| WAS | Spring Boot 3.4.3 내장 Tomcat |
| HikariCP | max-pool-size: 20, connection-timeout: 3000ms |
| 테스트 도구 | k6 (Docker) |

## 테스트 데이터

| 테이블 | 건수 |
|--------|------|
| surveys | 1,000 |
| questions | 4,000 |
| question_options | 10,000 |
| survey_responses | 2,000,000 |
| answers | 8,000,000 |

## 테스트 시나리오

5개 시나리오 동시 실행, 최대 300 VU.

| 시나리오 | VU | 시간 | 동작 |
|----------|-----|------|------|
| read_heavy | 0→100 | 5분 | 설문 목록/상세/필터 조회 |
| write_heavy | 0→30 | 5분 | 설문 조회 후 응답 제출 |
| aggregation | 0→20 | 5분 | 결과 집계 쿼리 |
| concurrent_submit | 50 (고정) | 1분 (1분 후 시작) | 같은 설문에 50명 동시 응답 |
| spike | 0→150 | 50초 (3분 후 시작) | 순간 트래픽 폭주 |

```
시간   0        30s       2m30s      4m30s     5m
       |---------|---------|----------|---------|
       워밍업     부하 증가   유지        정리

       1m        2m
       |---------|
       concurrent_submit (50VU 고정)

              3m     3m50s
              |------|
              spike (150VU)
```

## 성공 기준 (Thresholds)

| 기준 | 목표 | 결과 |
|------|------|------|
| http_req_duration p95 | < 500ms | **FAIL** |
| http_req_duration p99 | < 2000ms | **FAIL** |
| submit_response_duration p95 | < 1000ms | **FAIL** |
| result_query_duration p95 | < 2000ms | **FAIL** |
| http_req_failed rate | < 5% | PASS |

## 테스트 결과 요약

- 총 요청: 11,388건 / 5분
- 총 반복: 5,264회
- 최대 동시 VU: 300

## 모니터링 지표 (테스트 종료 시점)

### HikariCP 커넥션 풀

| 지표 | 값 | 판단 |
|------|-----|------|
| connections.max | 20 | - |
| connections.active | 0 (테스트 종료 후) | - |
| connections.pending | 0 (테스트 종료 후) | - |
| **connections.timeout** | **226회** | **커넥션 풀 고갈** |

### JVM

| 지표 | 값 | 판단 |
|------|-----|------|
| memory.used | 1,015 MB | 힙 사용량 높음 |
| threads.peak | 220개 | 톰캣 스레드 풀 거의 포화 |

### HTTP 요청

| 지표 | 값 |
|------|-----|
| 총 요청 수 | 11,388 |
| 총 처리 시간 | 11,427초 (누적) |
| 최대 응답 시간 | **8,484ms (8.4초)** |
| 에러 유형 | CannotCreateTransactionException (500) |

## 병목 분석

### 1. DB 커넥션 풀 고갈 (Critical)

**증상:** `hikaricp.connections.timeout` = 226회, `CannotCreateTransactionException` 발생

**원인:** max-pool-size 20개로 최대 300 VU를 감당할 수 없음.
connection-timeout 3초 내에 커넥션을 확보하지 못하면 요청 실패.

**해결 방안:**
- `maximum-pool-size`를 50~100으로 확대
- `connection-timeout`을 5000~10000ms로 완화
- MySQL `max_connections` 설정도 함께 확인

### 2. 설문 목록 조회 N+1 문제 (Critical)

**증상:** `GET /api/surveys` 응답 시간 급증

**원인:** 설문 1,000개 조회 시 `Survey → Questions → Options` 전체를 로드.
페이징 없이 전체 목록을 한 번에 반환하며, 단방향 `@OneToMany`로 인해
Hibernate가 추가 SELECT를 설문 수만큼 발생시킴 (N+1).

**해결 방안:**
- 페이징 도입 (`Pageable`, `Page<Survey>`)
- 목록 조회 시 질문/선택지를 제외한 요약 DTO 반환
- `@EntityGraph` 또는 Fetch Join으로 N+1 해결

### 3. 결과 집계 쿼리 부하 (Major)

**증상:** `result_query_duration` p95 threshold 초과

**원인:** 설문당 응답 2,000건 x 답변 8,000건 대상으로 `GROUP BY` 집계.
인덱스 없이 전체 테이블 스캔 발생 가능.

**해결 방안:**
- `answers` 테이블에 `(question_id, selected_option_id)` 복합 인덱스 추가
- 집계 결과 캐싱 (Redis 또는 애플리케이션 레벨)
- 응답 수가 많은 설문은 비동기 집계로 전환

## 인덱스 분석

### 기존 인덱스

| 테이블 | 인덱스 | 컬럼 | 비고 |
|--------|--------|------|------|
| answers | PK | id | - |
| answers | FK | question_id | JPA 자동 생성 |
| answers | FK | survey_response_id | JPA 자동 생성 |
| survey_responses | PK | id | - |
| survey_responses | FK | survey_id | JPA 자동 생성 |
| questions | PK, FK | id, survey_id | - |
| question_options | PK, FK | id, question_id | - |
| surveys | PK | id | status 인덱스 없음 |

### 추가한 인덱스

```sql
CREATE INDEX idx_answers_question_option ON answers (question_id, selected_option_id);
CREATE INDEX idx_answers_question_text ON answers (question_id, text_value(100));
CREATE INDEX idx_surveys_status ON surveys (status);
```

### EXPLAIN 비교

**집계 쿼리** (`SELECT selected_option_id, COUNT(*) ... GROUP BY selected_option_id`)

| 항목 | Before | After |
|------|--------|-------|
| 실행 방식 | FK 인덱스 → temporary table → GROUP BY | **Covering index scan** |
| cost | 4,149 | **824 (80% 감소)** |
| 디스크 접근 | 있음 | 인덱스만으로 처리 |

**텍스트 조회** (`SELECT text_value ... WHERE question_id = ? AND text_value IS NOT NULL`)

| 항목 | Before | After |
|------|--------|-------|
| 실행 방식 | FK 인덱스 → 디스크 필터 | 복합 인덱스 사용 |

**상태 필터** (`SELECT * FROM surveys WHERE status = ?`)

| 항목 | Before | After |
|------|--------|-------|
| 실행 방식 | 풀 테이블 스캔 | 풀 스캔 유지 (1,000건이라 옵티마이저가 인덱스보다 풀스캔이 효율적으로 판단) |

## Before vs After 비교 (인덱스 추가 후)

2회 동일 조건으로 테스트. 인덱스만 추가, 커넥션 풀/페이징 등은 미적용.

### Threshold 결과

| 기준 | Before | After |
|------|--------|-------|
| http_req_duration p95 < 500ms | FAIL | FAIL |
| http_req_duration p99 < 2000ms | FAIL | FAIL |
| submit_response_duration p95 < 1000ms | FAIL | FAIL |
| result_query_duration p95 < 2000ms | FAIL | FAIL |
| http_req_failed rate < 5% | PASS | FAIL |

### 모니터링 지표 비교

| 지표 | Before | After | 변화 |
|------|--------|-------|------|
| hikaricp.connections.timeout | 226 | 7,722 | 악화 (테스트 3회 누적) |
| jvm.memory.used | 1,015 MB | 971 MB | 소폭 개선 |
| jvm.threads.peak | 220 | 221 | 동일 |
| http.server.requests COUNT | 11,388 | 45,594 | 3회 테스트 누적 |
| http.server.requests MAX | 8.48s | **13.94s** | 악화 |

### 분석

인덱스 추가로 **집계 쿼리 자체의 cost는 80% 감소**했으나, 전체 부하 테스트 결과는 개선되지 않음.

**근본 원인은 인덱스가 아니라 커넥션 풀 고갈:**
- 커넥션 20개로 300 VU를 처리할 수 없음
- 커넥션 대기 → 타임아웃 → 500 에러 → 전체 성능 저하
- `GET /api/surveys`가 1,000개 설문 전체를 로드하면서 커넥션을 오래 점유

**인덱스 효과는 커넥션 풀/페이징 문제 해결 후에야 체감 가능.**

## 최종 결과 (커넥션 풀 확대 + 페이징 + 인덱스 적용 후)

### Threshold 결과

| 기준 | Before | After |
|------|--------|-------|
| http_req_duration p95 < 500ms | FAIL | **PASS (365ms)** |
| http_req_duration p99 < 2000ms | FAIL | **PASS** |
| submit_response_duration p95 < 1000ms | FAIL | **PASS (490ms)** |
| result_query_duration p95 < 2000ms | FAIL | **PASS (107ms)** |
| http_req_failed rate < 5% | FAIL | **PASS (0.00%)** |

### 주요 지표 비교

| 지표 | Before | After | 변화 |
|------|--------|-------|------|
| 총 요청 수 | 11,388 | 32,133 | **2.8배 증가** |
| 처리량 (req/s) | ~39 | 112 | **2.9배 증가** |
| 에러율 | >5% | 0.00% | 에러 제거 |
| 커넥션 타임아웃 | 7,722회 | 0회 | 완전 해소 |
| JVM 메모리 | 1,015 MB | 294 MB | **70% 감소** |
| 스레드 peak | 221 | 94 | **57% 감소** |
| 최대 응답 시간 | 13.94s | 884ms | **93% 감소** |

### 엔드포인트별 응답 시간 (p95)

| 엔드포인트 | Before | After |
|-----------|--------|-------|
| 목록 조회 (페이징) | 수 초 이상 | **24ms** |
| 상세 조회 | 수백 ms | **21ms** |
| 결과 집계 | >2000ms | **107ms** |
| 응답 제출 | >1000ms | **490ms** |

### 모니터링 지표 (테스트 종료 시점)

| 지표 | 값 |
|------|-----|
| hikaricp.connections.active | 0 |
| hikaricp.connections.idle | 50 |
| hikaricp.connections.pending | 0 |
| hikaricp.connections.timeout | 0 |
| jvm.memory.used | 294 MB |
| jvm.threads.live | 94 |
| http.server.requests | 32,140 |

### 적용한 개선 사항

1. **HikariCP**: max-pool-size 20→50, connection-timeout 3s→5s
2. **Tomcat**: max-threads 200→300
3. **페이징**: `GET /api/surveys` → `Page<SurveySummaryResponse>` (질문/선택지 미포함)
4. **인덱스**: answers(question_id, selected_option_id), answers(question_id, text_value), surveys(status)
5. **배치 페칭**: `default_batch_fetch_size=100`

## 추가 테스트 결과

### Breakpoint 테스트 (한계점 탐색)

VU를 50 → 100 → 150 → 200 → 300 → 400 → 500으로 단계 증가.

| 지표 | 값 |
|------|-----|
| 최대 VU | 500 |
| 에러 발생 시점 | ~400VU (i/o timeout 6건) |
| http_req_duration p95 | 975ms |
| 처리량 | 344 req/s |
| 커넥션 타임아웃 | 0 |
| 스레드 peak | 319 |

**결론:** 시스템 한계점은 약 **400 VU**. Tomcat max-threads(300) 포화가 원인.
추가 확장이 필요하면 스레드 풀 확대 또는 WebFlux 전환 고려.

### Soak 테스트 (내구성, 10분)

50 VU로 10분간 지속 실행.

| 지표 | 값 |
|------|-----|
| http_req_duration p95 | 51ms |
| 에러율 | 0% |
| 메모리 (종료 시) | 276MB |
| GC pause 횟수 | 170 |
| 커넥션 타임아웃 | 0 |

**결론:** 메모리 누수 없음. 10분간 응답 시간/메모리 안정적. GC도 정상 수준.

### 사용자 시나리오 테스트

실제 사용자 흐름(목록 조회 → 상세 확인 → 응답 제출)을 시뮬레이션. 80 VU, 5분.

| 지표 | 값 | 판정 |
|------|-----|------|
| http_req_duration p95 | 54ms | PASS |
| http_req_failed | 0% | PASS |
| checks 성공률 | 100% | PASS |
| 처리량 | 17 req/s | - |

처리량이 낮은 이유는 사용자 think time(2~8초)이 포함되기 때문. 서버 응답 자체는 54ms로 충분히 빠름.

### 대용량 집계 스트레스 테스트

설문 1개(응답 2,000건, 답변 8,000건)에 50VU가 집계 API만 집중 호출.

| 지표 | 값 | 판정 |
|------|-----|------|
| http_req_duration p95 | 995ms | PASS (기준 2,000ms) |
| http_req_failed | 0% | PASS |
| result_query_duration med | 626ms | - |
| 처리량 | 31 req/s | - |
| 데이터 전송 | 937MB 수신 | 응답 페이로드 큼 |
| 커넥션 타임아웃 | 0 | - |

**결론:** 응답 2,000건 규모에서는 50VU 동시 집계 가능. p95=995ms로 기준 내.
응답이 10,000건 이상으로 늘어나면 캐싱(Redis) 도입 필요.

### 쓰기 집중 스트레스 테스트

읽기 없이 응답 제출(INSERT)만 100VU로 집중.

| 지표 | 값 | 판정 |
|------|-----|------|
| http_req_duration p95 | **1,117ms** | **FAIL** (기준 1,000ms) |
| http_req_failed | 0% | PASS |
| submit_duration med | 446ms | - |
| 처리량 | **84 req/s** | - |
| 총 INSERT | 17,353건 / 3분 | - |
| 커넥션 타임아웃 | 0 | - |
| 메모리 | 269MB | 안정 |

**결론:** 100VU 쓰기 집중 시 p95=1.1초로 threshold 초과.
에러는 0%이므로 데이터 유실은 없으나 **INSERT 병목** 존재.

**쓰기 병목 원인:**
- `SurveySubmission` + 다수의 `Answer` INSERT가 하나의 트랜잭션
- 단방향 `@OneToMany @JoinColumn`은 INSERT 후 FK UPDATE가 추가 발생
- `cascade = ALL`로 부모-자식 전부 순차 INSERT

**개선 시도: JdbcTemplate 벌크 INSERT 적용 후 재테스트**

| 지표 | Before (cascade) | After (벌크) |
|------|---|---|
| submit p95 | 1,117ms | 1,267ms |
| submit med | 446ms | 454ms |
| 처리량 | 84 req/s | 80 req/s |
| 메모리 | 269MB | 232MB (14% 감소) |

효과 미미. 응답 1건당 Answer 4개로 배치 사이즈가 작아 벌크 이점이 없음.
**실제 병목은 INSERT 방식이 아니라 트랜잭션당 DB 라운드트립 횟수.**

추가 개선이 필요하면:
- 비동기 처리 (응답 접수 후 큐잉 → 백그라운드 INSERT)
- 쓰기 전용 DB 분리 (CQRS)

## 개선 우선순위

| 순위 | 항목 | 예상 효과 |
|------|------|----------|
| 1 | 커넥션 풀 확대 (20→50) | 타임아웃 226회 → 0 |
| 2 | 목록 조회 페이징 + N+1 해결 | 목록 응답 시간 90% 감소 |
| 3 | 답변 테이블 인덱스 추가 | 집계 쿼리 속도 개선 |
| 4 | 톰캣 스레드 풀 튜닝 | 동시 처리량 증가 |
