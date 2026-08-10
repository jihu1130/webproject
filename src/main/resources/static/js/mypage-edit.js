var USERNAME_PATTERN = /^[A-Za-z0-9]+$/;

document.addEventListener('DOMContentLoaded', function () {
    var usernameInput = document.getElementById('username');
    var checkBtn = document.getElementById('checkUsernameBtn');
    var msgEl = document.getElementById('usernameCheckMsg');
    var form = document.getElementById('mypageEditForm');
    var passwordInput = document.getElementById('newPassword');
    var confirmInput = document.getElementById('confirmNewPassword');
    var passwordCheckMsg = document.getElementById('passwordCheckMsg');

    if (!usernameInput || !checkBtn || !msgEl || !form) return;

    var originalUsername = usernameInput.value.trim();
    var checkedValue = null;
    var isAvailable = false;

    function showMessage(text, type) {
        msgEl.textContent = text;
        msgEl.className = 'auth-check-msg' + (type ? ' ' + type : '');
    }

    function resetCheckState() {
        checkedValue = null;
        isAvailable = false;
        showMessage('', '');
    }

    usernameInput.addEventListener('input', resetCheckState);

    checkBtn.addEventListener('click', function () {
        var username = usernameInput.value.trim();
        if (!username) {
            showMessage('아이디를 입력해주세요.', 'error');
            usernameInput.focus();
            return;
        }

        if (username === originalUsername) {
            checkedValue = username;
            isAvailable = true;
            showMessage('현재 사용 중인 아이디입니다.', 'success');
            return;
        }

        if (!USERNAME_PATTERN.test(username)) {
            showMessage('아이디는 영문과 숫자만 사용할 수 있습니다.', 'error');
            usernameInput.focus();
            return;
        }

        checkBtn.disabled = true;
        showMessage('확인 중...', 'pending');

        fetch('/api/users/check-username?username=' + encodeURIComponent(username))
            .then(function (res) { return res.json(); })
            .then(function (data) {
                checkedValue = username;
                isAvailable = !!data.available;
                showMessage(data.message, isAvailable ? 'success' : 'error');
            })
            .catch(function () {
                checkedValue = null;
                isAvailable = false;
                showMessage('중복 확인 중 오류가 발생했습니다. 다시 시도해주세요.', 'error');
            })
            .finally(function () {
                checkBtn.disabled = false;
            });
    });

    function checkPasswordMatch() {
        if (!passwordInput || !confirmInput || !passwordCheckMsg) return true;

        if (!passwordInput.value && !confirmInput.value) {
            passwordCheckMsg.textContent = '';
            passwordCheckMsg.className = 'auth-check-msg';
            return true; // 비밀번호를 변경하지 않는 경우 통과
        }

        var match = passwordInput.value.length > 0 && passwordInput.value === confirmInput.value;
        passwordCheckMsg.textContent = match ? '비밀번호가 일치합니다.' : '비밀번호가 일치하지 않습니다.';
        passwordCheckMsg.className = 'auth-check-msg' + (match ? ' success' : ' error');
        return match;
    }

    if (passwordInput && confirmInput) {
        passwordInput.addEventListener('input', checkPasswordMatch);
        confirmInput.addEventListener('input', checkPasswordMatch);
    }

    // 학교 검색
    var schoolSearchInput = document.getElementById('editSchoolSearchInput');
    var schoolSearchResults = document.getElementById('editSchoolSearchResults');
    var schoolSearchWrap = document.getElementById('editSchoolSearchWrap');
    var schoolNameHidden = document.getElementById('schoolName');
    var schoolCodeHidden = document.getElementById('schoolCode');
    var atptCodeHidden = document.getElementById('atptCode');
    var schoolKindHidden = document.getElementById('schoolKind');
    var gradeSelect = document.getElementById('grade');
    var classSelect = document.getElementById('classNum');
    var originalClassNum = classSelect ? classSelect.value : '';

    var currentSchoolCodes = (schoolCodeHidden && schoolCodeHidden.value && atptCodeHidden && atptCodeHidden.value)
        ? { atptCode: atptCodeHidden.value, schoolCode: schoolCodeHidden.value }
        : null;

    function refreshClassOptions(preserveValue) {
        if (!classSelect) return;
        if (!currentSchoolCodes || !gradeSelect.value) {
            classSelect.innerHTML = '<option value="" selected>학교와 학년을 먼저 선택하세요</option>';
            classSelect.disabled = true;
            return;
        }
        loadClassOptions({
            atptCode: currentSchoolCodes.atptCode,
            schoolCode: currentSchoolCodes.schoolCode,
            grade: gradeSelect.value,
            selectEl: classSelect,
            placeholder: '반 선택',
            preserveValue: preserveValue
        });
    }

    if (gradeSelect) {
        gradeSelect.addEventListener('change', function () { refreshClassOptions(); });
    }

    if (schoolSearchInput && schoolSearchResults && schoolSearchWrap && typeof initSchoolSearchWidget === 'function') {
        initSchoolSearchWidget({
            input: schoolSearchInput,
            resultsBox: schoolSearchResults,
            wrap: schoolSearchWrap,
            onSelect: function (school) {
                schoolNameHidden.value = school.schoolName;
                schoolCodeHidden.value = school.schoolCode;
                atptCodeHidden.value = school.officeCode;
                if (schoolKindHidden) schoolKindHidden.value = school.schoolKind || '';
                currentSchoolCodes = { atptCode: school.officeCode, schoolCode: school.schoolCode };
                if (gradeSelect && typeof buildGradeOptions === 'function') {
                    buildGradeOptions(school.schoolKind, gradeSelect);
                }
                refreshClassOptions();
            },
            onClear: function () {
                schoolNameHidden.value = '';
                schoolCodeHidden.value = '';
                atptCodeHidden.value = '';
                if (schoolKindHidden) schoolKindHidden.value = '';
                currentSchoolCodes = null;
                if (gradeSelect && typeof buildGradeOptions === 'function') {
                    buildGradeOptions('', gradeSelect);
                }
                refreshClassOptions();
            }
        });
    }

    // 기존에 학교 정보가 저장되어 있다면, 페이지 진입 시 실제 학년 범위·반 목록으로 교체 (현재 값 유지 시도)
    if (currentSchoolCodes) {
        var originalGrade = gradeSelect ? gradeSelect.value : '';
        if (gradeSelect && typeof buildGradeOptions === 'function' && schoolKindHidden) {
            buildGradeOptions(schoolKindHidden.value, gradeSelect, originalGrade);
        }
        if (gradeSelect && gradeSelect.value) {
            refreshClassOptions(originalClassNum);
        }
    }

    form.addEventListener('submit', function (e) {
        var username = usernameInput.value.trim();

        if (!USERNAME_PATTERN.test(username)) {
            e.preventDefault();
            showMessage('아이디는 영문과 숫자만 사용할 수 있습니다.', 'error');
            usernameInput.focus();
            return;
        }

        if (username !== originalUsername && (!isAvailable || checkedValue !== username)) {
            e.preventDefault();
            showMessage('아이디 중복확인을 먼저 완료해주세요.', 'error');
            usernameInput.focus();
            return;
        }

        if (!checkPasswordMatch()) {
            e.preventDefault();
            if (confirmInput) confirmInput.focus();
            return;
        }

        if (schoolCodeHidden && !schoolCodeHidden.value) {
            e.preventDefault();
            alert('목록에서 학교를 검색하여 선택해주세요.');
            if (schoolSearchInput) schoolSearchInput.focus();
            return;
        }

        if (classSelect && (classSelect.disabled || !classSelect.value)) {
            e.preventDefault();
            alert('학년과 반을 선택해주세요.');
            if (gradeSelect) gradeSelect.focus();
        }
    });
});
