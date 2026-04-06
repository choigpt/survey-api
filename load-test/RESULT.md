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

## 개선 우선순위

| 순위 | 항목 | 예상 효과 |
|------|------|----------|
| 1 | 커넥션 풀 확대 (20→50) | 타임아웃 226회 → 0 |
| 2 | 목록 조회 페이징 + N+1 해결 | 목록 응답 시간 90% 감소 |
| 3 | 답변 테이블 인덱스 추가 | 집계 쿼리 속도 개선 |
| 4 | 톰캣 스레드 풀 튜닝 | 동시 처리량 증가 |
