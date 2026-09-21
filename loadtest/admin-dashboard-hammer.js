// 시나리오 8 - 관리자 대시보드(KPI 개요) 단독 집중 부하.
// admin-dashboard-tour.js(시나리오 7)는 14개 탭을 한 번씩만 지나가서 /admin/dashboard 자체에는
// 거의 부하가 안 걸렸다(그 실행에서 p95=29.9ms로 사실상 무의미한 수치). 이 화면은 다른 관리자
// 목록 화면과 달리 한 요청 안에서 활성 사용자 수·전체 게시글 수·미해결 신고 집계(게시글/댓글/
// 한마디 3개 테이블 COUNT) · 미답변 문의 수 · 최근 14일 게시글 추이(날짜별 GROUP BY 흉내를
// 애플리케이션에서 직접 버킷팅) · 전체 활성 유저의 포인트 티어 분포(전수 스캔) · 서버 CPU/힙
// 히스토리까지 한 번에 계산한다(AdminDashboardController 참고) - 다른 관리자 화면보다 훨씬
// 무거운 단일 엔드포인트라 이것만 따로 반복 조회해서 실제 부담을 확인한다.
//
// **직접 겪은 문제(2026-09-21)**: 실행 중 Spring DevTools가 우연히 전체 재컴파일+재시작을 하면서
// (webschool.log에 "Restarting due to 107 class path changes" 기록, 이 로컬 환경은 IntelliJ의
// 백그라운드 자동 빌드가 Gradle의 build/classes를 같이 건드리면서 주기적으로 이게 발생하는 것으로
// 추정 - IDE 설정 문제라 이 스크립트에서 고칠 수 있는 부분은 아님) "connection actively refused"가
// 몇 초간 발생, http_req_failed가 튀어 임계값이 실패했다 - 앱/대시보드 자체의 버그가 아니라 로컬
// 개발 서버 재시작과 우연히 겹친 것(재시작이 끝난 뒤 재실행하면 매번 p95<20ms, 실패율 0%로
// 깨끗하게 통과). 처음엔 재시도 3회(1초 간격, 총 최대 2초)로 고정했는데, k6가 동시에 CPU를 먹는
// 상황에서는 같은 재시작이 유휴 상태보다 훨씬 오래 걸렸다(1.7s → 2.0s → 3.1s → 7.2s로 재시작
// 자체가 점점 느려지는 것도 직접 관측함) - 그래서 "몇 번"이 아니라 "몇 초까지"로 재시도 예산을
// 바꾼다. 그렇다고 이 노이즈를 그냥 무시하면 나중에 "진짜" 5xx/타임아웃이 나도 똑같이 "또 재시작
// 겹쳤나보다"하고 지나치게 될 위험이 있어서, 재시도는 일시적 접속 불가(연결 자체가 안 됨, status 0)
// 만 흡수하고 진짜 실패(4xx/5xx)는 즉시 실패로 잡는다.
//
// **절대 운영 서버(webschool.kro.kr)를 BASE_URL로 넘기지 말 것 - 로컬/스테이징 전용.**
// 대시보드는 총관리자 전용(AdminAccessInterceptor 참고)이라 admin/admin! 계정으로 로그인한다.
//
// 실행: k6 run loadtest/admin-dashboard-hammer.js
//   -e BASE_URL=http://localhost:8888 (기본값, 생략 가능)
//   -e ADMIN_USERNAME=admin -e ADMIN_PASSWORD=admin! (기본값, 생략 가능)
//   -e VUS=10 (동시 조회 수, 기본 10 - 실제 총관리자 수보다 넉넉하게 잡아서 이 화면의 한계를
//              먼저 확인해두려는 의도, 실사용 규모가 아니라 상한선 탐색 목적)
//   -e DURATION=30s (부하를 유지할 시간, 기본 30초)
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate } from 'k6/metrics';
import { login } from './lib/login.js';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8888';
const ADMIN_USERNAME = __ENV.ADMIN_USERNAME || 'admin';
const ADMIN_PASSWORD = __ENV.ADMIN_PASSWORD || 'admin!';
const VUS = __ENV.VUS ? parseInt(__ENV.VUS, 10) : 10;
const DURATION = __ENV.DURATION || '30s';
// 연결 자체가 안 되는 순간(devtools 재시작 등)을 몇 초까지 재시도로 흡수할지 - 고정 횟수가 아니라
// 시간 예산으로 잡는다. 관측된 재시작 소요 시간이 부하 상황에서 최대 7.2초까지 늘어났으므로,
// 그 두 배 이상인 15초를 예산으로 둔다(devtools 재시작이 아니라 진짜로 서버가 죽은 경우라면 이
// 예산을 다 써도 실패로 잡히는 게 맞음 - 무한 재시도는 아님).
const RETRY_BUDGET_MS = 15000;
const RETRY_INTERVAL_S = 1;

// 기본 http_req_failed는 "이 요청이 실패했는가"만 보고 재시도 여부를 모른다 - 재시도해서 결국
// 성공한 요청까지 실패로 잡히면 임계값이 다시 노이즈에 흔들린다. 그래서 "재시도까지 다 써도
// 최종적으로 실패했는가"만 별도로 집계하는 커스텀 지표를 두고, 임계값은 이 지표에 건다.
const dashboardFailureRate = new Rate('dashboard_final_failure_rate');

export const options = {
    scenarios: {
        admin_dashboard_hammer: {
            executor: 'constant-vus',
            vus: VUS,
            duration: DURATION,
        },
    },
    thresholds: {
        // 집계 쿼리 여러 개가 겹치는 화면이라 알림 폴링(200ms)/랭킹(500ms)보다 넉넉하게 잡되,
        // admin-dashboard-tour.js와 같은 800ms 기준 - 이걸 넘기면 "관리자가 대시보드를 열 때마다
        // 기다린다"는 체감이 생기는 수준.
        http_req_duration: ['p(95)<800'],
        // 재시도까지 실패한 "진짜" 실패 비율만 본다(위 dashboardFailureRate 설명 참고) - 재시작
        // 중 잠깐 튄 연결 거부는 재시도로 성공하면 여기 안 잡힌다.
        dashboard_final_failure_rate: ['rate<0.01'],
    },
};

// VU마다 최초 1회만 로그인하고, 이후 반복은 세션 쿠키를 재사용한다(admin-dashboard-tour.js와
// 동일한 패턴 - 매 반복 재로그인하면 로그인 자체의 부하까지 섞여서 대시보드만의 순수 부하를
// 못 잰다).
let sessionReady = false;

// status 0은 응답 자체를 못 받은 것(연결 거부/타임아웃 등 네트워크 레벨 실패) - devtools 재시작
// 같은 일시적 상황이 여기 해당한다. 4xx/5xx처럼 서버가 실제로 응답은 한 실패는 재시도해도 같은
// 결과일 가능성이 높으므로 즉시 반환한다(진짜 버그를 재시도로 덮어버리지 않기 위함).
function getDashboardWithRetry() {
    const deadline = Date.now() + RETRY_BUDGET_MS;
    let res;
    for (;;) {
        res = http.get(`${BASE_URL}/admin/dashboard`);
        if (res.status !== 0) return res;
        if (Date.now() >= deadline) return res;
        sleep(RETRY_INTERVAL_S);
    }
}

export default function () {
    if (!sessionReady) {
        login(BASE_URL, ADMIN_USERNAME, ADMIN_PASSWORD);
        sessionReady = true;
    }

    const res = getDashboardWithRetry();
    const ok = res.status === 200;
    dashboardFailureRate.add(!ok);
    check(res, {
        '200 응답(연결 실패 시 최대 15초 재시도 후)': () => ok,
    });
    sleep(1); // 실제로 대시보드를 열어두고 숫자를 훑어보는 최소 간격 흉내
}
