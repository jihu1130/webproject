// 시나리오 9 - 신고 큐 대량 처리(선택 블라인드/블라인드 해제) 동시성.
// report-list.html의 "선택 블라인드/선택 삭제" 일괄 처리(admin-bulk.js가 즉석에서 <form>을 만들어
// POST /admin/posts/bulk-blind 등으로 제출 - AdminPostController/AdminCommentController/
// AdminScheduleCommentController에 동일한 패턴이 3벌 반복 구현돼 있다, CLAUDE.md "신고→자동
// 블라인드 패턴" 참고)는 지금까지 관리자 한 명이 순차로 쓰는 상황만 확인됐다. 실제로는 신고가
// 몰리는 시점에 여러 관리자가 같은 신고 큐 화면을 보고 있다가 같은 항목을 동시에 처리(블라인드나
// 그 반대인 블라인드 해제)하려 들 수 있다 - setup()에서 실제 신고 대기열의 id를 읽어와, 여러 VU가
// 같은 id 집합에 정반대 액션을 동시에 반복해서 걸어 500 에러/예외가 나는지 확인한다.
//
// **파괴적인 bulk-delete는 일부러 안 쓴다** - TestDataSeeder가 시딩한 게시글/댓글/한마디를 실제로
// 지우면, 이 시더는 이미 존재하는 데이터를 건너뛰는 멱등 방식이라 재실행해도 복구가 안 될 수
// 있다(로컬 데모 데이터 영구 손실 위험). 대신 blind/unblind만 반복하고, teardown()에서 setup() 때
// 읽어둔 "행별 원래 블라인드 여부"로 정확히 되돌린다.
// **직접 겪은 실수(2026-09-21)**: 처음엔 teardown()에서 무조건 bulk-blind만 다시 걸었는데,
// 신고 큐에는 이미 블라인드된 항목(자동 블라인드)과 아직 신고 1건이라 블라인드 안 된 "정상" 항목이
// 섞여 있다(TestDataSeeder 참고) - 무조건 블라인드로 되돌리면 원래 "정상"이던 항목까지 블라인드로
// 바뀌어버려서 로컬 데모 데이터가 실제로 오염됐다(체육대회 게시글이 의도치 않게 블라인드됨,
// 브라우저로 직접 확인 후 수동으로 되돌림). 그래서 setup()에서 행별 원래 상태(blindIds/normalIds)를
// 나눠 기록해두고, teardown()은 그 기록대로만 복원한다 - "신고 큐 = 전부 블라인드"라고 가정하지
// 말 것.
// **주의: 이 스크립트는 조회 전용이 아니라 실제로 데이터를 수정한다** - 매 blind/unblind 호출마다
// 알림 1건 + 감사 로그(AdminActionLog) 1건이 실제로 쌓인다(AdminPostService.setBlind() 등 참고),
// 반복 실행하면 그만큼 로컬 DB에 로그성 데이터가 누적되는 건 의도된 부작용.
//
// **절대 운영 서버(webschool.kro.kr)를 BASE_URL로 넘기지 말 것 - 로컬/스테이징 전용.**
// 신고 관리·게시글/댓글/한마디 관리 권한이 모두 있는 계정이 필요해 총관리자(admin/admin!)로
// 로그인한다.
//
// 실행: k6 run loadtest/report-queue-bulk-contention.js
//   -e BASE_URL=http://localhost:8888 (기본값, 생략 가능)
//   -e ADMIN_USERNAME=admin -e ADMIN_PASSWORD=admin! (기본값, 생략 가능)
//   -e VUS=6 (동시 관리자 세션 수, 기본 6 - 짝수 VU는 블라인드만, 홀수 VU는 블라인드 해제만
//             계속 걸어서 절반씩 정반대 액션이 부딪히게 함)
//   -e DURATION=15s (부하를 유지할 시간, 기본 15초 - 매 반복마다 알림/로그가 실제로 쌓이므로
//                    너무 길게 잡지 않음)
import http from 'k6/http';
import { check, group, sleep } from 'k6';
import { login } from './lib/login.js';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8888';
const ADMIN_USERNAME = __ENV.ADMIN_USERNAME || 'admin';
const ADMIN_PASSWORD = __ENV.ADMIN_PASSWORD || 'admin!';
const VUS = __ENV.VUS ? parseInt(__ENV.VUS, 10) : 6;
const DURATION = __ENV.DURATION || '15s';

// report-list.html의 3개 탭(게시물/댓글/오늘의 한마디)과 동일한 구조 - 각 탭은
// data-bulk-table로 표시된 <table> 하나를 갖고, 그 안 각 행은 1번째 td=체크박스(admin-bulk-check,
// value=id), 2번째 td=상태 배지(문제없음/블라인드/신고누적/정상) 순으로 고정돼 있다.
const TARGETS = [
    {
        label: '게시글',
        table: 'table[data-bulk-table="bulkToolbar-reportPosts"]',
        blindPath: '/admin/posts/bulk-blind',
        unblindPath: '/admin/posts/bulk-unblind',
    },
    {
        label: '댓글',
        table: 'table[data-bulk-table="bulkToolbar-reportComments"]',
        blindPath: '/admin/comments/bulk-blind',
        unblindPath: '/admin/comments/bulk-unblind',
    },
    {
        label: '오늘의 한마디',
        table: 'table[data-bulk-table="bulkToolbar-reportSchedule"]',
        blindPath: '/admin/schedule-comments/bulk-blind',
        unblindPath: '/admin/schedule-comments/bulk-unblind',
    },
];

export const options = {
    scenarios: {
        report_queue_bulk_contention: {
            executor: 'constant-vus',
            vus: VUS,
            duration: DURATION,
        },
    },
    thresholds: {
        // 여기서는 응답 속도보다 "500이 뜨는가/데이터가 깨지는가"가 관심사라 http_req_failed만
        // 본다 - 정상 처리는 매번 리다이렉트 후 200이라 상태코드 체크는 아래 check()로 별도 확인.
        http_req_failed: ['rate<0.01'],
    },
};

function csrfToken(page) {
    return page.html().find('meta[name="_csrf"]').first().attr('content');
}

// k6 html Element(toArray()로 얻는 개별 원소)는 .attr()/.text()는 지원하지만 .find()는 없다 -
// 그래서 행(tr) 단위로 순회하지 않고, "체크박스 전체"와 "상태 td 전체"를 각각 별도 셀렉터로 뽑아
// DOM 순서가 같다는 점(같은 tbody 안에서 위→아래로 1:1 대응)을 이용해 인덱스로 짝짓는다.
function reportRowsByCategory() {
    const res = http.get(`${BASE_URL}/admin/reports`);
    const doc = res.html();
    return TARGETS.map((t) => {
        const checkboxes = doc.find(`${t.table} .admin-bulk-check`).toArray();
        const statusCells = doc.find(`${t.table} tbody tr td:nth-child(2)`).toArray();
        const blindIds = [];
        const normalIds = [];
        checkboxes.forEach((cb, i) => {
            const id = cb.attr('value');
            const statusText = (statusCells[i] ? statusCells[i].text() : '').trim();
            (statusText === '블라인드' ? blindIds : normalIds).push(id);
        });
        return { ...t, blindIds, normalIds, ids: blindIds.concat(normalIds) };
    }).filter((t) => t.ids.length > 0);
}

function submitBulk(path, ids, csrf) {
    return http.post(`${BASE_URL}${path}`, {
        ids, // k6 http.post는 배열 값을 ids=1&ids=2...처럼 반복 파라미터로 직렬화한다.
        returnUrl: '/admin/reports',
        _csrf: csrf,
    });
}

// setup()은 본 실행과 분리된 별도 컨텍스트에서 딱 한 번 실행되고, 반환값만 각 VU의 default()/
// teardown()으로 전달된다(쿠키/세션은 공유 안 됨 - login.js와 별개로 여기서도 한 번 더 로그인이
// 필요한 이유). 목적 두 가지: (1) 실제 신고 대기열의 id를 미리 읽어와서 모든 VU가 "같은 행"을
// 두고 부딪히게 만드는 것(각 VU가 자기 나름의 데이터를 고르면 진짜 동시성 충돌이 재현 안 됨),
// (2) teardown에서 정확히 복원할 수 있도록 행별 원래 블라인드 여부를 기록해두는 것.
export function setup() {
    login(BASE_URL, ADMIN_USERNAME, ADMIN_PASSWORD);
    const idsByCategory = reportRowsByCategory();
    if (idsByCategory.length === 0) {
        throw new Error(
            '신고 대기열이 비어있어 부하테스트 대상이 없습니다 - TestDataSeeder가 실행됐는지 먼저 확인할 것.'
        );
    }
    return { idsByCategory };
}

export default function (data) {
    if (__ITER === 0) {
        login(BASE_URL, ADMIN_USERNAME, ADMIN_PASSWORD);
    }

    const reportsPage = http.get(`${BASE_URL}/admin/reports`);
    const csrf = csrfToken(reportsPage);

    // 짝수 VU는 블라인드만, 홀수 VU는 블라인드 해제만 반복 - 같은 id에 정반대 액션이 계속
    // 부딪히는 게 가장 레이스가 잘 드러나는 조합(둘 다 블라인드만 반복하면 멱등이라 큰 의미 없음).
    const acting = __VU % 2 === 0 ? 'blind' : 'unblind';

    data.idsByCategory.forEach(({ label, blindPath, unblindPath, ids }) => {
        group(`${label}-${acting}`, () => {
            const path = acting === 'blind' ? blindPath : unblindPath;
            const res = submitBulk(path, ids, csrf);
            check(res, {
                '정상 처리(리다이렉트 후 200)': (r) => r.status === 200,
                '500 에러 아님': (r) => r.status < 500,
            });
        });
    });

    sleep(0.5);
}

// 테스트 도중 블라인드/블라인드 해제가 뒤섞여 끝나므로, setup()에서 기록해둔 행별 원래 상태로
// 정확히 되돌린다 - "신고 큐 = 전부 블라인드"가 아니므로 무조건 bulk-blind만 걸면 안 된다(위
// 파일 상단 "직접 겪은 실수" 참고).
export function teardown(data) {
    login(BASE_URL, ADMIN_USERNAME, ADMIN_PASSWORD);
    data.idsByCategory.forEach(({ blindPath, unblindPath, blindIds, normalIds }) => {
        if (blindIds.length > 0) {
            const page1 = http.get(`${BASE_URL}/admin/reports`);
            submitBulk(blindPath, blindIds, csrfToken(page1));
        }
        if (normalIds.length > 0) {
            const page2 = http.get(`${BASE_URL}/admin/reports`);
            submitBulk(unblindPath, normalIds, csrfToken(page2));
        }
    });
}
