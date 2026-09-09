// k6 부하테스트 공용 로그인 헬퍼 (loadtest/notification-polling.js,
// loadtest/neis-cache-thundering-herd.js에서 공용으로 씀). CSRF가 재활성화된 이후라
// (CLAUDE.md "알려진 함정" 참고) 폼 로그인도 토큰이 필요 - GET /login으로 받은 페이지에서
// th:action 폼이 자동으로 넣어준 hidden _csrf 값을 그대로 읽어서 같이 보낸다.
//
// k6는 VU(가상 사용자)마다 쿠키 저장소를 자동으로 유지하므로, 이 함수로 한 번 로그인하면
// 그 뒤 같은 VU의 요청은 별도 처리 없이 세션 쿠키가 자동으로 실린다.
import http from 'k6/http';
import { check } from 'k6';

export function login(baseUrl, username, password) {
    const loginPage = http.get(`${baseUrl}/login`);
    const csrfToken = loginPage.html().find('input[name="_csrf"]').first().attr('value');

    const res = http.post(
        `${baseUrl}/login`,
        { username, password, _csrf: csrfToken },
    );

    check(res, {
        '로그인 성공(홈으로 리다이렉트 후 200)': (r) => r.status === 200,
    });

    return res;
}
