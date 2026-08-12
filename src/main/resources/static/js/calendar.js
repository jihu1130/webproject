document.addEventListener('DOMContentLoaded', function () {
    var calGridMain = document.getElementById('calGridMain');
    var calGridBody = document.getElementById('calGridBody');
    var dayDetailPanel = document.getElementById('dayDetailPanel');

    var today = new Date();
    var currentYear = today.getFullYear();
    var currentMonth = today.getMonth() + 1; // 1~12

    var selectedSchool = null; // { schoolName, schoolCode, officeCode, schoolKind }
    var selectedDateStr = formatLocalDate(today); // 방금 클릭한(선택된) 날짜 ('YYYY-MM-DD') - 페이지를 열면
    // 자동으로 오늘 날짜가 선택되어 상세 패널까지 곧바로 열린다(아래 handleDayClick(selectedDateStr) 참고)
    var currentCommentDate = null; // 현재 패널에 열려있는 날짜 ('YYYYMMDD', 댓글 API용)
    var currentCommentGrade = null; // 댓글이 공유되는 학년
    var currentCommentClassNm = null; // 댓글이 공유되는 반
    var currentMonthEventMap = {}; // 'YYYY-MM-DD' -> 학사일정명 배열 (하루에 여러 일정이 겹칠 수 있음, 현재 보이는 42칸 그리드 범위)

    var DOW_KO = ['일', '월', '화', '수', '목', '금', '토'];

    var gradeSelect = document.getElementById('gradeSelect');
    var classSelect = document.getElementById('classSelect');

    // 1. 캘린더 그리드 초기화
    renderGrid();

    gradeSelect.addEventListener('change', function () {
        updateTitle();
        refreshClassOptions();
    });
    classSelect.addEventListener('change', updateTitle);

    applyMySchoolIfAvailable();
    initSchoolSearch();
    initEventSearch();
    initQuicknav();
    updateTitle();
    loadMonthEvents(); // 로그인 사용자의 저장된 학교(있다면)가 반영된 뒤에 조회해야 정확함
    handleDayClick(selectedDateStr); // 페이지를 열자마자 오늘 날짜를 클릭한 것처럼 상세 패널을 바로 연다

    document.getElementById('dayDetailClose').addEventListener('click', closePanel);

    // ── 달력 그리드 ─────────────────────────────────────────────

    function formatLocalDate(date) {
        var y = date.getFullYear();
        var m = String(date.getMonth() + 1).padStart(2, '0');
        var d = String(date.getDate()).padStart(2, '0');
        return y + '-' + m + '-' + d;
    }

    // 해당 연/월의 42칸(6주) 그리드용 날짜 배열 생성 - 앞뒤 달 날짜로 빈 칸을 채운다
    function buildCalendarDays(year, month) {
        var todayStr = formatLocalDate(new Date());
        var firstDay = new Date(year, month - 1, 1);
        var startDow = firstDay.getDay();
        var daysInMonth = new Date(year, month, 0).getDate();
        var prevMonthDays = new Date(year, month - 1, 0).getDate();
        var days = [];

        for (var i = startDow - 1; i >= 0; i--) {
            var d = prevMonthDays - i;
            var pm = month === 1 ? 12 : month - 1;
            var py = month === 1 ? year - 1 : year;
            var date = py + '-' + String(pm).padStart(2, '0') + '-' + String(d).padStart(2, '0');
            days.push({ date: date, day: d, isCurrentMonth: false, isToday: date === todayStr, dow: days.length % 7 });
        }

        for (var day = 1; day <= daysInMonth; day++) {
            var date2 = year + '-' + String(month).padStart(2, '0') + '-' + String(day).padStart(2, '0');
            days.push({ date: date2, day: day, isCurrentMonth: true, isToday: date2 === todayStr, dow: days.length % 7 });
        }

        var remaining = 42 - days.length;
        for (var d2 = 1; d2 <= remaining; d2++) {
            var nm = month === 12 ? 1 : month + 1;
            var ny = month === 12 ? year + 1 : year;
            var date3 = ny + '-' + String(nm).padStart(2, '0') + '-' + String(d2).padStart(2, '0');
            days.push({ date: date3, day: d2, isCurrentMonth: false, isToday: date3 === todayStr, dow: days.length % 7 });
        }

        return days;
    }

    function renderGrid() {
        var days = buildCalendarDays(currentYear, currentMonth);
        calGridBody.innerHTML = '';

        days.forEach(function (day) {
            var cell = document.createElement('div');
            var classes = ['cal-day'];
            if (day.isToday) classes.push('cal-day-today');
            if (day.date === selectedDateStr) classes.push('cal-day-selected');
            cell.className = classes.join(' ');
            cell.dataset.date = day.date;
            cell.dataset.inMonth = day.isCurrentMonth ? '1' : '0'; // 색 충돌 해소 시 "이 달에만 속한 일정인지" 판단용

            var num = document.createElement('span');
            var numClasses = ['cal-day-num'];
            if (!day.isCurrentMonth) {
                numClasses.push('cal-day-num-muted');
            } else if (day.dow === 0) {
                numClasses.push('cal-day-num-sun');
            } else if (day.dow === 6) {
                numClasses.push('cal-day-num-sat');
            }
            num.className = numClasses.join(' ');
            num.textContent = day.day;

            cell.appendChild(num);
            cell.addEventListener('click', function () { handleDayClick(day.date); });
            calGridBody.appendChild(cell);
        });

        applyMonthEventChips(currentMonthEventMap);
    }

    // 현재 학교의 학사일정(시험, 방학, 각종 "OO주간" 등 특정 날짜/기간에 잡힌 일정
    // 전부)을 조회해서 그리드에 걸쳐 있는 해당 날짜 칸마다 작은 배지로 표시한다.
    // 서버가 기간 전체를 이미 걸러서 내려주므로 프론트에서 추가 필터링은 안 함.
    function loadMonthEvents() {
        var params = new URLSearchParams({ year: currentYear, month: currentMonth });
        if (selectedSchool) {
            params.set('atptCode', selectedSchool.officeCode);
            params.set('schoolCode', selectedSchool.schoolCode);
        }

        fetch(`/school/api/calendar-events?${params.toString()}`)
            .then(function (res) { return res.ok ? res.json() : []; })
            .then(function (events) {
                var map = {};
                (events || []).forEach(function (e) {
                    if (!e.eventName) return;
                    if (!map[e.date]) map[e.date] = [];
                    if (map[e.date].indexOf(e.eventName) === -1) map[e.date].push(e.eventName);
                });
                currentMonthEventMap = map;
                applyMonthEventChips(currentMonthEventMap);
            })
            .catch(function () {
                // 부가 표시 기능이라 실패해도 조용히 무시 - 날짜 클릭 시 상세 조회는 별개로 동작함
            });
    }

    // 매주 반복되는 토요휴업일 자체는 표시하면 오히려 매주 눈에 띄어 정작 중요한
    // 방학/시험/행사 기간이 묻히므로 계속 숨긴다. 다만 예전 버그처럼 같은 날의
    // 다른 진짜 일정까지 함께 가려지면 안 되므로, 이제는 이름 하나만 걸러내고
    // 나머지 일정은 그대로 보여준다(아래 applyMonthEventChips 참고).
    var SATURDAY_CLOSURE_NAME = '토요휴업일';

    // 7가지였을 때는 이름이 조금만 많아져도(한 달에 10개 넘는 일정) 해시값이
    // 겹쳐서 서로 다른 일정인데 같은 색으로 보이는 경우가 잦았다 - 팔레트를
    // 16가지로 넓혀서 겹칠 확률을 낮춘다.
    var EVENT_COLOR_PALETTE = [
        { bg: '#ede9fe', fg: '#4f46e5' }, // 인디고
        { bg: '#ffebee', fg: '#e53935' }, // 레드
        { bg: '#fee2e2', fg: '#dc2626' }, // 진한 레드
        { bg: '#d1fae5', fg: '#059669' }, // 그린
        { bg: '#f3e8ff', fg: '#7c3aed' }, // 퍼플
        { bg: '#e0f2fe', fg: '#0284c7' }, // 스카이
        { bg: '#fef3c7', fg: '#b45309' }, // 앰버
        { bg: '#fce7f3', fg: '#db2777' }, // 핑크
        { bg: '#ccfbf1', fg: '#0d9488' }, // 틸
        { bg: '#ffedd5', fg: '#ea580c' }, // 오렌지
        { bg: '#ecfccb', fg: '#65a30d' }, // 라임
        { bg: '#cffafe', fg: '#0891b2' }, // 시안
        { bg: '#ffe4e6', fg: '#e11d48' }, // 로즈
        { bg: '#dbeafe', fg: '#2563eb' }, // 블루
        { bg: '#fef9c3', fg: '#ca8a04' }, // 옐로우
        { bg: '#f1f5f9', fg: '#475569' }  // 슬레이트
    ];

    // 일정명 문자열 자체를 해시해서 팔레트 인덱스를 고정 배정한다 - 등장 순서에
    // 의존하지 않으므로, 같은 이름의 일정이면 몇 월에 나타나든(달을 이동해서
    // 다시 렌더링해도) 항상 같은 색이 된다. 이 "원래 색"은 앞뒤 달로 이어지는
    // 일정에는 절대 바뀌지 않고 그대로 쓰인다(아래 resolveColorOverrides 참고).
    function paletteIndexForName(name) {
        var hash = 0;
        for (var i = 0; i < name.length; i++) {
            hash = (hash * 31 + name.charCodeAt(i)) | 0;
        }
        return Math.abs(hash) % EVENT_COLOR_PALETTE.length;
    }

    function colorForEventName(name) {
        return EVENT_COLOR_PALETTE[paletteIndexForName(name)];
    }

    // 이번 달 화면 안에서 서로 다른 이름인데 해시가 겹쳐 같은 색이 된 일정들을
    // 구분되는 색으로 재배정한다. 단, 앞뒤 달로 이어지는(그리드의 회색 패딩
    // 칸까지 걸쳐 있는) 일정은 절대 재배정하지 않고 항상 이름 해시로 정해지는
    // "원래 색"을 그대로 쓴다 - 안 그러면 이번 달에서만 다른 색으로 바뀌었다가
    // 다음 달로 넘어가서 보면(그 달 기준으로는 겹치는 상대가 없어서) 다시
    // 원래 색으로 돌아와, 같은 일정인데 달을 넘길 때마다 색이 바뀌어 보이게 된다.
    function resolveColorOverrides(cells, visibleNames) {
        var namesInOrder = [];
        var seen = {};
        var touchesPadding = {};

        cells.forEach(function (cell) {
            var inMonth = cell.dataset.inMonth === '1';
            visibleNames(cell.dataset.date).forEach(function (name) {
                if (!seen[name]) {
                    seen[name] = true;
                    namesInOrder.push(name);
                }
                if (!inMonth) touchesPadding[name] = true;
            });
        });

        var usedSlots = {};
        var override = {};

        // 이어지는 일정들의 원래 색 자리를 먼저 전부 예약해서, 재배정 대상(아래)이
        // 그 자리를 새치기하지 못하게 막는다
        namesInOrder.forEach(function (name) {
            if (touchesPadding[name]) {
                usedSlots[paletteIndexForName(name)] = true;
            }
        });

        namesInOrder.forEach(function (name) {
            if (touchesPadding[name]) return; // 이어지는 일정은 항상 원래 색 유지

            var idx = paletteIndexForName(name);
            if (!usedSlots[idx]) {
                usedSlots[idx] = true;
                return; // 겹치는 상대가 없으면 원래 색 그대로
            }

            var next = (idx + 1) % EVENT_COLOR_PALETTE.length;
            while (usedSlots[next] && next !== idx) {
                next = (next + 1) % EVENT_COLOR_PALETTE.length;
            }
            usedSlots[next] = true;
            if (next !== idx) override[name] = EVENT_COLOR_PALETTE[next];
        });

        return override;
    }

    // 같은 이름의 일정이 (같은 주 안에서) 연속된 날짜에 걸쳐 있으면 칸 경계를 넘어
    // 하나의 색깔 띠처럼 이어 보이게 하고, 일정마다 다른 색을 배정한다. 하루에
    // 여러 일정이 겹치면(예: "학부모 상담주"이면서 "학교교육과정설명회"인 날)
    // 띠를 세로로 여러 개 쌓아서 전부 보여준다. 토요휴업일이라는 이름의 일정만
    // 걸러내고, 그 날 다른 진짜 일정이 있으면 그건 그대로 보여준다.
    //
    // 세로 위치(행)는 하루 단위가 아니라 **같은 주 전체를 한 번에 보고** 배정한다.
    // 예전에는 그날의 일정 배열에서 몇 번째인지로만 행이 정해져서, "1회고사"가
    // 끝나는 칸 바로 다음 칸에서 전혀 다른 "현장체험학습"이 시작돼도 둘 다 첫 번째
    // 자리를 차지해 마치 하나의 띠가 이어지는 것처럼 보이는 문제가 있었다(사용자
    // 스크린샷으로 확인). 이제는 서로 다른 일정이 바로 옆 칸에서 맞닿으면 같은
    // 행을 재사용하지 않고 한 칸 아래로 내려서 배정한다 - 최소 한 칸 이상 떨어져
    // 있어야만 같은 행을 다시 쓸 수 있다.
    function applyMonthEventChips(map) {
        var cells = Array.prototype.slice.call(calGridBody.querySelectorAll('.cal-day'));

        cells.forEach(function (cell) {
            var existingBars = Array.prototype.slice.call(
                cell.querySelectorAll('.cal-day-event-chip, .cal-day-event-bar'));
            existingBars.forEach(function (bar) { bar.remove(); });
            cell.style.borderRightColor = '';
        });

        function visibleNames(dateStr) {
            return (map[dateStr] || []).filter(function (name) { return name !== SATURDAY_CLOSURE_NAME; });
        }

        var colorOverride = resolveColorOverrides(cells, visibleNames);
        function colorFor(name) {
            return colorOverride[name] || colorForEventName(name);
        }

        // 이번 주(7칸) 안에서 이름이 연속된 구간을 하나의 "일정 구간"으로 묶는다
        function buildWeekEpisodes(weekCells) {
            var episodes = [];
            var openByName = {}; // 이름 -> 현재 이어지고 있는 구간

            weekCells.forEach(function (cell, col) {
                var namesToday = visibleNames(cell.dataset.date);

                namesToday.forEach(function (name) {
                    if (openByName[name]) {
                        openByName[name].endCol = col;
                    } else {
                        var ep = { name: name, startCol: col, endCol: col };
                        episodes.push(ep);
                        openByName[name] = ep;
                    }
                });

                Object.keys(openByName).forEach(function (name) {
                    if (namesToday.indexOf(name) === -1) delete openByName[name]; // 오늘 끊긴 구간은 닫는다
                });
            });

            return episodes;
        }

        // 구간마다 세로 행(row)을 배정한다. 여러 날짜에 걸친(기간이 긴) 일정을
        // 먼저 배정해서 되도록 위쪽 행을 차지하게 한다 - 그래야 하루짜리 사소한
        // 일정이 그 며칠 전에 먼저 시작했다는 이유만으로 시험기간처럼 여러 날짜에
        // 걸친 일정을 아래로 밀어내는 일이 없다(실사용 사례: "1,2학년 2학기
        // 1회고사"(3일짜리)가 그보다 며칠 전 하루짜리 "대체공휴일" 때문에 아래
        // 행으로 밀리고, 정작 하루짜리인 "현장체험학습"이 위 행을 차지해서
        // 이상해 보이는 문제가 있었음 - 시작일 순서로만 배정하던 이전 방식의
        // 한계). 기간이 같으면(하루짜리끼리 등) 원래 순서(먼저 있던 일정)를
        // 그대로 유지한다.
        //
        // 행을 재사용하려면 그 행에 이미 배정된 모든 구간과 최소 한 칸은 떨어져
        // 있어야 한다(바로 옆 칸이면 다른 일정끼리 이어 보여서 혼동됨). 처리
        // 순서가 시간순이 아니라 기간이 긴 것부터라서, 각 행마다 마지막 구간
        // 하나만이 아니라 배정된 구간 목록 전체와 비교해야 정확하다.
        function assignRows(episodes) {
            var ordered = episodes.map(function (ep, i) { return { ep: ep, i: i }; });
            ordered.sort(function (a, b) {
                var durA = a.ep.endCol - a.ep.startCol;
                var durB = b.ep.endCol - b.ep.startCol;
                if (durA !== durB) return durB - durA; // 긴 구간 먼저
                return a.i - b.i; // 같은 기간이면 원래 순서 유지(안정 정렬)
            });

            var rowIntervals = []; // rowIntervals[row] = 그 행에 배정된 구간들의 [startCol, endCol] 목록
            ordered.forEach(function (item) {
                var ep = item.ep;
                var row = 0;
                while (true) {
                    var intervals = rowIntervals[row];
                    var fits = !intervals || intervals.every(function (iv) {
                        return ep.endCol < iv.startCol - 1 || ep.startCol > iv.endCol + 1;
                    });
                    if (fits) break;
                    row++;
                }
                ep.row = row;
                if (!rowIntervals[row]) rowIntervals[row] = [];
                rowIntervals[row].push({ startCol: ep.startCol, endCol: ep.endCol });
            });
        }

        function renderWeek(weekCells, episodes) {
            var maxRow = -1;
            episodes.forEach(function (ep) { if (ep.row > maxRow) maxRow = ep.row; });

            for (var row = 0; row <= maxRow; row++) {
                weekCells.forEach(function (cell, col) {
                    var ep = episodes.find(function (e) {
                        return e.row === row && col >= e.startCol && col <= e.endCol;
                    });

                    var bar = document.createElement('span');

                    if (!ep) {
                        // 이 행엔 오늘 일정이 없음 - 아래 행들의 세로 위치가 요일마다
                        // 어긋나지 않도록 보이지 않는 자리만 차지하는 빈 칸을 넣는다
                        bar.className = 'cal-day-event-bar cal-day-event-bar-spacer';
                        bar.textContent = ' ';
                        cell.appendChild(bar);
                        return;
                    }

                    var isStart = col === ep.startCol;
                    var isEnd = col === ep.endCol;
                    var color = colorFor(ep.name);

                    bar.className = 'cal-day-event-bar'
                        + (isStart ? ' cal-day-event-bar-start' : '')
                        + (isEnd ? ' cal-day-event-bar-end' : '');
                    bar.style.background = color.bg;
                    bar.style.color = color.fg;
                    bar.title = ep.name;
                    // 연결된 칸들 중 시작 칸에만 이름을 적어서 하나의 띠처럼 읽히게 함
                    bar.textContent = isStart ? ep.name : '';
                    cell.appendChild(bar);

                    if (!isEnd) {
                        cell.style.borderRightColor = 'transparent'; // 이어지는 칸 사이의 경계선을 지워 끊김 없이 보이게 함
                    }
                });
            }
        }

        for (var weekStart = 0; weekStart < cells.length; weekStart += 7) {
            var weekCells = cells.slice(weekStart, weekStart + 7);
            var episodes = buildWeekEpisodes(weekCells);
            assignRows(episodes);
            renderWeek(weekCells, episodes);
        }
    }

    function handleDayClick(dateStr) {
        selectedDateStr = dateStr;
        renderGrid();
        openPanel();
        var formattedDate = dateStr.replace(/-/g, '');
        fetchCalendarDetails(formattedDate, dateStr);
    }

    function openPanel() {
        dayDetailPanel.style.display = 'flex';
        calGridMain.classList.add('cal-grid-main--split');
    }

    function closePanel() {
        dayDetailPanel.style.display = 'none';
        calGridMain.classList.remove('cal-grid-main--split');
        selectedDateStr = null;
        renderGrid();
    }

    // ── 월 빠른 이동 컨트롤 (연/월 드롭다운 + 연/월 단위 이동 버튼) ─────

    function initQuicknav() {
        var yearSelect = document.getElementById('quickYearSelect');
        var monthSelect = document.getElementById('quickMonthSelect');

        var baseYear = new Date().getFullYear();
        for (var y = baseYear - 5; y <= baseYear + 5; y++) {
            var yOpt = document.createElement('option');
            yOpt.value = y;
            yOpt.textContent = y + '년';
            yearSelect.appendChild(yOpt);
        }

        for (var m = 1; m <= 12; m++) {
            var mOpt = document.createElement('option');
            mOpt.value = m;
            mOpt.textContent = m + '월';
            monthSelect.appendChild(mOpt);
        }

        function jumpToSelected() {
            currentYear = parseInt(yearSelect.value, 10);
            currentMonth = parseInt(monthSelect.value, 10);
            renderGrid();
            loadMonthEvents();
        }

        yearSelect.addEventListener('change', jumpToSelected);
        monthSelect.addEventListener('change', jumpToSelected);

        document.getElementById('quickPrevYear').addEventListener('click', function () {
            currentYear -= 1;
            renderGrid();
            syncQuicknav();
            loadMonthEvents();
        });
        document.getElementById('quickNextYear').addEventListener('click', function () {
            currentYear += 1;
            renderGrid();
            syncQuicknav();
            loadMonthEvents();
        });
        document.getElementById('quickPrevMonth').addEventListener('click', function () {
            if (currentMonth === 1) { currentMonth = 12; currentYear -= 1; } else { currentMonth -= 1; }
            renderGrid();
            syncQuicknav();
            loadMonthEvents();
        });
        document.getElementById('quickNextMonth').addEventListener('click', function () {
            if (currentMonth === 12) { currentMonth = 1; currentYear += 1; } else { currentMonth += 1; }
            renderGrid();
            syncQuicknav();
            loadMonthEvents();
        });
        document.getElementById('quickTodayBtn').addEventListener('click', function () {
            var now = new Date();
            currentYear = now.getFullYear();
            currentMonth = now.getMonth() + 1;
            selectedDateStr = formatLocalDate(now);
            renderGrid();
            syncQuicknav();
            loadMonthEvents();
        });

        syncQuicknav();
    }

    // 현재 표시 중인 연/월과 드롭다운 값을 맞춘다
    function syncQuicknav() {
        var yearSelect = document.getElementById('quickYearSelect');
        var monthSelect = document.getElementById('quickMonthSelect');
        if (!yearSelect || !monthSelect) return;

        if (!yearSelect.querySelector('option[value="' + currentYear + '"]')) {
            var yOpt = document.createElement('option');
            yOpt.value = currentYear;
            yOpt.textContent = currentYear + '년';
            yearSelect.appendChild(yOpt);
        }

        yearSelect.value = currentYear;
        monthSelect.value = currentMonth;
    }

    // 타이틀 문구 변경 (아이콘은 건드리지 않고 텍스트만 갱신 -> 아이콘이 바뀌어 보이는 현상 방지)
    function updateTitle() {
        var grade = document.getElementById('gradeSelect').value;
        var classNm = document.getElementById('classSelect').value;
        var schoolPrefix = selectedSchool ? selectedSchool.schoolName + ' ' : '';
        document.getElementById('calendarTitleText').textContent = `${schoolPrefix}${grade}학년 ${classNm}반 시간표 캘린더`;
    }

    // 학교가 선택되어 있으면 실제 반 목록으로 갱신 (선택된 값은 최대한 유지)
    function refreshClassOptions(preserveValue) {
        if (!selectedSchool) return; // 학교 미선택 시 기본 1~20반 목록 유지
        loadClassOptions({
            atptCode: selectedSchool.officeCode,
            schoolCode: selectedSchool.schoolCode,
            grade: gradeSelect.value,
            selectEl: classSelect,
            placeholder: '반 선택',
            preserveValue: preserveValue,
            onLoaded: updateTitle
        });
    }

    // 0. 로그인한 사용자가 마이페이지(=회원가입 시 입력한)에 등록해 둔 학교/학년/반을 캘린더에 자동 반영
    function applyMySchoolIfAvailable() {
        var mySchool = window.__USER_SCHOOL__;
        if (!mySchool || !mySchool.schoolCode) return;

        selectedSchool = {
            schoolName: mySchool.schoolName,
            schoolCode: mySchool.schoolCode,
            officeCode: mySchool.atptCode,
            schoolKind: mySchool.schoolKind
        };
        document.getElementById('schoolSearchInput').value = mySchool.schoolName;

        if (typeof buildGradeOptions === 'function') {
            buildGradeOptions(mySchool.schoolKind, gradeSelect, mySchool.grade);
        } else if (mySchool.grade) {
            gradeSelect.value = mySchool.grade;
        }
        refreshClassOptions(mySchool.classNum);
    }

    // 2. 학교 검색 (공용 위젯 사용)
    function initSchoolSearch() {
        initSchoolSearchWidget({
            input: document.getElementById('schoolSearchInput'),
            resultsBox: document.getElementById('schoolSearchResults'),
            wrap: document.getElementById('schoolSearchWrap'),
            onSelect: function (school) {
                selectedSchool = school;
                if (typeof buildGradeOptions === 'function') {
                    buildGradeOptions(school.schoolKind, gradeSelect);
                }
                updateTitle();
                refreshClassOptions();
                loadMonthEvents();
            },
            onClear: function () {
                selectedSchool = null;
                if (typeof buildGradeOptions === 'function') {
                    buildGradeOptions('', gradeSelect);
                }
                fillClassSelect(classSelect, '반 선택', fallbackClassList());
                updateTitle();
                loadMonthEvents();
            }
        });
    }

    // 2-1. 일정 이름 검색 - "기말고사"처럼 해마다 반복되는 이름을 검색하면 결과가
    // 여러 해에 걸쳐 나올 수 있어 헷갈리므로, 서버가 오늘과 가장 가까운 단 하나만
    // 찾아서 돌려준다(/api/calendar-events/search). 찾으면 그 날짜가 있는 달로
    // 캘린더를 이동시키고 상세 패널까지 자동으로 연다.
    function initEventSearch() {
        var form = document.getElementById('eventSearchForm');
        if (!form) return;

        var input = document.getElementById('eventSearchInput');
        var status = document.getElementById('eventSearchStatus');

        function showStatus(text, ok) {
            status.textContent = text;
            status.className = 'event-search-status ' + (ok ? 'event-search-status-ok' : 'event-search-status-error');
            status.style.display = 'block';
        }

        form.addEventListener('submit', function (e) {
            e.preventDefault();
            var keyword = input.value.trim();
            if (!keyword) return;

            if (!selectedSchool) {
                showStatus('먼저 학교를 검색해서 선택해주세요.', false);
                return;
            }

            var params = new URLSearchParams({
                atptCode: selectedSchool.officeCode,
                schoolCode: selectedSchool.schoolCode,
                keyword: keyword
            });

            fetch(`/school/api/calendar-events/search?${params.toString()}`)
                .then(function (res) {
                    if (res.status === 404) return null;
                    if (!res.ok) throw new Error('검색 실패');
                    return res.json();
                })
                .then(function (event) {
                    if (!event) {
                        showStatus(`'${keyword}'과(와) 일치하는 일정을 찾지 못했어요.`, false);
                        return;
                    }

                    var dateParts = event.date.split('-');
                    currentYear = parseInt(dateParts[0], 10);
                    currentMonth = parseInt(dateParts[1], 10);
                    syncQuicknav();
                    loadMonthEvents();
                    handleDayClick(event.date);

                    showStatus(`오늘과 가장 가까운 '${event.eventName}' 일정으로 이동했어요 (${event.date}).`, true);
                })
                .catch(function () {
                    showStatus('검색 중 오류가 발생했습니다.', false);
                });
        });
    }

    // 3-1. 날짜별 한마디 댓글: 조회/작성/수정/삭제 (같은 학년·같은 반끼리만 공유)
    function commentParams() {
        var params = new URLSearchParams({
            date: currentCommentDate,
            grade: currentCommentGrade,
            classNm: currentCommentClassNm
        });
        if (selectedSchool) {
            params.set('atptCode', selectedSchool.officeCode);
            params.set('schoolCode', selectedSchool.schoolCode);
        }
        return params;
    }

    function escapeHtml(str) {
        var div = document.createElement('div');
        div.textContent = str == null ? '' : str;
        return div.innerHTML;
    }

    function loadComments() {
        var commentList = document.getElementById('commentList');
        commentList.innerHTML = '<div class="comment-empty">불러오는 중...</div>';

        fetch(`/school/api/comments?${commentParams().toString()}`)
            .then(function (res) { return res.json(); })
            .then(renderComments)
            .catch(function () {
                commentList.innerHTML = '<div class="comment-empty">댓글을 불러오지 못했습니다.</div>';
            });
    }

    function renderComments(comments) {
        var commentList = document.getElementById('commentList');

        if (!comments || comments.length === 0) {
            commentList.innerHTML = '<div class="comment-empty">아직 댓글이 없어요. 첫 댓글을 남겨보세요!</div>';
            return;
        }

        commentList.innerHTML = '';
        comments.forEach(function (c) {
            var item = document.createElement('div');
            item.className = 'comment-item' + (c.blind ? ' comment-item-blind' : '');

            var editedBadge = c.edited ? ' <span class="comment-edited">(수정됨)</span>' : '';
            var blindBadge = c.blind ? ' <span class="comment-blind-badge">블라인드</span>' : '';
            var nicknameHtml = c.authorLinkable
                ? '<a href="/users/' + c.authorId + '" class="comment-nickname">' + escapeHtml(c.nickname) + '</a>'
                : '<span class="comment-nickname">' + escapeHtml(c.nickname) + '</span>';
            var actionsHtml = c.mine
                ? `<button type="button" class="comment-edit-btn" title="수정"><i class="fa-solid fa-pen"></i></button>
                   <button type="button" class="comment-delete-btn" title="삭제"><i class="fa-solid fa-xmark"></i></button>`
                : (c.reportedByMe
                    ? '<button type="button" class="comment-report-btn" title="이미 신고했어요" disabled><i class="fa-solid fa-flag"></i></button>'
                    : '<button type="button" class="comment-report-btn" title="신고"><i class="fa-solid fa-flag"></i></button>');
            var likeBookmarkHtml = `
                <button type="button" class="comment-like-btn${c.likedByMe ? ' active' : ''}" title="좋아요">
                    <i class="fa-solid fa-heart"></i> <span class="comment-like-count">${c.likeCount}</span>
                </button>
                <button type="button" class="comment-bookmark-btn${c.bookmarkedByMe ? ' active' : ''}" title="북마크">
                    <i class="fa-solid fa-bookmark"></i>
                </button>`;

            item.innerHTML = `
                <div class="comment-item-header">
                    ${nicknameHtml}
                    <span class="comment-time"><span class="comment-time-value">${escapeHtml(c.createdAt)}</span>${editedBadge}${blindBadge}</span>
                    ${likeBookmarkHtml}
                    ${actionsHtml}
                </div>
                <div class="comment-content">${escapeHtml(c.content)}</div>
            `;

            WebSchoolTimeago.apply(item.querySelector('.comment-time-value'));

            var editBtn = item.querySelector('.comment-edit-btn');
            if (editBtn) {
                editBtn.addEventListener('click', function () { startEditComment(item, c); });
            }

            var delBtn = item.querySelector('.comment-delete-btn');
            if (delBtn) {
                delBtn.addEventListener('click', function () { deleteComment(c.id); });
            }

            var reportBtn = item.querySelector('.comment-report-btn');
            if (reportBtn) {
                reportBtn.addEventListener('click', function () { reportComment(c.id, reportBtn); });
            }

            var likeBtn = item.querySelector('.comment-like-btn');
            if (likeBtn) {
                likeBtn.addEventListener('click', function () { toggleCommentLike(c.id, likeBtn); });
            }

            var bookmarkBtn = item.querySelector('.comment-bookmark-btn');
            if (bookmarkBtn) {
                bookmarkBtn.addEventListener('click', function () { toggleCommentBookmark(c.id, bookmarkBtn); });
            }

            commentList.appendChild(item);
        });
    }

    function startEditComment(item, c) {
        var contentEl = item.querySelector('.comment-content');
        contentEl.innerHTML = `
            <form class="comment-edit-form">
                <input type="text" class="comment-edit-input" maxlength="300" value="${escapeHtml(c.content)}">
                <button type="submit">저장</button>
                <button type="button" class="comment-edit-cancel">취소</button>
            </form>
        `;

        var input = contentEl.querySelector('.comment-edit-input');
        input.focus();
        input.setSelectionRange(input.value.length, input.value.length);

        contentEl.querySelector('.comment-edit-cancel').addEventListener('click', function () {
            loadComments();
        });

        contentEl.querySelector('.comment-edit-form').addEventListener('submit', function (e) {
            e.preventDefault();
            var newContent = input.value.trim();
            if (!newContent) return;

            fetch('/school/api/comments/' + c.id, {
                method: 'PUT',
                headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                body: 'content=' + encodeURIComponent(newContent)
            })
                .then(function (res) {
                    if (!res.ok) return res.json().then(function (body) { throw new Error(body.error || '수정 실패'); });
                    loadComments();
                })
                .catch(function (err) {
                    alert(err.message || '댓글 수정에 실패했습니다.');
                });
        });
    }

    function deleteComment(id) {
        if (!confirm('댓글을 삭제할까요?')) return;

        fetch('/school/api/comments/' + id, { method: 'DELETE' })
            .then(function (res) {
                if (!res.ok) throw new Error('삭제 실패');
                loadComments();
            })
            .catch(function () {
                alert('댓글 삭제에 실패했습니다.');
            });
    }

    function reportComment(id, btn) {
        if (!confirm('이 한마디를 신고하시겠어요?')) return;
        var reason = prompt('신고 사유를 입력해주세요 (선택 사항입니다. 비워두고 확인해도 신고가 접수됩니다).', '') || '';

        fetch('/school/api/comments/' + id + '/report', {
            method: 'POST',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
            body: 'reason=' + encodeURIComponent(reason)
        })
            .then(function (res) {
                return res.json().then(function (body) {
                    if (!res.ok) throw new Error(body.error || '신고 처리에 실패했습니다.');
                    return body;
                });
            })
            .then(function (body) {
                alert(body.blind ? '신고가 접수되었습니다. 신고 누적으로 한마디가 블라인드 처리되었습니다.' : '신고가 접수되었습니다.');
                loadComments();
            })
            .catch(function (err) {
                alert(err.message || '신고 처리에 실패했습니다.');
                if (btn) {
                    btn.disabled = true;
                }
            });
    }

    function toggleCommentLike(id, btn) {
        fetch('/school/api/comments/' + id + '/like', { method: 'POST' })
            .then(function (res) {
                if (!res.ok) throw new Error('처리 실패');
                return res.json();
            })
            .then(function (body) {
                btn.classList.toggle('active', body.liked);
                btn.querySelector('.comment-like-count').textContent = body.likeCount;
            })
            .catch(function () {
                alert('좋아요 처리에 실패했습니다.');
            });
    }

    function toggleCommentBookmark(id, btn) {
        fetch('/school/api/comments/' + id + '/bookmark', { method: 'POST' })
            .then(function (res) {
                if (!res.ok) throw new Error('처리 실패');
                return res.json();
            })
            .then(function (body) {
                btn.classList.toggle('active', body.bookmarked);
            })
            .catch(function () {
                alert('북마크 처리에 실패했습니다.');
            });
    }

    document.getElementById('commentForm').addEventListener('submit', function (e) {
        e.preventDefault();
        var input = document.getElementById('commentInput');
        var content = input.value.trim();
        if (!content) return;

        fetch(`/school/api/comments?${commentParams().toString()}`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
            body: 'content=' + encodeURIComponent(content)
        })
            .then(function (res) {
                if (!res.ok) return res.json().then(function (body) { throw new Error(body.error || '등록 실패'); });
                return res.json();
            })
            .then(function () {
                input.value = '';
                loadComments();
            })
            .catch(function (err) {
                alert(err.message || '댓글 등록에 실패했습니다.');
            });
    });

    // 3. 백엔드 REST API 호출 및 상세 패널 바인딩 함수
    function fetchCalendarDetails(formattedDate, displayDate) {
        var grade = document.getElementById('gradeSelect').value;
        var classNm = document.getElementById('classSelect').value;

        currentCommentDate = formattedDate;
        currentCommentGrade = grade;
        currentCommentClassNm = classNm;
        document.getElementById('commentInput').value = '';

        var dateParts = displayDate.split('-');
        var dateObj = new Date(displayDate + 'T12:00:00');
        var dow = dateObj.getDay();
        var shortDate = `${parseInt(dateParts[1], 10)}월 ${parseInt(dateParts[2], 10)}일 (${DOW_KO[dow]})`;

        var dateEl = document.getElementById('dayDetailDate');
        dateEl.textContent = shortDate;
        dateEl.className = 'day-detail-date' + (dow === 0 ? ' day-detail-date-sun' : dow === 6 ? ' day-detail-date-sat' : '');
        document.getElementById('dayDetailClass').textContent = `${grade}학년 ${classNm}반`;

        document.getElementById('mealContent').textContent = '급식 정보를 불러오는 중...';
        document.getElementById('timetableList').innerHTML = '';
        document.getElementById('eventBadge').style.display = 'none';

        var params = new URLSearchParams({ date: formattedDate, grade: grade, classNm: classNm });
        if (selectedSchool) {
            params.set('atptCode', selectedSchool.officeCode);
            params.set('schoolCode', selectedSchool.schoolCode);
        }

        var url = `/school/api/calendar-details?${params.toString()}`;

        fetch(url)
            .then(response => {
                if (!response.ok) {
                    throw new Error(`HTTP 에러! 상태 코드: ${response.status}`);
                }
                return response.json();
            })
            .then(data => {
                var eventBadge = document.getElementById('eventBadge');
                if (data.eventName) {
                    eventBadge.innerHTML = `<i class="fa-solid fa-bell"></i> 학사일정: ${escapeHtml(data.eventName)}`;
                    eventBadge.style.display = 'flex';
                } else {
                    eventBadge.style.display = 'none';
                }

                var isSpecialDay = false;
                var specialTitle = "";

                if (data.timetable && data.timetable.length > 0) {
                    var firstSubject = data.timetable[0].subject;
                    var allSame = data.timetable.every(item => item.subject === firstSubject);

                    if (allSame && (firstSubject.includes("방학") || firstSubject.includes("휴업") || firstSubject.includes("재량") || firstSubject.length <= 6)) {
                        isSpecialDay = true;
                        specialTitle = firstSubject;
                    }
                }

                var normalSection = document.getElementById('normalContentSection');
                var specialSection = document.getElementById('specialContentSection');

                if (isSpecialDay) {
                    if (normalSection) normalSection.style.display = 'none';

                    specialSection.innerHTML = `
                        <div class="special-day-card">
                            <div class="special-icon">🏫</div>
                            <h3>${escapeHtml(specialTitle)}</h3>
                            <p>오늘은 정규 수업 및 급식이 진행되지 않는 날입니다.</p>
                        </div>
                    `;
                    specialSection.style.display = 'block';

                } else {
                    if (specialSection) specialSection.style.display = 'none';
                    if (normalSection) normalSection.style.display = 'block';

                    var mealContent = document.getElementById('mealContent');
                    if (data.meal && data.meal.trim() !== "" && data.meal !== "등록된 급식 정보가 없습니다.") {
                        mealContent.innerText = data.meal;
                    } else {
                        mealContent.innerText = "등록된 급식 정보가 없습니다.";
                    }

                    var timetableList = document.getElementById('timetableList');
                    timetableList.innerHTML = '';

                    if (!data.timetable || data.timetable.length === 0) {
                        timetableList.innerHTML = '<div class="timetable-empty">등록된 시간표가 없습니다.</div>';
                    } else {
                        data.timetable.forEach(item => {
                            var row = document.createElement('div');
                            row.className = 'timetable-row';
                            row.innerHTML = `
                                <span class="timetable-perio">${escapeHtml(String(item.perio).replace('교시', ''))}</span>
                                <span class="timetable-subject">${escapeHtml(item.subject)}</span>
                            `;
                            timetableList.appendChild(row);
                        });
                    }
                }

                loadComments();
            })
            .catch(error => {
                console.error('상세 에러 로그:', error);
                alert('정보를 불러오는 중 오류가 발생했습니다.');
            });
    }
});
