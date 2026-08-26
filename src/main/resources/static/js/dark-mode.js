// 다크모드 수동 토글 - navbar.html 맨 위 인라인 스크립트가 body 렌더링 전에 이미 data-theme을
// 한 번 정해뒀다(localStorage 저장값 우선, 없으면 시스템 설정). 여기서는 버튼 클릭 시 토글 +
// 저장, 아이콘 동기화, 그리고 사용자가 명시적으로 고른 적 없을 때만 시스템 설정 변경을
// 실시간으로 따라가는 것만 담당한다.
document.addEventListener('DOMContentLoaded', function () {
    var toggle = document.getElementById('themeToggle');
    if (!toggle) return;

    var icon = toggle.querySelector('i');
    var label = document.getElementById('themeToggleLabel');

    function applyIcon(theme) {
        icon.className = theme === 'dark' ? 'fa-solid fa-sun' : 'fa-solid fa-moon';
        toggle.setAttribute('aria-label', theme === 'dark' ? '라이트모드로 전환' : '다크모드로 전환');
        toggle.setAttribute('title', theme === 'dark' ? '라이트모드로 전환' : '다크모드로 전환');
        if (label) label.textContent = theme === 'dark' ? '라이트모드' : '다크모드';
    }

    applyIcon(document.documentElement.getAttribute('data-theme'));

    toggle.addEventListener('click', function () {
        var next = document.documentElement.getAttribute('data-theme') === 'dark' ? 'light' : 'dark';
        document.documentElement.setAttribute('data-theme', next);
        try { localStorage.setItem('theme', next); } catch (e) {}
        applyIcon(next);
    });

    // 명시적으로 고른 적 없는 사용자(localStorage에 저장된 값이 없는 사용자)는 시스템 설정이
    // 바뀌면(야간 예약 등) 페이지를 새로고침하지 않아도 그대로 따라가게 한다.
    if (window.matchMedia) {
        window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', function (e) {
            var saved = null;
            try { saved = localStorage.getItem('theme'); } catch (err) {}
            if (saved === 'light' || saved === 'dark') return;
            var theme = e.matches ? 'dark' : 'light';
            document.documentElement.setAttribute('data-theme', theme);
            applyIcon(theme);
        });
    }
});
