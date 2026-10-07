// 인라인 이벤트 속성(onchange="this.form.submit()", onclick="history.back()") 대신 쓰는 공용 동작.
// Content-Security-Policy(보안 점검 L4)가 인라인 스크립트를 nonce 붙은 <script>만 허용해서 on* 속성은
// 브라우저가 실행하지 않는다 - 새 화면에서도 on* 속성을 쓰지 말고 아래 data 속성을 붙일 것
// (모든 페이지가 fragments/head.html의 commonScripts로 이 파일을 불러온다).
//   data-auto-submit  : select/input 값이 바뀌면 그 요소가 속한 폼을 바로 제출(목록 필터·페이지 크기)
//   data-history-back : 클릭하면 이전 페이지로
// 제출 전 확인창은 modal.js의 data-confirm을 쓴다.
(function () {
    document.addEventListener('change', function (e) {
        var el = e.target;
        if (el instanceof Element && el.hasAttribute('data-auto-submit') && el.form) {
            el.form.submit();
        }
    });

    document.addEventListener('click', function (e) {
        if (e.target instanceof Element && e.target.closest('[data-history-back]')) {
            history.back();
        }
    });
})();
