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

    document.addEventListener('DOMContentLoaded', initPollEditor);
})();
