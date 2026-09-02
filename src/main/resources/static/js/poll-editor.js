// 게시글/한마디 작성 폼의 설문 첨부 섹션 - 체크박스로 펼치기, 옵션 동적 추가(최대 10개), 공개범위
// 선택에 따라 "같은 학교 안에서만" 옵션 노출 여부 전환. 별도 저장 API를 호출하지 않고 폼 자체
// 필드로 게시글/한마디와 함께 그대로 제출된다(서버가 PollCreateRequest로 조립).
(function () {
    var MAX_OPTIONS = 10;

    function initPollEditor() {
        var toggle = document.getElementById('pollToggle');
        var fields = document.getElementById('pollFields');
        var optionList = document.getElementById('pollOptionList');
        var addBtn = document.getElementById('pollAddOptionBtn');
        var scopeRadios = document.querySelectorAll('input[name="pollVisibilityScope"]');
        var sameSchoolWrap = document.getElementById('pollSameSchoolOnlyWrap');
        var advancedToggle = document.getElementById('pollAdvancedToggle');
        var advancedFields = document.getElementById('pollAdvancedFields');

        if (!toggle || !fields) {
            return;
        }

        toggle.addEventListener('change', function () {
            fields.hidden = !toggle.checked;
        });

        // 질문/옵션은 필수라 항상 보이고, 복수선택·공개범위 같은 세부 설정만 따로 접어둔다 -
        // 켰다 껐다 자주 안 쓰는 옵션들이라 기본은 접힌 채로 시작한다.
        if (advancedToggle && advancedFields) {
            advancedToggle.addEventListener('click', function () {
                var expanded = advancedToggle.getAttribute('aria-expanded') === 'true';
                advancedToggle.setAttribute('aria-expanded', String(!expanded));
                advancedFields.hidden = expanded;
            });
        }

        function refreshScope() {
            var checked = document.querySelector('input[name="pollVisibilityScope"]:checked');
            if (sameSchoolWrap) {
                sameSchoolWrap.hidden = !checked || checked.value !== 'SAME_GRADE';
            }
        }
        scopeRadios.forEach(function (radio) {
            radio.addEventListener('change', refreshScope);
        });
        refreshScope();

        initPollDeadlinePicker();

        if (addBtn && optionList) {
            addBtn.addEventListener('click', function () {
                var rows = optionList.querySelectorAll('.post-poll-option-row');
                if (rows.length >= MAX_OPTIONS) {
                    return;
                }
                var row = document.createElement('div');
                row.className = 'post-poll-option-row';

                var input = document.createElement('input');
                input.type = 'text';
                input.name = 'pollOptions';
                input.className = 'post-poll-option-input';
                input.maxLength = 100;
                input.placeholder = '옵션 ' + (rows.length + 1);
                row.appendChild(input);

                var removeBtn = document.createElement('button');
                removeBtn.type = 'button';
                removeBtn.className = 'post-poll-option-remove';
                removeBtn.setAttribute('aria-label', '옵션 삭제');
                removeBtn.textContent = '×';
                removeBtn.addEventListener('click', function () {
                    row.remove();
                });
                row.appendChild(removeBtn);

                optionList.appendChild(row);
            });
        }
    }

    // 마감 기한 - 순정 <input type="datetime-local"> 하나만 있던 걸 디스코드/카카오톡 스타일
    // 팝오버(자주 쓰는 기한 버튼 + 직접 지정)로 교체(사용자 요청). 실제 제출값은 예전과 동일한
    // datetime-local 포맷 문자열(예: "2026-09-07T20:00")로 #pollExpiresAt hidden input에
    // 모아서 서버(PollService.parseExpiresAt() - LocalDateTime.parse 그대로 사용) 쪽은 손대지
    // 않아도 되게 했다.
    function initPollDeadlinePicker() {
        var trigger = document.getElementById('pollDeadlineTrigger');
        var panel = document.getElementById('pollDeadlinePanel');
        var hidden = document.getElementById('pollExpiresAt');
        var triggerText = document.getElementById('pollDeadlineTriggerText');
        var dateInput = document.getElementById('pollDeadlineDate');
        var timeInput = document.getElementById('pollDeadlineTime');
        var clearBtn = document.getElementById('pollDeadlineClear');
        var applyBtn = document.getElementById('pollDeadlineApply');

        if (!trigger || !panel || !hidden) {
            return;
        }

        function pad(n) { return String(n).length < 2 ? '0' + n : String(n); }

        function toHiddenValue(date) {
            return date.getFullYear() + '-' + pad(date.getMonth() + 1) + '-' + pad(date.getDate())
                + 'T' + pad(date.getHours()) + ':' + pad(date.getMinutes());
        }

        var displayFormat = (typeof Intl !== 'undefined')
            ? new Intl.DateTimeFormat('ko-KR', { month: 'long', day: 'numeric', hour: '2-digit', minute: '2-digit', hour12: false })
            : null;

        function setValue(date) {
            if (!date) {
                hidden.value = '';
                triggerText.textContent = '설정 안 함';
                dateInput.value = '';
                timeInput.value = '';
                return;
            }
            hidden.value = toHiddenValue(date);
            triggerText.textContent = (displayFormat ? displayFormat.format(date) : toHiddenValue(date)) + ' 마감';
            dateInput.value = date.getFullYear() + '-' + pad(date.getMonth() + 1) + '-' + pad(date.getDate());
            timeInput.value = pad(date.getHours()) + ':' + pad(date.getMinutes());
        }

        function onDocClick(e) {
            if (!panel.contains(e.target) && e.target !== trigger) {
                closePanel();
            }
        }

        function openPanel() {
            panel.hidden = false;
            trigger.setAttribute('aria-expanded', 'true');
            // 팝오버를 여는 이 클릭 자체가 바로 닫히지 않도록 다음 이벤트 루프부터 리스닝한다
            // (rich-editor.js의 첨부 팝오버와 동일한 패턴).
            setTimeout(function () { document.addEventListener('click', onDocClick); }, 0);
        }

        function closePanel() {
            panel.hidden = true;
            trigger.setAttribute('aria-expanded', 'false');
            document.removeEventListener('click', onDocClick);
        }

        trigger.addEventListener('click', function (e) {
            e.stopPropagation();
            if (panel.hidden) {
                openPanel();
            } else {
                closePanel();
            }
        });

        var PRESET_OFFSETS = {
            '1h': function (now) { return new Date(now.getTime() + 60 * 60 * 1000); },
            'tonight': function (now) { return new Date(now.getFullYear(), now.getMonth(), now.getDate() + 1, 0, 0, 0); },
            'tomorrow': function (now) { return new Date(now.getTime() + 24 * 60 * 60 * 1000); },
            '1w': function (now) { return new Date(now.getTime() + 7 * 24 * 60 * 60 * 1000); }
        };

        panel.querySelectorAll('.poll-deadline-preset').forEach(function (btn) {
            btn.addEventListener('click', function () {
                var compute = PRESET_OFFSETS[btn.dataset.preset];
                if (!compute) return;
                setValue(compute(new Date()));
                closePanel();
            });
        });

        if (clearBtn) {
            clearBtn.addEventListener('click', function () {
                setValue(null);
                closePanel();
            });
        }

        if (applyBtn) {
            applyBtn.addEventListener('click', function () {
                if (!dateInput.value) {
                    setValue(null);
                    closePanel();
                    return;
                }
                var dateParts = dateInput.value.split('-').map(Number);
                var timeParts = (timeInput.value || '00:00').split(':').map(Number);
                setValue(new Date(dateParts[0], dateParts[1] - 1, dateParts[2], timeParts[0], timeParts[1]));
                closePanel();
            });
        }
    }

    document.addEventListener('DOMContentLoaded', initPollEditor);
})();
