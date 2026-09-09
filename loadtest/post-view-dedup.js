// 시나리오 6 - 게시글 조회수 중복 방지 동시성.
// PostViewService의 IP+24시간 dedup(recentlyViewedByIp() 체크 → recordView() 저장, CLAUDE.md
// "조회수 중복 방지" 참고)이 동시 요청에서도 정확히 1회로만 카운트되는지 확인한다 - 체크와
// 저장 사이에 시간차가 있어 이론상 TOCTOU 레이스가 가능한 지점(todo.md #24 항목 6).
//
// 여러 VU(=서로 다른 세션이라 세션 기반 dedup은 안 걸림, 단 k6를 이 로컬 컴퓨터에서 돌리므로
// IP는 전부 동일 - 바로 이 IP 레이어의 레이스를 노리는 것)가 같은 글을 동시에 처음 여는 상황을
// 재현한다. setup()에서 커뮤니티 목록(글을 열지 않아도 보이는 목록 화면의 조회수, 페이지
// 로딩 자체는 조회수를 안 올림)에서 첫 글의 uuid와 "부하 걸기 전" 조회수를 읽어두고, 부하가
// 끝난 뒤 teardown()에서 다시 읽어 증가량을 비교한다. 정상이라면 VU 수와 무관하게 증가량은
// 정확히 1이어야 한다(이미 오늘 이 IP로 본 적 있는 글이면 0).
//
// **재실행 시 주의**: 같은 글로 다시 돌리면 이미 오늘 이 IP로 본 걸로 남아있어 증가량이 계속
// 0으로 나온다(이것도 정상 동작) - 레이스를 다시 재현하려면 새 글을 쓰거나(추천, 아래 참고),
// DB에서 `DELETE FROM post_views WHERE ip='...'`로 지울 것.
//
// **절대 운영 서버(webschool.kro.kr)를 BASE_URL로 넘기지 말 것 - 로컬/스테이징 전용.**
//
// 실행: k6 run loadtest/post-view-dedup.js
//   -e BASE_URL=http://localhost:8888 (기본값, 생략 가능)
//   -e VUS=10 (동시 조회 세션 수, 기본 10 - test1~5를 순환하며 재사용)
import http from 'k6/http';
import { check } from 'k6';
import { login } from './lib/login.js';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8888';
const VUS = __ENV.VUS ? parseInt(__ENV.VUS, 10) : 10;
const USERS = ['test1', 'test2', 'test3', 'test4', 'test5'];

export function setup() {
    const listPage = http.get(`${BASE_URL}/posts`);
    const item = listPage.html().find('li.post-list-item').first();
    const href = item.find('a.post-list-link').attr('href');
    if (!href) {
        throw new Error('커뮤니티 목록에서 게시글을 하나도 못 찾음 - 먼저 글을 하나 이상 작성해둘 것');
    }
    const viewCountText = item.find('.post-list-views span').first().text().trim();
    const viewCountBefore = parseInt(viewCountText, 10) || 0;
    return { detailUrl: `${BASE_URL}${href}`, viewCountBefore };
}

export const options = {
    scenarios: {
        post_view_dedup: {
            // 모든 VU가 준비되는 즉시 딱 1번씩 - "여러 명이 동시에 이 글을 처음 연다"를
            // 재현하는 게 목적이라 반복(iterations 여러 번)은 필요 없다.
            executor: 'per-vu-iterations',
            vus: VUS,
            iterations: 1,
            maxDuration: '30s',
        },
    },
};

export default function (data) {
    const username = USERS[(__VU - 1) % USERS.length];
    login(BASE_URL, username, username);

    const res = http.get(data.detailUrl);
    check(res, {
        '200 응답': (r) => r.status === 200,
    });
}

export function teardown(data) {
    const after = http.get(data.detailUrl);
    const viewCountText = after.html().find('.post-detail-views span').first().text().trim();
    const viewCountAfter = parseInt(viewCountText, 10) || 0;
    const increase = viewCountAfter - data.viewCountBefore;

    console.log(`조회수 변화: ${data.viewCountBefore} -> ${viewCountAfter} (증가량 ${increase}, VU수 ${__ENV.VUS || 10})`);
    if (increase > 1) {
        console.error(`레이스 컨디션 의심 - 동시 접속 ${__ENV.VUS || 10}명인데 조회수가 ${increase}회 증가함(1회여야 정상)`);
    } else if (increase === 1) {
        console.log('정상 - 동시 접속에도 조회수는 1회만 증가함');
    } else {
        console.log('증가량 0 - 이미 오늘 이 IP로 본 글일 가능성(정상, 새 글로 다시 시도해볼 것)');
    }
}
