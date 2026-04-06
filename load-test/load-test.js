import http from 'k6/http';
import { check, sleep, group } from 'k6';
import { Rate, Trend, Counter } from 'k6/metrics';
import { textSummary } from 'https://jslib.k6.io/k6-summary/0.1.0/index.js';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const SURVEY_MIN_ID = parseInt(__ENV.SURVEY_MIN_ID || '1');
const SURVEY_MAX_ID = parseInt(__ENV.SURVEY_MAX_ID || '100');

export const options = {
  scenarios: {
    // 읽기 부하
    read_heavy: {
      executor: 'ramping-vus',
      stages: [
        { duration: '30s', target: 50 },
        { duration: '2m', target: 100 },
        { duration: '2m', target: 100 },
        { duration: '30s', target: 0 },
      ],
      exec: 'readScenario',
    },
    // 쓰기 부하
    write_heavy: {
      executor: 'ramping-vus',
      stages: [
        { duration: '30s', target: 10 },
        { duration: '2m', target: 30 },
        { duration: '2m', target: 30 },
        { duration: '30s', target: 0 },
      ],
      exec: 'writeScenario',
    },
    // 결과 집계 (무거운 쿼리)
    aggregation: {
      executor: 'ramping-vus',
      stages: [
        { duration: '30s', target: 5 },
        { duration: '2m', target: 20 },
        { duration: '2m', target: 20 },
        { duration: '30s', target: 0 },
      ],
      exec: 'resultScenario',
    },
    // 동시성 스트레스 - 같은 설문에 동시 응답
    concurrent_submit: {
      executor: 'constant-vus',
      vus: 50,
      duration: '1m',
      startTime: '1m',
      exec: 'concurrentSubmitScenario',
    },
    // 스파이크 테스트 - 순간 폭주
    spike: {
      executor: 'ramping-vus',
      startTime: '3m',
      stages: [
        { duration: '10s', target: 150 },
        { duration: '30s', target: 150 },
        { duration: '10s', target: 0 },
      ],
      exec: 'readScenario',
    },
  },
  thresholds: {
    http_req_duration: ['p(95)<500', 'p(99)<2000'],
    http_req_failed: ['rate<0.05'],
    submit_response_duration: ['p(95)<1000'],
    result_query_duration: ['p(95)<2000'],
  },
};

// 커스텀 메트릭
const listDuration = new Trend('survey_list_duration');
const detailDuration = new Trend('survey_detail_duration');
const submitDuration = new Trend('submit_response_duration');
const resultDuration = new Trend('result_query_duration');
const errorRate = new Rate('error_rate');
const dbErrors = new Counter('db_connection_errors');

const headers = { 'Content-Type': 'application/json' };

function randomInt(min, max) {
  return Math.floor(Math.random() * (max - min + 1)) + min;
}

function randomSurveyId() {
  return randomInt(SURVEY_MIN_ID, SURVEY_MAX_ID);
}

function randomItem(arr) {
  return arr[Math.floor(Math.random() * arr.length)];
}

function buildAnswers(survey) {
  return survey.questions.map((q) => {
    switch (q.type) {
      case 'SINGLE_CHOICE':
        return { questionId: q.id, selectedOptionIds: [randomItem(q.options).id] };
      case 'MULTI_CHOICE': {
        const count = randomInt(1, Math.min(3, q.options.length));
        const shuffled = q.options.sort(() => 0.5 - Math.random());
        return { questionId: q.id, selectedOptionIds: shuffled.slice(0, count).map((o) => o.id) };
      }
      case 'RATING':
        return { questionId: q.id, textValue: String(randomInt(1, 5)) };
      case 'TEXT':
        return { questionId: q.id, textValue: randomItem(['좋아요', '보통', '개선 필요', '만족', '불만족']) };
    }
  });
}

// ========== 시나리오 ==========

export function readScenario() {
  group('목록 조회 (페이징)', () => {
    const page = randomInt(0, 49);
    const res = http.get(`${BASE_URL}/api/surveys?page=${page}&size=20`);
    listDuration.add(res.timings.duration);
    errorRate.add(res.status !== 200);
    if (res.status >= 500) dbErrors.add(1);
  });

  sleep(randomInt(1, 2));

  group('상세 조회', () => {
    const res = http.get(`${BASE_URL}/api/surveys/${randomSurveyId()}`);
    detailDuration.add(res.timings.duration);
    errorRate.add(res.status !== 200);
  });

  sleep(randomInt(1, 2));

  group('상태 필터 (페이징)', () => {
    const status = randomItem(['ACTIVE', 'DRAFT', 'CLOSED']);
    const page = randomInt(0, 10);
    http.get(`${BASE_URL}/api/surveys?status=${status}&page=${page}&size=20`);
  });

  sleep(randomInt(1, 2));
}

export function writeScenario() {
  const surveyId = randomSurveyId();
  const surveyRes = http.get(`${BASE_URL}/api/surveys/${surveyId}`);
  if (surveyRes.status !== 200) { errorRate.add(true); return; }

  const survey = JSON.parse(surveyRes.body);

  group('응답 제출', () => {
    const payload = JSON.stringify({
      respondent: `load_user_${__VU}_${__ITER}`,
      answers: buildAnswers(survey),
    });
    const res = http.post(`${BASE_URL}/api/surveys/${surveyId}/responses`, payload, { headers });
    submitDuration.add(res.timings.duration);
    errorRate.add(res.status !== 201);
    if (res.status >= 500) dbErrors.add(1);
  });

  sleep(randomInt(2, 4));
}

export function resultScenario() {
  group('결과 집계', () => {
    const res = http.get(`${BASE_URL}/api/surveys/${randomSurveyId()}/results`);
    resultDuration.add(res.timings.duration);
    errorRate.add(res.status !== 200);
    if (res.status >= 500) dbErrors.add(1);
  });

  sleep(randomInt(2, 5));
}

// 동시성 시나리오: 같은 설문에 50 VU가 동시 응답
export function concurrentSubmitScenario() {
  const surveyId = SURVEY_MIN_ID; // 모두 같은 설문
  const surveyRes = http.get(`${BASE_URL}/api/surveys/${surveyId}`);
  if (surveyRes.status !== 200) { errorRate.add(true); return; }

  const survey = JSON.parse(surveyRes.body);
  const payload = JSON.stringify({
    respondent: `concurrent_${__VU}_${__ITER}`,
    answers: buildAnswers(survey),
  });

  const res = http.post(`${BASE_URL}/api/surveys/${surveyId}/responses`, payload, { headers });
  submitDuration.add(res.timings.duration);
  check(res, { '동시 응답 201': (r) => r.status === 201 });
  errorRate.add(res.status !== 201);
  if (res.status >= 500) dbErrors.add(1);

  sleep(0.5);
}

// 모니터링 지표 수집
export function handleSummary(data) {
  // Actuator 메트릭 수집
  const metrics = {};
  const endpoints = [
    'hikaricp.connections.active',
    'hikaricp.connections.idle',
    'hikaricp.connections.pending',
    'hikaricp.connections.timeout',
    'jvm.memory.used',
    'jvm.threads.live',
    'http.server.requests',
  ];

  for (const metric of endpoints) {
    try {
      const res = http.get(`${BASE_URL}/actuator/metrics/${metric}`);
      if (res.status === 200) {
        const body = JSON.parse(res.body);
        metrics[metric] = body.measurements[0].value;
      }
    } catch (e) { /* ignore */ }
  }

  console.log('\n========== 앱 모니터링 지표 (테스트 종료 시점) ==========');
  for (const [key, value] of Object.entries(metrics)) {
    console.log(`  ${key}: ${value}`);
  }

  return {
    stdout: textSummary(data, { indent: '  ', enableColors: false }),
  };
}
