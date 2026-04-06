import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend, Counter } from 'k6/metrics';
import { textSummary } from 'https://jslib.k6.io/k6-summary/0.1.0/index.js';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const SURVEY_MIN_ID = parseInt(__ENV.SURVEY_MIN_ID || '1');
const SURVEY_MAX_ID = parseInt(__ENV.SURVEY_MAX_ID || '1000');

export const options = {
  scenarios: {
    write_only: {
      executor: 'ramping-vus',
      stages: [
        { duration: '20s', target: 20 },
        { duration: '1m', target: 50 },
        { duration: '1m', target: 100 },
        { duration: '1m', target: 100 },
        { duration: '20s', target: 0 },
      ],
    },
  },
  thresholds: {
    http_req_duration: ['p(95)<1000'],
    http_req_failed: ['rate<0.05'],
  },
};

const submitDuration = new Trend('submit_duration');
const errorRate = new Rate('error_rate');
const dbErrors = new Counter('db_errors');

const headers = { 'Content-Type': 'application/json' };

function randomInt(min, max) {
  return Math.floor(Math.random() * (max - min + 1)) + min;
}

// 설문 상세를 미리 캐시 (setup에서 로드)
const surveyCache = {};

export function setup() {
  // 10개 설문만 미리 로드
  const surveys = {};
  for (let i = 0; i < 10; i++) {
    const id = randomInt(SURVEY_MIN_ID, SURVEY_MAX_ID);
    const res = http.get(`${BASE_URL}/api/surveys/${id}`);
    if (res.status === 200) {
      surveys[id] = JSON.parse(res.body);
    }
  }
  return { surveys };
}

export default function (data) {
  const surveyIds = Object.keys(data.surveys);
  const surveyId = surveyIds[randomInt(0, surveyIds.length - 1)];
  const survey = data.surveys[surveyId];

  const answers = survey.questions.map((q) => {
    if (q.type === 'SINGLE_CHOICE' && q.options.length > 0)
      return { questionId: q.id, selectedOptionIds: [q.options[randomInt(0, q.options.length - 1)].id] };
    if (q.type === 'MULTI_CHOICE' && q.options.length > 0)
      return { questionId: q.id, selectedOptionIds: [q.options[0].id] };
    if (q.type === 'RATING')
      return { questionId: q.id, textValue: String(randomInt(1, 5)) };
    return { questionId: q.id, textValue: '쓰기 스트레스 테스트' };
  });

  const res = http.post(`${BASE_URL}/api/surveys/${surveyId}/responses`,
    JSON.stringify({ respondent: `write_${__VU}_${__ITER}`, answers }), { headers });

  submitDuration.add(res.timings.duration);
  check(res, { '응답 201': (r) => r.status === 201 });
  errorRate.add(res.status !== 201);
  if (res.status >= 500) dbErrors.add(1);

  sleep(0.3);
}

export function handleSummary(data) {
  const metrics = {};
  ['hikaricp.connections.active', 'hikaricp.connections.timeout',
   'jvm.memory.used', 'jvm.threads.live'].forEach((m) => {
    try {
      const res = http.get(`${BASE_URL}/actuator/metrics/${m}`);
      if (res.status === 200) metrics[m] = JSON.parse(res.body).measurements[0].value;
    } catch (e) {}
  });

  console.log('\n========== 쓰기 스트레스 모니터링 ==========');
  for (const [k, v] of Object.entries(metrics)) console.log(`  ${k}: ${v}`);

  return { stdout: textSummary(data, { indent: '  ', enableColors: false }) };
}
