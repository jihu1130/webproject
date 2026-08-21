// 모바일 햄버거 메뉴 토글 (768px 이하에서만 CSS로 보이는 버튼) - 열림 상태는 클래스 + aria-expanded로 관리
document.addEventListener('DOMContentLoaded', function () {
    var toggle = document.getElementById('siteNavToggle');
    var menu = document.getElementById('siteNavMenu');
    if (!toggle || !menu) return;

    function setOpen(isOpen) {
        menu.classList.toggle('open', isOpen);
        toggle.setAttribute('aria-expanded', isOpen ? 'true' : 'false');
        toggle.setAttribute('aria-label', isOpen ? '메뉴 닫기' : '메뉴 열기');
        toggle.querySelector('i').className = isOpen ? 'fa-solid fa-xmark' : 'fa-solid fa-bars';
    }

    toggle.addEventListener('click', function () {
        setOpen(!menu.classList.contains('open'));
    });

    // 메뉴 안의 링크/버튼을 눌러 페이지가 이동하기 전까지는 계속 열려있을 수 있으므로,
    // 화면 폭을 넓혀 데스크톱 레이아웃으로 돌아가면 열림 상태를 초기화해둔다.
    window.addEventListener('resize', function () {
        if (window.innerWidth > 768 && menu.classList.contains('open')) {
            setOpen(false);
        }
    });
});
