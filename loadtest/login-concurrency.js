// 시나리오 5 - 로그인 시도 횟수 제한 동시성.
// LoginAttemptService.recordFailure()가 원자적 벌크 UPDATE(userRepository.
// incrementFailedLoginAttempts)로 DB의 실패 횟수는 안전하게 늘리지만, 그 직후
// "newAttempts = user.getFailedLoginAttempts() + 1"로 잠금 여부를 판단하는 부분은 그 UPDATE
// *이전에* 메모리로 읽어온 user 객체의 값을 그대로 쓴다(코드 39~40번째 줄) - 즉 동시에 여러
// 요청이 실패하면 각 요청이 "자기가 읽은 시점 기준"으로만 판단해서, DB 실제 실패 횟수는 이미
// MAX_ATTEMPTS(5)를 넘었는데도 그걸 감지 못하는 요청이 생길 수 있다(전형적인 read-then-write
// 레이스). 이 스크립트는 그 레이스가 실제로 관측되는지 확인한다.
//
// 확인 방법: 같은 계정에 비밀번호 틀린 로그인을 N개(기본 8, MAX_ATTEMPTS=5보다 많이) 동시에
// 쏘고, 각 응답의 Location 헤더(LoginFailureHandler가 항상 /login?error=true&attempts=N...
// 또는 /login?locked=true&minutes=M로 리다이렉트시킴, redirects:0으로 직접 확인)에 찍힌
// attempts 값을 전부 모아서 콘솔에 그대로 출력한다 - 정상이라면 1~5가 정확히 한 번씩(순서는
// 뒤섞일 수 있음) 나오고 마지막 하나 이상이 locked=true여야 한다. 중복된 attempts 값이
// 여러 개 찍히면 위에서 설명한 레이스가 실제로 발생한 것.
//
// ⚠️ **이 스크립트는 실행하면 대상 계정을 실제로 5분간 잠근다** - 다른 용도로 그 계정을 쓰고
// 있다면(다른 세션의 로그인 테스트 등) 절대 겹쳐서 돌리지 말 것. USERNAME을 명시적으로 지정하는
// 걸 권장(기본값은 test5 - 이 프로젝트 세션에서 상대적으로 덜 쓰인 계정).
//
// **절대 운영 서버(webschool.kro.kr)를 BASE_URL로 넘기지 말 것 - 로컬/스테이징 전용,
// 게다가 실제 계정을 잠그는 부작용이 있어 운영에선 더더욱 안 됨.**
//
// 실행: k6 run loadtest/login-concurrency.js
//   -e BASE_URL=http://localhost:8888 (기본값, 생략 가능)
//   -e USERNAME=test5 (잠글 대상 계정, 기본 test5)
//   -e VUS=8 (동시 실패 시도 수, 기본 8 - MAX_ATTEMPTS=5보다 커야 잠금까지 확인 가능)
import http from 'k6/http';
import { check } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8888';
const USERNAME = __ENV.USERNAME || 'test5';
const VUS = __ENV.VUS ? parseInt(__ENV.VUS, 10) : 8;

export const options = {
    scenarios: {
        login_concurrency: {
            // 모든 VU가 준비되는 즉시 딱 1번씩 - "동시에 실패가 몰린다"를 재현하는 게 목적.
            executor: 'per-vu-iterations',
            vus: VUS,
            iterations: 1,
            maxDuration: '30s',
        },
    },
};

export default function () {
    const loginPage = http.get(`${BASE_URL}/login`);
    const csrfToken = loginPage.html().find('input[name="_csrf"]').first().attr('value');

    // 일부러 틀린 비밀번호로 로그인 시도 - redirects:0으로 응답의 Location 헤더를 직접 읽는다.
    const res = http.post(
        `${BASE_URL}/login`,
        { username: USERNAME, password: 'definitely-wrong-password', _csrf: csrfToken },
        { redirects: 0 },
    );

    check(res, {
        '302로 리다이렉트(에러 또는 잠금 안내)': (r) => r.status === 302,
    });

    const location = res.headers['Location'] || '';
    console.log(`VU${__VU}: ${location}`);
}
