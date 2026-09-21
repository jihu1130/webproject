// 시나리오 7 - 관리자 대시보드 탭 전체 순회(신규, 2026-09-21 5그룹 탭 재편 이후).
// 게시글/댓글/신고 등 관리자 목록 화면 다수가 global.util.PageUtils로 "전체 조회 후 메모리에서
// 페이지 자르기" 패턴을 쓴다(CLAUDE.md "PageUtils" 참고) - 데이터가 늘어날수록 단순 조회 API보다
// 느려질 여지가 있는 지점이다. 이번에 admin/fragments/nav.html을 5그룹(개요/콘텐츠 관리/
// 신고·모더레이션/계정·권한/로그·운영)으로 재편하면서 부하테스트 탭도 새로 노출시켰으니, 재편된
// 탭 순서 그대로 한 세션 안에서 전부 연속 조회했을 때 성능을 확인한다. 여러 관리자가 동시에
// 로그인해 탭을 훑어보는 상황(예: 신고가 몰린 시점에 다 같이 대시보드부터 확인하는 경우)을 재현.
//
// **절대 운영 서버(webschool.kro.kr)를 BASE_URL로 넘기지 말 것 - 로컬/스테이징 전용.**
// 총관리자 계정(기본 admin/admin!, TestDataSeeder+SuperAdminSeeder로 생성)으로 로그인한다 -
// 대시보드/부하테스트 결과/에러 로그/문의 관리 4개 화면이 총관리자 전용(AdminAccessInterceptor
// 참고)이라 부관리자(subadmin) 계정으로는 탭 전체 순회가 불가능하다. VU마다 같은 계정으로
// 로그인하는 건 여러 사람이 하나의 공용 관리자 계정을 쓰는 소규모 운영 상황을 흉내낸 것 - k6는
// VU마다 독립된 쿠키 저장소를 주므로 서로 다른 세션/JWT를 갖는다(login.js 참고).
//
// 실행: k6 run loadtest/admin-dashboard-tour.js
//   -e BASE_URL=http://localhost:8888 (기본값, 생략 가능)
//   -e ADMIN_USERNAME=admin -e ADMIN_PASSWORD=admin! (기본값, 생략 가능)
//   -e VUS=5 (동시 관리자 세션 수, 기본 5 - 실제 운영진 규모를 감안해 다른 시나리오보다 작게 잡음)
//   -e TOURS=5 (VU당 "탭 전체 순회" 반복 횟수, 기본 5회)
import http from 'k6/http';
import { check, group, sleep } from 'k6';
import { login } from './lib/login.js';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8888';
const ADMIN_USERNAME = __ENV.ADMIN_USERNAME || 'admin';
const ADMIN_PASSWORD = __ENV.ADMIN_PASSWORD || 'admin!';
const VUS = __ENV.VUS ? parseInt(__ENV.VUS, 10) : 5;
const TOURS = __ENV.TOURS ? parseInt(__ENV.TOURS, 10) : 5;

// admin/fragments/nav.html의 2026-09-21 재편 순서 그대로 - 그룹 순서: 개요 → 콘텐츠 관리 →
// 신고·모더레이션 → 계정·권한 → 로그·운영. 그룹이 바뀌면 이 배열도 같이 맞춰줄 것.
const TABS = [
    ['개요-대시보드', '/admin/dashboard'],
    ['개요-부하테스트결과', '/admin/loadtest'],
    ['콘텐츠관리-게시글', '/admin/posts'],
    ['콘텐츠관리-댓글', '/admin/comments'],
    ['콘텐츠관리-한마디', '/admin/schedule-comments'],
    ['콘텐츠관리-공지사항', '/admin/notices'],
    ['모더레이션-신고', '/admin/reports'],
    ['계정권한-계정', '/admin/users'],
    ['계정권한-관리자권한', '/admin/users/admins'],
    ['로그운영-감사로그', '/admin/audit-log'],
    ['로그운영-보안로그', '/admin/security-log'],
    ['로그운영-에러로그', '/admin/error-log'],
    ['로그운영-문의관리', '/admin/bug-reports'],
    ['로그운영-상점관리', '/admin/shop-items'],
];

export const options = {
    scenarios: {
        admin_dashboard_tour: {
            executor: 'per-vu-iterations',
            vus: VUS,
            iterations: TOURS,
            maxDuration: '10m',
        },
    },
    thresholds: {
        // 목록 화면 다수가 메모리 필터링(PageUtils)이라 알림 폴링(200ms)/랭킹(500ms)보다
        // 넉넉하게 잡음 - 그래도 800ms를 넘기면 "관리자가 탭 하나 누를 때마다 기다린다"는
        // 체감이 생기는 수준이라 그 밑으로 유지되는지가 목표.
        http_req_duration: ['p(95)<800'],
        http_req_failed: ['rate<0.01'],
    },
};

// k6는 VU마다 별도의 JS 실행 컨텍스트를 주므로, 모듈 스코프 변수는 같은 VU의 반복(iteration)
// 사이에서 그대로 유지된다 - 매 tour마다 다시 로그인하지 않고 최초 1회만 로그인한다(실제 관리자도
// 탭을 넘길 때마다 재로그인하지 않음, notification-polling.js처럼 iterations:1이 아니라 여러 번
// 반복하는 시나리오라 이 플래그가 필요).
let sessionReady = false;

export default function () {
    if (!sessionReady) {
        login(BASE_URL, ADMIN_USERNAME, ADMIN_PASSWORD);
        sessionReady = true;
    }

    for (const [label, path] of TABS) {
        group(label, () => {
            const res = http.get(`${BASE_URL}${path}`);
            check(res, {
                '200 응답': (r) => r.status === 200,
            });
        });
        sleep(1); // 실제 관리자가 화면을 한 번 훑어보는 최소 간격 흉내
    }
}
