// 시나리오 4 - 포인트/티어 랭킹 페이지.
// /ranking이 캐싱 없이 매 요청마다 ORDER BY points DESC 쿼리를 직접 날리는 구조라
// (UserPointService.getTopRanking(), todo.md #23 참고), 사용자 수가 늘었을 때를 가정한 동시
// 조회 성능을 확인한다. 로그인 없이도 열람 가능(SecurityConfig - GET /ranking은 permitAll).
//
// **절대 운영 서버(webschool.kro.kr)를 BASE_URL로 넘기지 말 것 - 로컬/스테이징 전용.**
//
// 실행: k6 run loadtest/ranking-page.js
//   -e BASE_URL=http://localhost:8888 (기본값, 생략 가능)
//   -e VUS=20 (동시 사용자 수, 기본 20)
//   -e DURATION=30s (부하를 유지할 시간, 기본 30초)
import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8888';
const VUS = __ENV.VUS ? parseInt(__ENV.VUS, 10) : 20;
const DURATION = __ENV.DURATION || '30s';

export const options = {
    scenarios: {
        ranking_page: {
            executor: 'constant-vus',
            vus: VUS,
            duration: DURATION,
        },
    },
    thresholds: {
        http_req_duration: ['p(95)<500'],
        http_req_failed: ['rate<0.01'],
    },
};

export default function () {
    const res = http.get(`${BASE_URL}/ranking`);
    check(res, {
        '200 응답': (r) => r.status === 200,
    });
    sleep(1);
}
