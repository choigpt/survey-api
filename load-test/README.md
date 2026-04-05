# 부하 테스트

k6 기반 부하 테스트. Docker로 실행.

## 실행 순서

### 1. 앱 실행

```bash
./gradlew bootRun
```

### 2. 시드 데이터 생성

**SQL 벌크 인서트 (권장)** — 설문 1,000개 x 응답 2,000개 = 약 200만건

```bash
mysql -u root survey_db < load-test/seed-bulk.sql
```

예상 데이터량:

| 테이블 | 건수 |
|--------|------|
| surveys | 1,000 |
| questions | 4,000 |
| question_options | 10,000 |
| survey_responses | 2,000,000 |
| answers | 8,000,000 |

**k6 API 방식 (소량 테스트용)**

```bash
docker run --rm -i --add-host=host.docker.internal:host-gateway \
  -v "%cd%/load-test:/scripts" grafana/k6 \
  run -e BASE_URL=http://host.docker.internal:8080 \
  -e SURVEY_COUNT=20 -e RESPONSES_PER_SURVEY=50 /scripts/seed-data.js
```

### 3. 부하 테스트 실행

```bash
docker run --rm -i --add-host=host.docker.internal:host-gateway \
  -v "%cd%/load-test:/scripts" grafana/k6 \
  run -e BASE_URL=http://host.docker.internal:8080 /scripts/load-test.js
```

## 테스트 시나리오

| 시나리오 | VU (최대) | 시간 | 목적 |
|----------|-----------|------|------|
| read_heavy | 100 | 5분 | 읽기 부하 (목록/상세/필터) |
| write_heavy | 30 | 5분 | 쓰기 부하 (응답 제출) |
| aggregation | 20 | 5분 | 집계 쿼리 부하 |
| concurrent_submit | 50 | 1분 | 같은 설문에 50명 동시 응답 |
| spike | 150 | 50초 | 순간 트래픽 폭주 |

## 성공 기준

- 전체 p95 < 500ms, p99 < 2000ms
- 응답 제출 p95 < 1000ms
- 결과 집계 p95 < 2000ms
- 에러율 < 5%

## 모니터링 지표

테스트 종료 시 Actuator에서 자동 수집:

| 지표 | 설명 | 병목 판단 |
|------|------|----------|
| `hikaricp.connections.active` | 활성 DB 커넥션 수 | pool-size에 근접하면 DB 커넥션 부족 |
| `hikaricp.connections.pending` | 대기 중인 커넥션 요청 | 0보다 크면 커넥션 풀 병목 |
| `hikaricp.connections.timeout` | 타임아웃 횟수 | 1 이상이면 커넥션 풀 확대 필요 |
| `jvm.memory.used` | JVM 메모리 사용량 | 힙 한도에 근접하면 GC 병목 |
| `jvm.threads.live` | 활성 스레드 수 | 톰캣 스레드 풀 포화 확인 |

### 수동 확인

```bash
# 전체 메트릭
curl http://localhost:8080/actuator/metrics

# HikariCP 커넥션 풀
curl http://localhost:8080/actuator/metrics/hikaricp.connections.active

# Prometheus 형식 (Grafana 연동용)
curl http://localhost:8080/actuator/prometheus
```
