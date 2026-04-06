import http from 'k6/http';
import { check, sleep, group } from 'k6';
import { Rate, Trend } from 'k6/metrics';
import { textSummary } from 'https://jslib.k6.io/k6-summary/0.1.0/index.js';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const SURVEY_MIN_ID = parseInt(__ENV.SURVEY_MIN_ID || '1');
const SURVEY_MAX_ID = parseInt(__ENV.SURVEY_MAX_ID || '1000');

export const options = {
  scenarios: {
    breakpoint: {
      executor: 'ramping-vus',
      stages: [
        { duration: '30s', target: 50 },
        { duration: '30s', target: 100 },
        { duration: '30s', target: 150 },
        { duration: '30s', target: 200 },
        { duration: '30s', target: 300 },
        { duration: '30s', target: 400 },
        { duration: '30s', target: 500 },
        { duration: '30s', target: 0 },
      ],
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.10'],
  },
};

const errorRate = new Rate('error_rate');
const listDuration = new Trend('list_duration');
const detailDuration = new Trend('detail_duration');
const submitDuration = new Trend('submit_duration');

const headers = { 'Content-Type': 'application/json' };

function randomInt(min, max) {
  return Math.floor(Math.random() * (max - min + 1)) + min;
}

function randomSurveyId() {
  return randomInt(SURVEY_MIN_ID, SURVEY_MAX_ID);
}

export default function () {
  const action = randomInt(1, 10);

  if (action <= 5) {
    // 50% 목록 조회
    const res = http.get(`${BASE_URL}/api/surveys?page=${randomInt(0, 49)}&size=20`);
    listDuration.add(res.timings.duration);
    errorRate.add(res.status !== 200);
  } else if (action <= 8) {
    // 30% 상세 조회
    const res = http.get(`${BASE_URL}/api/surveys/${randomSurveyId()}`);
    detailDuration.add(res.timings.duration);
    errorRate.add(res.status !== 200);
  } else {
    // 20% 응답 제출
    const surveyId = randomSurveyId();
    const surveyRes = http.get(`${BASE_URL}/api/surveys/${surveyId}`);
    if (surveyRes.status !== 200) { errorRate.add(true); return; }

    const survey = JSON.parse(surveyRes.body);
    const answers = survey.questions.map((q) => {
      if (q.type === 'SINGLE_CHOICE' && q.options.length > 0)
        return { questionId: q.id, selectedOptionIds: [q.options[randomInt(0, q.options.length - 1)].id] };
      if (q.type === 'MULTI_CHOICE' && q.options.length > 0)
        return { questionId: q.id, selectedOptionIds: [q.options[0].id] };
      if (q.type === 'RATING')
        return { questionId: q.id, textValue: String(randomInt(1, 5)) };
      return { questionId: q.id, textValue: '테스트' };
    });

    const res = http.post(`${BASE_URL}/api/surveys/${surveyId}/responses`,
      JSON.stringify({ respondent: `bp_${__VU}_${__ITER}`, answers }), { headers });
    submitDuration.add(res.timings.duration);
    errorRate.add(res.status !== 201);
  }

  sleep(0.5);
}

export function handleSummary(data) {
  const metrics = {};
  ['hikaricp.connections.timeout', 'jvm.memory.used', 'jvm.threads.live'].forEach((m) => {
    try {
      const res = http.get(`${BASE_URL}/actuator/metrics/${m}`);
      if (res.status === 200) metrics[m] = JSON.parse(res.body).measurements[0].value;
    } catch (e) {}
  });

  console.log('\n========== Breakpoint 모니터링 ==========');
  for (const [k, v] of Object.entries(metrics)) console.log(`  ${k}: ${v}`);

  return { stdout: textSummary(data, { indent: '  ', enableColors: false }) };
}
