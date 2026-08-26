// 네비바 종 아이콘 배지 - 서버 렌더링 시점의 초기값(GlobalModelAdvice) 위에 주기적으로 폴링해서
// 다른 탭/기기에서 생긴 알림도 페이지를 새로고침하지 않고 반영되게 한다.
document.addEventListener('DOMContentLoaded', function () {
    // 모바일 상단바 퀵 액세스(.site-nav-mobile-quick)에도 같은 배지가 하나 더 있어서
    // (navbar.html 참고) id 하나가 아니라 클래스로 전부 찾아 동시에 갱신한다.
    var badges = document.querySelectorAll('.site-nav-bell-badge');
    if (!badges.length) return;

    var POLL_INTERVAL_MS = 20000;

    function applyCount(count) {
        badges.forEach(function (badge) {
            if (count > 0) {
                badge.textContent = count > 99 ? '99+' : String(count);
                badge.classList.remove('is-hidden');
            } else {
                badge.textContent = '0';
                badge.classList.add('is-hidden');
            }
        });
    }

    function poll() {
        fetch('/notifications/unread-count', { credentials: 'same-origin' })
            .then(function (res) { return res.ok ? res.json() : null; })
            .then(function (data) { if (data) applyCount(data.count); })
            .catch(function () { /* 네트워크 일시 오류는 조용히 무시하고 다음 폴링에서 재시도 */ });
    }

    setInterval(poll, POLL_INTERVAL_MS);
});
