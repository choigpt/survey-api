# 부하 테스트

k6 기반 부하 테스트 스크립트.

## 사전 준비

### k6 설치

```bash
# Windows
choco install k6

# macOS
brew install k6
```

### 앱 실행

```bash
./gradlew bootRun
```

## 실행 순서

### 1. 시드 데이터 생성

```bash
k6 run load-test/seed-data.js
```

출력된 설문 ID를 확인하고, `load-test.js`의 `SURVEY_IDS`를 해당 ID로 수정합니다.

### 2. 부하 테스트 실행

```bash
# 기본 실행
k6 run load-test/load-test.js

# JSON 결과 저장
k6 run --out json=load-test/result.json load-test/load-test.js

# 다른 서버 대상
k6 run -e BASE_URL=http://your-server:8080 load-test/load-test.js
```

## 테스트 시나리오

| 시나리오 | VU (최대) | 동작 |
|----------|-----------|------|
| read_surveys | 50 | 목록 조회, 상세 조회, 상태별 필터 |
| submit_responses | 15 | 설문 조회 후 응답 제출 |
| read_results | 10 | 결과 집계 조회 |

## 성공 기준 (Thresholds)

- p95 응답시간 < 500ms
- p99 응답시간 < 1000ms
- 에러율 < 1%

## 단계별 부하

```
0s      30s     1m30s   3m30s   4m
|-------|-------|-------|-------|
 워밍업   증가     유지     정리
```
