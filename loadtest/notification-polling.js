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
//
// **알려진 로컬 환경 노이즈(2026-09-09 직접 조사)**: k6를 이 앱과 같은 컴퓨터에서 돌리면
// http_req_duration의 p95 임계값(200ms)이 종종 실패로 뜬다(실제로 300~780ms까지 관측됨).
// 근데 이게 앱 버그가 아니라는 걸 직접 검증했다 - 아래 전부 재현 실패(=정상):
//   - VUS=1(동시성 없음)로 돌리면 깨끗하게 통과(p95=46ms)
//   - curl로 정확히 같은 패턴(5개 세션 동시 로그인 + 동시 폴링 버스트 3라운드)을 그대로
//     재현해도 전부 30ms 이내
//   - DB 쿼리(notifications 61행, recipient_id 인덱스 있음)·세션 레지스트리 락·
//     ServerMetricsHistoryService의 60초 스케줄러 전부 원인에서 배제(직접 하나씩 확인)
// 즉 VUS>1일 때만, k6 실행 그 자체에서만 재현된다 - 부하테스트 모범사례가 항상 경고하는
// "부하 생성기와 대상 서버가 같은 머신 자원(CPU)을 공유"하는 문제로 추정(막 받은 서명 없는
// k6.exe라 Windows 실시간 보안 검사 영향일 수도 있음). **다른 머신에서 k6를 돌리거나, 이
// 결과의 p95 실패 자체를 곧이곧대로 "앱이 느려졌다"로 해석하지 말 것** - avg/med가 정상
// 범위(수십 ms)인데 p90/p95만 튀면 이 노이즈를 의심할 것.
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
