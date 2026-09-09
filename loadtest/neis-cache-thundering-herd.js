// 시나리오 2(최우선, todo.md #24) - 캘린더 NEIS 캐시 만료 몰림(thundering herd).
// SchoolService의 24시간 TTL 캐시(CLAUDE.md 참고)가 같은 학교 학생들에게 비슷한 시점에
// 동시 만료되면, 여러 요청이 한꺼번에 캐시 미스로 NEIS API를 때릴 수 있다 - 같은 학교/
// 학년/반/날짜 조합에 여러 계정이 동시 조회하는 상황을 재현한다.
//
// **캐시 미스를 확실히 재현하려면 DATE를 이전에 한 번도 조회한 적 없는 값으로 줄 것**
// (이미 캐시된 날짜로 돌리면 전부 캐시 히트라 몰림 자체가 재현되지 않는다) - 기본값은
// 실행 시각 기준으로 자동 생성하므로 보통 별도 지정 안 해도 됨.
//
// **절대 운영 서버(webschool.kro.kr)를 BASE_URL로 넘기지 말 것 - 로컬/스테이징 전용,
// 실제 NEIS Open API를 호출하는 시나리오라 남용하면 안 됨(개인 발급 키의 호출 한도도 있음).**
//
// 실행: k6 run loadtest/neis-cache-thundering-herd.js
//   -e BASE_URL=http://localhost:8888 (기본값, 생략 가능)
//   -e VUS=5 (동시 조회 계정 수, 기본 5 - test1~5 순환)
//   -e DATE=20261225 (yyyyMMdd, 생략하면 자동 생성)
import http from 'k6/http';
import { check } from 'k6';
import { login } from './lib/login.js';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8888';
const VUS = __ENV.VUS ? parseInt(__ENV.VUS, 10) : 5;

// TestDataSeeder가 심어둔 학교(CLAUDE.md 참고) - 아산배방중학교, SchoolController
// 기본 파라미터값과 동일해서 별도 학교 검색 없이 바로 씀.
const ATPT_CODE = 'N10';
const SCHOOL_CODE = '8181104';
const GRADE = 1;
const CLASS_NM = '1';

// 실제 존재하는 날짜일 필요는 없지만(NEIS가 빈 배열/캐시 미존재로 응답해도 캐시 미스 자체는
// 동일하게 재현됨), **달력상 유효한 날짜여야 한다** - SchoolService.getCalendarDetails()가
// date 파라미터를 검증 없이 LocalDate.parse()에 바로 넘기므로(2026-09-09 이 스크립트로 직접
// 발견 - 월/일이 잘못된 문자열을 넘기면 DateTimeParseException으로 500이 났었음), 문자열을
// 직접 조립하지 않고 Date 객체로 계산해서 항상 유효한 날짜가 나오게 한다. 10~20년 뒤 범위에서
// 무작위로 골라 실행마다 "한 번도 캐시된 적 없는 키"가 되게 한다.
function futureDateString() {
    var d = new Date();
    d.setFullYear(d.getFullYear() + 10 + Math.floor(Math.random() * 10));
    d.setDate(d.getDate() + Math.floor(Math.random() * 365));
    var pad = function (n) { return String(n).padStart(2, '0'); };
    return '' + d.getFullYear() + pad(d.getMonth() + 1) + pad(d.getDate());
}
const DATE = __ENV.DATE || futureDateString();

export const options = {
    scenarios: {
        neis_cache_thundering_herd: {
            // 모든 VU가 준비되는 즉시 딱 1번씩만 - "동시에 몰린다"를 재현하는 게 목적이라
            // 반복 실행(iterations 여러 번)은 오히려 첫 요청이 캐시를 채워버려 이후 요청은
            // 전부 히트가 되므로 이 시나리오와 안 맞는다.
            executor: 'per-vu-iterations',
            vus: VUS,
            iterations: 1,
            maxDuration: '30s',
        },
    },
    thresholds: {
        http_req_failed: ['rate<0.01'],
    },
};

export default function () {
    const USERS = ['test1', 'test2', 'test3', 'test4', 'test5'];
    const username = USERS[(__VU - 1) % USERS.length];
    login(BASE_URL, username, username);

    const url =
        `${BASE_URL}/school/api/calendar-details` +
        `?atptCode=${ATPT_CODE}&schoolCode=${SCHOOL_CODE}&date=${DATE}&grade=${GRADE}&classNm=${CLASS_NM}`;

    const res = http.get(url);
    check(res, {
        '200 응답': (r) => r.status === 200,
        '5초 이내 응답(NEIS 왕복 포함)': (r) => r.timings.duration < 5000,
    });
}
