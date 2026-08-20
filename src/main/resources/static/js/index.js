document.addEventListener('DOMContentLoaded', function () {
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
