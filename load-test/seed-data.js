import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const SURVEY_COUNT = parseInt(__ENV.SURVEY_COUNT || '100');

export const options = {
  vus: 1,
  iterations: 1,
};

function buildSurvey(index) {
  return {
    title: `부하테스트 설문 #${index}`,
    description: `부하 테스트를 위해 자동 생성된 설문입니다. (${index}번)`,
    startDate: '2026-01-01',
    endDate: '2026-12-31',
    questions: [
      {
        content: `[${index}] 단일 선택 질문`,
        type: 'SINGLE_CHOICE',
        orderIndex: 1,
        required: true,
        options: Array.from({ length: 5 }, (_, i) => ({
          content: `선택지 ${i + 1}`,
          orderIndex: i + 1,
        })),
      },
      {
        content: `[${index}] 복수 선택 질문`,
        type: 'MULTI_CHOICE',
        orderIndex: 2,
        required: true,
        options: Array.from({ length: 6 }, (_, i) => ({
          content: `항목 ${i + 1}`,
          orderIndex: i + 1,
        })),
      },
      {
        content: `[${index}] 별점 평가`,
        type: 'RATING',
        orderIndex: 3,
        required: true,
      },
      {
        content: `[${index}] 자유 서술`,
        type: 'TEXT',
        orderIndex: 4,
        required: false,
      },
    ],
  };
}

export default function () {
  const headers = { 'Content-Type': 'application/json' };
  const createdIds = [];

  // 설문 생성
  for (let i = 1; i <= SURVEY_COUNT; i++) {
    const res = http.post(`${BASE_URL}/api/surveys`, JSON.stringify(buildSurvey(i)), { headers });
    if (res.status === 201) {
      createdIds.push(JSON.parse(res.body).id);
    }
    if (i % 10 === 0) console.log(`설문 생성 진행: ${i}/${SURVEY_COUNT}`);
  }

  // 활성화
  for (const id of createdIds) {
    http.patch(`${BASE_URL}/api/surveys/${id}/status?status=ACTIVE`, null, { headers });
  }

  const RESPONSES_PER_SURVEY = parseInt(__ENV.RESPONSES_PER_SURVEY || '200');
  console.log(`\n초기 응답 데이터 삽입 시작 (설문당 ${RESPONSES_PER_SURVEY}개)...`);
  let totalResponses = 0;

  for (const surveyId of createdIds) {
    const surveyRes = http.get(`${BASE_URL}/api/surveys/${surveyId}`);
    if (surveyRes.status !== 200) continue;

    const survey = JSON.parse(surveyRes.body);

    for (let r = 0; r < RESPONSES_PER_SURVEY; r++) {
      const answers = survey.questions.map((q) => {
        switch (q.type) {
          case 'SINGLE_CHOICE':
            return { questionId: q.id, selectedOptionIds: [q.options[Math.floor(Math.random() * q.options.length)].id] };
          case 'MULTI_CHOICE': {
            const count = Math.floor(Math.random() * 3) + 1;
            const shuffled = q.options.sort(() => 0.5 - Math.random());
            return { questionId: q.id, selectedOptionIds: shuffled.slice(0, count).map((o) => o.id) };
          }
          case 'RATING':
            return { questionId: q.id, textValue: String(Math.floor(Math.random() * 5) + 1) };
          case 'TEXT':
            return { questionId: q.id, textValue: `테스트 응답 ${r + 1}` };
        }
      });

      http.post(`${BASE_URL}/api/surveys/${surveyId}/responses`,
        JSON.stringify({ respondent: `seed_user_${r}`, answers }), { headers });
      totalResponses++;
    }

    if (createdIds.indexOf(surveyId) % 5 === 4) {
      console.log(`응답 삽입 진행: ${createdIds.indexOf(surveyId) + 1}/${createdIds.length} 설문 완료`);
    }
  }

  console.log(`\n시드 데이터 완료:`);
  console.log(`  설문: ${createdIds.length}개`);
  console.log(`  응답: ${totalResponses}개`);
  console.log(`  설문 ID 범위: ${createdIds[0]} ~ ${createdIds[createdIds.length - 1]}`);
}
