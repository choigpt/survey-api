import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';
import { textSummary } from 'https://jslib.k6.io/k6-summary/0.1.0/index.js';

// 응답이 가장 많은 설문 ID 1번을 집중 공격
// seed-bulk.sql 기준 설문당 2,000응답 x 4질문 = 8,000 answers
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const TARGET_SURVEY_ID = parseInt(__ENV.TARGET_SURVEY_ID || '1');

export const options = {
  scenarios: {
    // 집계만 집중 호출
    aggregation_only: {
      executor: 'ramping-vus',
      stages: [
        { duration: '20s', target: 10 },
        { duration: '1m', target: 30 },
        { duration: '1m', target: 50 },
        { duration: '1m', target: 50 },
        { duration: '20s', target: 0 },
      ],
    },
  },
  thresholds: {
    http_req_duration: ['p(95)<2000', 'p(99)<5000'],
    http_req_failed: ['rate<0.01'],
  },
};

const resultDuration = new Trend('result_query_duration');
const errorRate = new Rate('error_rate');

export default function () {
  const res = http.get(`${BASE_URL}/api/surveys/${TARGET_SURVEY_ID}/results`);
  resultDuration.add(res.timings.duration);
  check(res, { '집계 200': (r) => r.status === 200 });
  errorRate.add(res.status !== 200);

  sleep(0.5);
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

  console.log('\n========== 집계 스트레스 모니터링 ==========');
  for (const [k, v] of Object.entries(metrics)) console.log(`  ${k}: ${v}`);

  return { stdout: textSummary(data, { indent: '  ', enableColors: false }) };
}
