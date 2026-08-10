// 공용 상대 시간(timeago) 유틸.
// 서버가 내려주는 날짜 문자열은 "MM.dd HH:mm" 형식(연도 없음)이라, 현재 연도를 가정해서 복원한다.
// 복원한 시각이 미래로 계산되면(예: 12월 글을 다음 해 1월에 보는 경우) 작년으로 보정한다.
(function (global) {
    function parseCompactDate(str) {
        var m = /^(\d{2})\.(\d{2}) (\d{2}):(\d{2})$/.exec(str || '');
        if (!m) return null;

        var now = new Date();
        var month = parseInt(m[1], 10) - 1;
        var day = parseInt(m[2], 10);
        var hour = parseInt(m[3], 10);
        var minute = parseInt(m[4], 10);

        var date = new Date(now.getFullYear(), month, day, hour, minute);
        if (date.getTime() - now.getTime() > 24 * 60 * 60 * 1000) {
            date = new Date(now.getFullYear() - 1, month, day, hour, minute);
        }
        return date;
    }

    function formatRelativeTime(str) {
        var date = parseCompactDate(str);
        if (!date) return str;

        var diffSec = Math.max(0, Math.floor((Date.now() - date.getTime()) / 1000));

        if (diffSec < 60) return '방금 전';
        if (diffSec < 3600) return Math.floor(diffSec / 60) + '분 전';
        if (diffSec < 86400) return Math.floor(diffSec / 3600) + '시간 전';
        if (diffSec < 7 * 86400) return Math.floor(diffSec / 86400) + '일 전';
        return str;
    }

    function applyRelativeTime(el) {
        if (!el) return;
        var original = el.getAttribute('data-datetime');
        if (!original) {
            original = el.textContent.trim();
            el.setAttribute('data-datetime', original);
        }
        el.textContent = formatRelativeTime(original);
        el.title = original;
    }

    function applyRelativeTimeAll(selector, root) {
        var nodes = (root || document).querySelectorAll(selector);
        for (var i = 0; i < nodes.length; i++) applyRelativeTime(nodes[i]);
    }

    global.WebSchoolTimeago = {
        format: formatRelativeTime,
        apply: applyRelativeTime,
        applyAll: applyRelativeTimeAll
    };
})(window);
