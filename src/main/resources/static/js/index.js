document.addEventListener('DOMContentLoaded', function () {
    // ---- 히어로 목업 카드 마우스 패럴랙스 틸트 ----
    var visual = document.querySelector('.hero-visual');
    var mockup = document.getElementById('heroMockup');

    if (visual && mockup && window.matchMedia('(hover: hover)').matches) {
        visual.addEventListener('mousemove', function (e) {
            var rect = visual.getBoundingClientRect();
            var x = (e.clientX - rect.left) / rect.width - 0.5;
            var y = (e.clientY - rect.top) / rect.height - 0.5;
            var rotateY = x * 14;
            var rotateX = y * -14;
            mockup.style.transform = 'rotateX(' + rotateX + 'deg) rotateY(' + rotateY + 'deg)';
        });

        visual.addEventListener('mouseleave', function () {
            mockup.style.transform = 'rotateX(0deg) rotateY(0deg)';
        });
    }

    // ---- 맨 위로 버튼 ----
    var backToTop = document.getElementById('backToTop');
    if (backToTop) {
        window.addEventListener('scroll', function () {
            backToTop.classList.toggle('is-visible', window.scrollY > 480);
        });

        backToTop.addEventListener('click', function () {
            window.scrollTo({ top: 0, behavior: 'smooth' });
        });
    }
});
