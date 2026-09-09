// 시나리오 3 - 커뮤니티 목록/검색.
// /posts 목록·검색(정렬 옵션 포함, 좋아요순 포함)에 동시 요청을 걸어 페이지네이션 쿼리 성능을
// 확인한다. 로그인 없이도 열람 가능한 화면이라(SecurityConfig - GET /posts는 permitAll) 로그인
// 단계 없이 바로 부하를 건다 - 실사용자 트래픽 중 가장 자주 열리는 화면이라 로그인 여부와
// 무관하게 항상 부하가 걸린다.
//
// **절대 운영 서버(webschool.kro.kr)를 BASE_URL로 넘기지 말 것 - 로컬/스테이징 전용.**
//
// 실행: k6 run loadtest/community-list-search.js
//   -e BASE_URL=http://localhost:8888 (기본값, 생략 가능)
//   -e VUS=20 (동시 사용자 수, 기본 20 - 로그인 불필요라 알림 폴링/NEIS 시나리오보다 크게 잡음)
//   -e DURATION=30s (부하를 유지할 시간, 기본 30초)
import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8888';
const VUS = __ENV.VUS ? parseInt(__ENV.VUS, 10) : 20;
const DURATION = __ENV.DURATION || '30s';

// 실제 화면에서 고를 수 있는 조합(post/list.html의 카테고리 탭/정렬 드롭다운과 동일) - 매 반복마다
// 무작위로 하나를 골라 요청해서 "다들 똑같은 쿼리만 반복"이 아니라 실제 트래픽처럼 섞이게 한다.
const QUERY_VARIANTS = [
    '', // 전체, 기본 정렬
    '?category=FREE',
    '?category=ANONYMOUS',
    '?category=QNA',
    '?sort=likes', // 최근 추가된 좋아요순 정렬
    '?sort=views',
    // 브라우저는 URL에 넣기 전에 항상 자동으로 퍼센트 인코딩을 해주지만, k6는 그냥 준 문자열을
    // 그대로 요청 줄에 실어버린다 - 한글을 인코딩 없이 보내면 Tomcat이 RFC 7230 위반으로 400을
    // 뱉는다(직접 겪음, 실제 사용자는 브라우저가 인코딩해주므로 겪을 일 없는 클라이언트 버그였음).
    `?keyword=${encodeURIComponent('시험')}`, // TestDataSeeder 시딩 글에 실제로 등장하는 키워드
    `?keyword=${encodeURIComponent('급식')}`,
    '?page=1',
];

export const options = {
    scenarios: {
        community_list_search: {
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
    const variant = QUERY_VARIANTS[Math.floor(Math.random() * QUERY_VARIANTS.length)];
    const res = http.get(`${BASE_URL}/posts${variant}`);
    check(res, {
        '200 응답': (r) => r.status === 200,
    });
    sleep(1); // 실제 사용자가 목록을 훑어보는 최소 간격 흉내(쉬지 않고 초당 수백 회씩 요청하면
              // 브라우저 사용 패턴이 아니라 무의미한 스트레스 테스트가 된다)
}
