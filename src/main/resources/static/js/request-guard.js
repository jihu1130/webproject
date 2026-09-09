// 버튼 연타로 같은 API 요청이 응답 오기 전에 중복으로 나가는 걸 막는 공용 헬퍼 -
// modal.js/csrf.js와 동일한 "작은 전역 헬퍼" 컨벤션. 요청 중인 버튼에 data-requesting을
// 표시해두고, 같은 버튼이 요청 중일 때 다시 눌리면 무시한다. 응답(성공/실패 무관)이 오면
// 자동으로 해제되므로 호출부에서 따로 재활성화 코드를 쓸 필요 없다.
var WebSchoolRequestGuard = {
    // el: 클릭을 트리거한 버튼 요소, fn: fetch()의 Promise 체인을 반환하는 함수.
    // 이미 요청 중이면 fn()을 아예 호출하지 않고 undefined를 반환한다.
    run: function (el, fn) {
        if (!el || el.dataset.requesting === 'true') return;
        el.dataset.requesting = 'true';
        var release = function () { delete el.dataset.requesting; };

        var result = fn();
        if (result && typeof result.finally === 'function') {
            result.finally(release);
        } else {
            release();
        }
        return result;
    }
};
