// 시나리오 1(최우선, todo.md #24) - 알림 폴링 배경 부하.
// 로그인한 사용자 전원이 20초마다 두드리는 /notifications/unread-count(notification.js)는
// 실사용자가 늘면 그 자체로 상시 배경 부하가 된다 - 동시 로그인 세션 N개가 계속 폴링하는
// 상황을 재현한다.
//
// **절대 운영 서버(webschool.kro.kr)를 BASE_URL로 넘기지 말 것 - 로컬/스테이징 전용.**
// TestDataSeeder가 만든 test1~test5(아이디=비밀번호) 계정을 그대로 씀.
//
// 실행: k6 run loadtest/notification-polling.js
//   -e BASE_URL=http://localhost:8888 (기본값, 생략 가능)
//   -e VUS=5 (동시 세션 수, 기본 5 - test1~5를 순환하며 재사용하므로 5보다 크게 줘도 됨)
//   -e POLL_COUNT=30 (VU당 폴링 횟수, 기본 30회 = 20초 * 30 ≈ 10분 세션)
import http from 'k6/http';
import { sleep, check } from 'k6';
import { login } from './lib/login.js';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8888';
const VUS = __ENV.VUS ? parseInt(__ENV.VUS, 10) : 5;
const POLL_COUNT = __ENV.POLL_COUNT ? parseInt(__ENV.POLL_COUNT, 10) : 30;
const POLL_INTERVAL_SECONDS = 20; // notification.js의 실제 폴링 주기와 동일

const USERS = ['test1', 'test2', 'test3', 'test4', 'test5'];

export const options = {
    scenarios: {
        notification_polling: {
            executor: 'per-vu-iterations',
            vus: VUS,
            iterations: 1,
            maxDuration: `${POLL_COUNT * POLL_INTERVAL_SECONDS + 60}s`,
        },
    },
    thresholds: {
        // 배경 폴링 API는 200ms를 넘기면 안 되는 게 목표(사용자 체감엔 안 보이지만 서버 자원은
        // 계속 먹는 요청이라, 느려지기 시작하면 다른 요청들도 밀리기 시작했다는 신호).
        http_req_duration: ['p(95)<200'],
        http_req_failed: ['rate<0.01'],
    },
};

export default function () {
    const username = USERS[(__VU - 1) % USERS.length];
    login(BASE_URL, username, username);

    for (let i = 0; i < POLL_COUNT; i++) {
        const res = http.get(`${BASE_URL}/notifications/unread-count`);
        check(res, {
            '200 응답': (r) => r.status === 200,
            'JSON 응답(로그인 페이지로 안 새고 있음)': (r) =>
                (r.headers['Content-Type'] || '').includes('application/json'),
        });
        sleep(POLL_INTERVAL_SECONDS);
    }
}
