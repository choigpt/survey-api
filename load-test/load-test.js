import http from 'k6/http';
import { check, sleep, group } from 'k6';
import { Rate, Trend } from 'k6/metrics';

// ========== 설정 ==========

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

// seed-data.js 실행 후 생성된 설문 ID로 교체
const SURVEY_IDS = [1, 2, 3];

export const options = {
  scenarios: {
    // 시나리오 1: 설문 조회 (읽기 위주)
    read_surveys: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '30s', target: 20 },  // 워밍업
        { duration: '1m', target: 50 },   // 부하 증가
        { duration: '2m', target: 50 },   // 유지
        { duration: '30s', target: 0 },   // 정리
      ],
      exec: 'readScenario',
    },
    // 시나리오 2: 응답 제출 (쓰기)
    submit_responses: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '30s', target: 5 },
        { duration: '1m', target: 15 },
        { duration: '2m', target: 15 },
        { duration: '30s', target: 0 },
      ],
      exec: 'writeScenario',
    },
    // 시나리오 3: 결과 집계 조회
    read_results: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '30s', target: 3 },
        { duration: '1m', target: 10 },
        { duration: '2m', target: 10 },
        { duration: '30s', target: 0 },
      ],
      exec: 'resultScenario',
    },
  },
  thresholds: {
    http_req_duration: ['p(95)<500', 'p(99)<1000'],
    http_req_failed: ['rate<0.01'],
  },
};

// ========== 커스텀 메트릭 ==========

const listDuration = new Trend('survey_list_duration');
const detailDuration = new Trend('survey_detail_duration');
const submitDuration = new Trend('submit_response_duration');
const resultDuration = new Trend('result_query_duration');
const errorRate = new Rate('error_rate');

// ========== 유틸 ==========

const headers = { 'Content-Type': 'application/json' };

function randomItem(arr) {
  return arr[Math.floor(Math.random() * arr.length)];
}

function randomSurveyId() {
  return randomItem(SURVEY_IDS);
}

function randomInt(min, max) {
  return Math.floor(Math.random() * (max - min + 1)) + min;
}

// ========== 시나리오 1: 읽기 ==========

export function readScenario() {
  group('설문 목록 조회', () => {
    const res = http.get(`${BASE_URL}/api/surveys`);
    listDuration.add(res.timings.duration);
    check(res, { '목록 조회 200': (r) => r.status === 200 });
    errorRate.add(res.status !== 200);
  });

  sleep(randomInt(1, 3));

  group('설문 상세 조회', () => {
    const id = randomSurveyId();
    const res = http.get(`${BASE_URL}/api/surveys/${id}`);
    detailDuration.add(res.timings.duration);
    check(res, { '상세 조회 200': (r) => r.status === 200 });
    errorRate.add(res.status !== 200);
  });

  sleep(randomInt(1, 3));

  group('상태별 필터 조회', () => {
    const status = randomItem(['ACTIVE', 'DRAFT', 'CLOSED']);
    const res = http.get(`${BASE_URL}/api/surveys?status=${status}`);
    check(res, { '필터 조회 200': (r) => r.status === 200 });
    errorRate.add(res.status !== 200);
  });

  sleep(randomInt(1, 2));
}

// ========== 시나리오 2: 응답 제출 ==========

export function writeScenario() {
  const surveyId = randomSurveyId();

  // 먼저 설문 상세를 조회하여 질문 ID와 옵션 ID를 가져옴
  const surveyRes = http.get(`${BASE_URL}/api/surveys/${surveyId}`);
  if (surveyRes.status !== 200) {
    errorRate.add(true);
    return;
  }

  const survey = JSON.parse(surveyRes.body);
  const answers = [];

  for (const question of survey.questions) {
    switch (question.type) {
      case 'SINGLE_CHOICE':
        if (question.options && question.options.length > 0) {
          answers.push({
            questionId: question.id,
            selectedOptionIds: [randomItem(question.options).id],
          });
        }
        break;
      case 'MULTI_CHOICE':
        if (question.options && question.options.length > 0) {
          const count = randomInt(1, Math.min(3, question.options.length));
          const shuffled = question.options.sort(() => 0.5 - Math.random());
          answers.push({
            questionId: question.id,
            selectedOptionIds: shuffled.slice(0, count).map((o) => o.id),
          });
        }
        break;
      case 'RATING':
        answers.push({
          questionId: question.id,
          textValue: String(randomInt(1, 5)),
        });
        break;
      case 'TEXT':
        answers.push({
          questionId: question.id,
          textValue: randomItem([
            '매우 좋습니다',
            '개선이 필요합니다',
            '보통입니다',
            '만족합니다',
            '불만족합니다',
            '특별한 의견 없음',
          ]),
        });
        break;
    }
  }

  group('응답 제출', () => {
    const payload = JSON.stringify({
      respondent: `user_${__VU}_${__ITER}`,
      answers: answers,
    });

    const res = http.post(`${BASE_URL}/api/surveys/${surveyId}/responses`, payload, { headers });
    submitDuration.add(res.timings.duration);
    check(res, { '응답 제출 201': (r) => r.status === 201 });
    errorRate.add(res.status !== 201);
  });

  sleep(randomInt(2, 5));
}

// ========== 시나리오 3: 결과 집계 ==========

export function resultScenario() {
  group('결과 집계 조회', () => {
    const id = randomSurveyId();
    const res = http.get(`${BASE_URL}/api/surveys/${id}/results`);
    resultDuration.add(res.timings.duration);
    check(res, { '결과 조회 200': (r) => r.status === 200 });
    errorRate.add(res.status !== 200);
  });

  sleep(randomInt(3, 6));
}
