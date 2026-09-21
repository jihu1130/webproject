document.addEventListener('DOMContentLoaded', function () {
    // ---- 오프닝 날짜 스탬프 ----
    var todayEl = document.getElementById('opToday');
    var stampEl = document.getElementById('opStampDate');
    if (todayEl || stampEl) {
        var now = new Date();
        var weekday = ['일', '월', '화', '수', '목', '금', '토'][now.getDay()];
        if (todayEl) todayEl.textContent = (now.getMonth() + 1) + '월 ' + now.getDate() + '일 (' + weekday + ')';
        if (stampEl) stampEl.textContent = (now.getMonth() + 1) + '.' + now.getDate();
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
