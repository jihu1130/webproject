// 마이페이지 출석 미니 캘린더 팝업(디자인 개선 계획, 2026-09-09) - "출석 현황" 타일을 누르면 열리고,
// /mypage/attendance/calendar?year=&month= API로 그 달의 출석일만 받아 42칸 그리드에 점으로 표시한다.
// GET 전용 API라 CSRF 토큰이 필요 없다(csrf.js/메타 태그 미포함 - post-form.js 등과 다른 점).
document.addEventListener('DOMContentLoaded', function () {
    var trigger = document.getElementById('attendanceCalendarTrigger');
    var overlay = document.getElementById('attendanceCalOverlay');
    if (!trigger || !overlay) return;

    var titleEl = document.getElementById('attendanceCalTitle');
    var streakEl = document.getElementById('attendanceCalStreak');
    var gridEl = document.getElementById('attendanceCalGrid');
    var prevBtn = document.getElementById('attendanceCalPrev');
    var nextBtn = document.getElementById('attendanceCalNext');
    var closeBtn = document.getElementById('attendanceCalClose');

    var today = new Date();
    var viewYear = today.getFullYear();
    var viewMonth = today.getMonth() + 1; // 1-12

    function open() {
        overlay.hidden = false;
        document.addEventListener('keydown', onKeydown);
        load();
    }

    function close() {
        overlay.hidden = true;
        document.removeEventListener('keydown', onKeydown);
    }

    function onKeydown(e) {
        if (e.key === 'Escape') close();
    }

    function changeMonth(delta) {
        viewMonth += delta;
        if (viewMonth > 12) { viewMonth = 1; viewYear++; }
        if (viewMonth < 1) { viewMonth = 12; viewYear--; }
        load();
    }

    function load() {
        titleEl.textContent = viewYear + '년 ' + viewMonth + '월';
        gridEl.innerHTML = '';
        streakEl.textContent = '불러오는 중...';

        fetch('/mypage/attendance/calendar?year=' + viewYear + '&month=' + viewMonth)
            .then(function (res) { return res.json(); })
            .then(function (data) { render(data); })
            .catch(function () { streakEl.textContent = '출석 현황을 불러오지 못했어요.'; });
    }

    function render(data) {
        var attended = {};
        (data.attendedDates || []).forEach(function (d) { attended[d] = true; });

        streakEl.innerHTML = data.checkedInToday
            ? '<i class="fa-solid fa-fire"></i> 오늘 출석 완료 · <strong>' + data.currentStreakDay + '일</strong> 연속'
            : (data.currentStreakDay > 0
                ? '<i class="fa-solid fa-fire"></i> 오늘 출석하면 <strong>' + data.currentStreakDay + '일</strong> 연속이 돼요'
                : '오늘 출석하면 새 연속 기록이 시작돼요');

        var firstOfMonth = new Date(viewYear, viewMonth - 1, 1);
        var startWeekday = firstOfMonth.getDay(); // 0=일요일
        var daysInMonth = new Date(viewYear, viewMonth, 0).getDate();
        var todayKey = formatDateKey(today.getFullYear(), today.getMonth() + 1, today.getDate());

        var html = '';
        for (var i = 0; i < startWeekday; i++) {
            html += '<span class="attendance-cal-cell attendance-cal-cell-empty"></span>';
        }
        for (var day = 1; day <= daysInMonth; day++) {
            var key = formatDateKey(viewYear, viewMonth, day);
            var classes = 'attendance-cal-cell';
            if (attended[key]) classes += ' attendance-cal-cell-attended';
            if (key === todayKey) classes += ' attendance-cal-cell-today';
            html += '<span class="' + classes + '">' + day + '</span>';
        }
        gridEl.innerHTML = html;
    }

    function formatDateKey(y, m, d) {
        return y + '-' + String(m).padStart(2, '0') + '-' + String(d).padStart(2, '0');
    }

    trigger.addEventListener('click', open);
    closeBtn.addEventListener('click', close);
    prevBtn.addEventListener('click', function () { changeMonth(-1); });
    nextBtn.addEventListener('click', function () { changeMonth(1); });
    overlay.addEventListener('click', function (e) {
        if (e.target === overlay) close();
    });
});
