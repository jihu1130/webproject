document.addEventListener('DOMContentLoaded', function () {
    var calendarEl = document.getElementById('calendar');

    var selectedSchool = null; // { schoolName, schoolCode, officeCode, schoolKind }
    var selectedDateStr = null; // 방금 클릭한(선택된) 날짜 ('YYYY-MM-DD')
    var currentCommentDate = null; // 현재 모달에 열려있는 날짜 ('YYYYMMDD', 댓글 API용)
    var currentCommentGrade = null; // 댓글이 공유되는 학년
    var currentCommentClassNm = null; // 댓글이 공유되는 반

    // 1. FullCalendar 달력 초기화
    var calendar = new FullCalendar.Calendar(calendarEl, {
        initialView: 'dayGridMonth',
        locale: 'ko',
        headerToolbar: {
            left: '',
            center: 'title',
            right: ''
        },
        dateClick: function (info) {
            selectedDateStr = info.dateStr;
            applySelectedDayStyle();
            var selectedDate = info.dateStr.replace(/-/g, '');
            fetchCalendarDetails(selectedDate, info.dateStr);
        },
        datesSet: function () {
            syncQuicknav();
            applySelectedDayStyle();
        }
    });

    calendar.render();

    // 아직 아무 날짜도 클릭하지 않았다면 오늘이 기본 선택 상태
    selectedDateStr = formatLocalDate(new Date());
    applySelectedDayStyle();

    var gradeSelect = document.getElementById('gradeSelect');
    var classSelect = document.getElementById('classSelect');

    gradeSelect.addEventListener('change', function () {
        updateTitle();
        refreshClassOptions();
    });
    classSelect.addEventListener('change', updateTitle);

    applyMySchoolIfAvailable();
    initSchoolSearch();
    initQuicknav();
    updateTitle();

    // 방금 클릭한 날짜(없으면 오늘)에 선택 표시를 적용
    function applySelectedDayStyle() {
        var prevSelected = calendarEl.querySelectorAll('.fc-day-selected');
        prevSelected.forEach(function (el) { el.classList.remove('fc-day-selected'); });

        if (!selectedDateStr) return;
        var cell = calendarEl.querySelector('.fc-daygrid-day[data-date="' + selectedDateStr + '"]');
        if (cell) cell.classList.add('fc-day-selected');
    }

    function formatLocalDate(date) {
        var y = date.getFullYear();
        var m = String(date.getMonth() + 1).padStart(2, '0');
        var d = String(date.getDate()).padStart(2, '0');
        return y + '-' + m + '-' + d;
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

    // 4. 월 빠른 이동 컨트롤 (연/월 드롭다운 + 연/월 단위 이동 버튼)
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
            var year = parseInt(yearSelect.value, 10);
            var month = parseInt(monthSelect.value, 10);
            calendar.gotoDate(new Date(year, month - 1, 1));
        }

        yearSelect.addEventListener('change', jumpToSelected);
        monthSelect.addEventListener('change', jumpToSelected);

        document.getElementById('quickPrevYear').addEventListener('click', function () {
            calendar.incrementDate({ years: -1 });
        });
        document.getElementById('quickNextYear').addEventListener('click', function () {
            calendar.incrementDate({ years: 1 });
        });
        document.getElementById('quickPrevMonth').addEventListener('click', function () {
            calendar.incrementDate({ months: -1 });
        });
        document.getElementById('quickNextMonth').addEventListener('click', function () {
            calendar.incrementDate({ months: 1 });
        });
        document.getElementById('quickTodayBtn').addEventListener('click', function () {
            calendar.today();
            selectedDateStr = formatLocalDate(new Date());
            applySelectedDayStyle();
        });

        syncQuicknav();
    }

    // FullCalendar가 표시 중인 연/월과 드롭다운 값을 맞춘다
    function syncQuicknav() {
        var yearSelect = document.getElementById('quickYearSelect');
        var monthSelect = document.getElementById('quickMonthSelect');
        if (!yearSelect || !monthSelect) return;

        var current = calendar.getDate();
        var year = current.getFullYear();
        var month = current.getMonth() + 1;

        if (!yearSelect.querySelector('option[value="' + year + '"]')) {
            var yOpt = document.createElement('option');
            yOpt.value = year;
            yOpt.textContent = year + '년';
            yearSelect.appendChild(yOpt);
        }

        yearSelect.value = year;
        monthSelect.value = month;
    }

    // 타이틀 문구 변경 (아이콘은 건드리지 않고 텍스트만 갱신 -> 아이콘이 바뀌어 보이는 현상 방지)
    function updateTitle() {
        var grade = document.getElementById('gradeSelect').value;
        var classNm = document.getElementById('classSelect').value;
        var schoolPrefix = selectedSchool ? selectedSchool.schoolName + ' ' : '';
        document.getElementById('calendarTitleText').textContent = `${schoolPrefix}${grade}학년 ${classNm}반 시간표 캘린더`;
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
            },
            onClear: function () {
                selectedSchool = null;
                if (typeof buildGradeOptions === 'function') {
                    buildGradeOptions('', gradeSelect);
                }
                fillClassSelect(classSelect, '반 선택', fallbackClassList());
                updateTitle();
            }
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

    // 3. 백엔드 REST API 호출 및 모달 바인딩 함수
    function fetchCalendarDetails(formattedDate, displayDate) {
        var grade = document.getElementById('gradeSelect').value;
        var classNm = document.getElementById('classSelect').value;

        currentCommentDate = formattedDate;
        currentCommentGrade = grade;
        currentCommentClassNm = classNm;
        document.getElementById('commentInput').value = '';

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
                var dateParts = displayDate.split('-');
                var shortDate = `${parseInt(dateParts[1], 10)}월 ${parseInt(dateParts[2], 10)}일`;
                document.getElementById('modalTitle').innerText = `${shortDate} (${grade}-${classNm})`;

                var eventBadge = document.getElementById('eventBadge');
                if (data.eventName) {
                    eventBadge.innerText = `🔔 학사일정: ${data.eventName}`;
                    eventBadge.style.display = 'block';
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
                            <h3>${specialTitle}</h3>
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

                    var tbody = document.getElementById('timetableBody');
                    tbody.innerHTML = '';

                    if (!data.timetable || data.timetable.length === 0) {
                        tbody.innerHTML = '<tr><td colspan="2">등록된 시간표가 없습니다.</td></tr>';
                    } else {
                        data.timetable.forEach(item => {
                            var row = `<tr>
                                <td>${item.perio}</td>
                                <td>${item.subject}</td>
                            </tr>`;
                            tbody.innerHTML += row;
                        });
                    }
                }

                document.getElementById('timetableModal').style.display = 'flex';
                loadComments();
            })
            .catch(error => {
                console.error('상세 에러 로그:', error);
                alert('정보를 불러오는 중 오류가 발생했습니다.');
            });
    }
});

// 모달 닫기 함수
function closeModal() {
    document.getElementById('timetableModal').style.display = 'none';
}
