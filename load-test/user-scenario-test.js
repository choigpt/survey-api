import http from 'k6/http';
import { check, sleep, group } from 'k6';
import { Rate, Trend } from 'k6/metrics';
import { textSummary } from 'https://jslib.k6.io/k6-summary/0.1.0/index.js';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const SURVEY_MIN_ID = parseInt(__ENV.SURVEY_MIN_ID || '1');
const SURVEY_MAX_ID = parseInt(__ENV.SURVEY_MAX_ID || '1000');

export const options = {
  scenarios: {
    user_flow: {
      executor: 'ramping-vus',
      stages: [
        { duration: '30s', target: 30 },
        { duration: '2m', target: 80 },
        { duration: '2m', target: 80 },
        { duration: '30s', target: 0 },
      ],
    },
  },
  thresholds: {
    http_req_duration: ['p(95)<500'],
    http_req_failed: ['rate<0.01'],
    flow_total_duration: ['p(95)<20000'],
  },
};

const errorRate = new Rate('error_rate');
const flowDuration = new Trend('flow_total_duration');

const headers = { 'Content-Type': 'application/json' };

function randomInt(min, max) {
  return Math.floor(Math.random() * (max - min + 1)) + min;
}

// 실제 사용자 흐름: 목록 → 상세 → 응답 제출
export default function () {
  const flowStart = Date.now();

  // 1. 설문 목록 탐색
  let surveyId;
  group('1_목록_탐색', () => {
    const res = http.get(`${BASE_URL}/api/surveys?page=${randomInt(0, 49)}&size=20`);
    check(res, { '목록 200': (r) => r.status === 200 });
    errorRate.add(res.status !== 200);

    if (res.status === 200) {
      const page = JSON.parse(res.body);
      if (page.content && page.content.length > 0) {
        surveyId = page.content[randomInt(0, page.content.length - 1)].id;
      }
    }
  });

  sleep(randomInt(2, 5)); // 사용자 읽는 시간

  if (!surveyId) {
    surveyId = randomInt(SURVEY_MIN_ID, SURVEY_MAX_ID);
  }

  // 2. 설문 상세 확인
  let survey;
  group('2_상세_확인', () => {
    const res = http.get(`${BASE_URL}/api/surveys/${surveyId}`);
    check(res, { '상세 200': (r) => r.status === 200 });
    errorRate.add(res.status !== 200);

    if (res.status === 200) {
      survey = JSON.parse(res.body);
    }
  });

  sleep(randomInt(3, 8)); // 질문 읽는 시간

  // 3. 응답 제출
  if (survey && survey.questions) {
    group('3_응답_제출', () => {
      const answers = survey.questions.map((q) => {
        if (q.type === 'SINGLE_CHOICE' && q.options.length > 0)
          return { questionId: q.id, selectedOptionIds: [q.options[randomInt(0, q.options.length - 1)].id] };
        if (q.type === 'MULTI_CHOICE' && q.options.length > 0) {
          const count = randomInt(1, Math.min(3, q.options.length));
          return { questionId: q.id, selectedOptionIds: q.options.slice(0, count).map((o) => o.id) };
        }
        if (q.type === 'RATING')
          return { questionId: q.id, textValue: String(randomInt(1, 5)) };
        return { questionId: q.id, textValue: '사용자 시나리오 테스트 응답입니다.' };
      });

      const res = http.post(`${BASE_URL}/api/surveys/${surveyId}/responses`,
        JSON.stringify({ respondent: `user_${__VU}_${__ITER}`, answers }), { headers });
      check(res, { '응답 201': (r) => r.status === 201 });
      errorRate.add(res.status !== 201);
    });
  }

  flowDuration.add(Date.now() - flowStart);
  sleep(randomInt(1, 3)); // 다음 행동 전 대기
}

export function handleSummary(data) {
  return { stdout: textSummary(data, { indent: '  ', enableColors: false }) };
}
