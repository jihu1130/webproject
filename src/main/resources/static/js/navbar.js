// 모바일 햄버거 메뉴 토글 (768px 이하에서만 CSS로 보이는 버튼) - 열림 상태는 클래스 + aria-expanded로 관리
document.addEventListener('DOMContentLoaded', function () {
    var toggle = document.getElementById('siteNavToggle');
    var menu = document.getElementById('siteNavMenu');
    var backdrop = document.getElementById('siteNavBackdrop');
    if (!toggle || !menu) return;

    // 모바일에서는 전체화면 드로어라 열려있는 동안 배경 스크롤을 막아야 드로어 안에서만
    // 스크롤된다(안 막으면 메뉴 위로 손가락을 움직였을 때 뒤 페이지가 같이 밀려 올라간다).
    function setOpen(isOpen) {
        menu.classList.toggle('open', isOpen);
        if (backdrop) backdrop.classList.toggle('open', isOpen);
        document.body.classList.toggle('site-nav-lock-scroll', isOpen);
        toggle.setAttribute('aria-expanded', isOpen ? 'true' : 'false');
        toggle.setAttribute('aria-label', isOpen ? '메뉴 닫기' : '메뉴 열기');
        toggle.querySelector('i').className = isOpen ? 'fa-solid fa-xmark' : 'fa-solid fa-bars';
    }

    toggle.addEventListener('click', function () {
        setOpen(!menu.classList.contains('open'));
    });

    if (backdrop) {
        backdrop.addEventListener('click', function () {
            setOpen(false);
        });
    }

    document.addEventListener('keydown', function (e) {
        if (e.key === 'Escape' && menu.classList.contains('open')) {
            setOpen(false);
            toggle.focus();
        }
    });

    // 메뉴 안의 링크/버튼을 눌러 페이지가 이동하기 전까지는 계속 열려있을 수 있으므로,
    // 화면 폭을 넓혀 데스크톱 레이아웃으로 돌아가면 열림 상태를 초기화해둔다.
    window.addEventListener('resize', function () {
        if (window.innerWidth > 768 && menu.classList.contains('open')) {
            setOpen(false);
        }
    });
});
