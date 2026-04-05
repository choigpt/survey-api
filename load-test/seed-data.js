import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

export const options = {
  vus: 1,
  iterations: 1,
};

export default function () {
  const surveys = [
    {
      title: '개발자 만족도 조사',
      description: '개발 환경에 대한 만족도를 조사합니다.',
      startDate: '2026-04-01',
      endDate: '2026-12-31',
      questions: [
        {
          content: '사용하는 주 프로그래밍 언어는?',
          type: 'SINGLE_CHOICE',
          orderIndex: 1,
          required: true,
          options: [
            { content: 'Java', orderIndex: 1 },
            { content: 'Python', orderIndex: 2 },
            { content: 'JavaScript', orderIndex: 3 },
            { content: 'Go', orderIndex: 4 },
          ],
        },
        {
          content: '사용 중인 IDE를 모두 선택해주세요',
          type: 'MULTI_CHOICE',
          orderIndex: 2,
          required: true,
          options: [
            { content: 'IntelliJ IDEA', orderIndex: 1 },
            { content: 'VS Code', orderIndex: 2 },
            { content: 'Eclipse', orderIndex: 3 },
            { content: 'Vim/Neovim', orderIndex: 4 },
          ],
        },
        {
          content: '현재 개발 환경에 대한 만족도는?',
          type: 'RATING',
          orderIndex: 3,
          required: true,
        },
        {
          content: '개선 사항이 있다면 자유롭게 작성해주세요',
          type: 'TEXT',
          orderIndex: 4,
          required: false,
        },
      ],
    },
    {
      title: '점심 메뉴 선호도 조사',
      description: '구내식당 메뉴 개선을 위한 조사입니다.',
      startDate: '2026-04-01',
      endDate: '2026-12-31',
      questions: [
        {
          content: '선호하는 점심 유형은?',
          type: 'SINGLE_CHOICE',
          orderIndex: 1,
          required: true,
          options: [
            { content: '한식', orderIndex: 1 },
            { content: '중식', orderIndex: 2 },
            { content: '일식', orderIndex: 3 },
            { content: '양식', orderIndex: 4 },
          ],
        },
        {
          content: '식사 만족도를 평가해주세요',
          type: 'RATING',
          orderIndex: 2,
          required: true,
        },
      ],
    },
    {
      title: '사내 교육 수요 조사',
      description: '올해 교육 프로그램 계획을 위한 수요 조사입니다.',
      startDate: '2026-04-01',
      endDate: '2026-12-31',
      questions: [
        {
          content: '관심 있는 교육 분야를 모두 선택해주세요',
          type: 'MULTI_CHOICE',
          orderIndex: 1,
          required: true,
          options: [
            { content: 'Cloud/DevOps', orderIndex: 1 },
            { content: 'AI/ML', orderIndex: 2 },
            { content: '보안', orderIndex: 3 },
            { content: '리더십', orderIndex: 4 },
            { content: '커뮤니케이션', orderIndex: 5 },
          ],
        },
        {
          content: '희망하는 교육 형태는?',
          type: 'SINGLE_CHOICE',
          orderIndex: 2,
          required: true,
          options: [
            { content: '온라인 강의', orderIndex: 1 },
            { content: '오프라인 세미나', orderIndex: 2 },
            { content: '워크숍', orderIndex: 3 },
          ],
        },
        {
          content: '기타 요청사항',
          type: 'TEXT',
          orderIndex: 3,
          required: false,
        },
      ],
    },
  ];

  const headers = { 'Content-Type': 'application/json' };
  const createdIds = [];

  // 설문 생성
  for (const survey of surveys) {
    const res = http.post(`${BASE_URL}/api/surveys`, JSON.stringify(survey), { headers });
    check(res, { '설문 생성 201': (r) => r.status === 201 });

    if (res.status === 201) {
      const id = JSON.parse(res.body).id;
      createdIds.push(id);
      console.log(`설문 생성 완료: id=${id}, title=${survey.title}`);
    }
    sleep(0.2);
  }

  // 설문 활성화
  for (const id of createdIds) {
    const res = http.patch(`${BASE_URL}/api/surveys/${id}/status?status=ACTIVE`, null, { headers });
    check(res, { '상태 변경 200': (r) => r.status === 200 });
    console.log(`설문 활성화: id=${id}`);
    sleep(0.1);
  }

  console.log(`\n시드 데이터 완료. 생성된 설문 ID: [${createdIds.join(', ')}]`);
  console.log('load-test.js의 SURVEY_IDS를 위 ID로 업데이트하세요.');
}
