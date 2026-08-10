// 공용 스크롤 등장 애니메이션. ".reveal-on-scroll" 클래스가 붙은 요소가
// 화면에 들어오면 ".is-revealed"를 붙여 theme.css의 트랜지션을 트리거한다.
(function () {
    function init() {
        var elements = document.querySelectorAll('.reveal-on-scroll');
        if (!elements.length) return;

        if (!('IntersectionObserver' in window)) {
            for (var i = 0; i < elements.length; i++) elements[i].classList.add('is-revealed');
            return;
        }

        var observer = new IntersectionObserver(function (entries) {
            entries.forEach(function (entry) {
                if (entry.isIntersecting) {
                    entry.target.classList.add('is-revealed');
                    observer.unobserve(entry.target);
                }
            });
        }, { threshold: 0.15, rootMargin: '0px 0px -40px 0px' });

        for (var j = 0; j < elements.length; j++) {
            var el = elements[j];
            var customDelay = el.style.getPropertyValue('--reveal-delay');
            var step = customDelay ? parseInt(customDelay, 10) : (j % 6);
            el.style.transitionDelay = step * 70 + 'ms';
            observer.observe(el);
        }
    }

    document.addEventListener('DOMContentLoaded', init);
})();
