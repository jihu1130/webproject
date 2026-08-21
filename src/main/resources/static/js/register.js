var USERNAME_PATTERN = /^[A-Za-z0-9]+$/;

document.addEventListener('DOMContentLoaded', function () {
    var usernameInput = document.getElementById('username');
    var checkBtn = document.getElementById('checkUsernameBtn');
    var msgEl = document.getElementById('usernameCheckMsg');
    var form = document.getElementById('registerForm');
    var passwordInput = document.getElementById('password');
    var confirmInput = document.getElementById('confirmPassword');
    var passwordCheckMsg = document.getElementById('passwordCheckMsg');
    var strengthBar = document.getElementById('passwordStrengthBar');
    var strengthMsg = document.getElementById('passwordStrengthMsg');

    if (!usernameInput || !checkBtn || !msgEl || !form) return;

    // 순수 클라이언트 참고용 표시일 뿐, 실제 비밀번호 규칙(최소 길이 등)은 서버(UserService)가 검증한다.
    function checkPasswordStrength() {
        if (!passwordInput || !strengthBar || !strengthMsg) return;
        var value = passwordInput.value;

        if (!value) {
            strengthBar.className = 'auth-password-strength';
            strengthMsg.textContent = '';
            strengthMsg.className = 'auth-check-msg';
            return;
        }

        var score = 0;
        if (value.length >= 8) score++;
        if (value.length >= 12) score++;
        if (/[a-z]/.test(value) && /[A-Z]/.test(value)) score++;
        if (/[0-9]/.test(value)) score++;
        if (/[^A-Za-z0-9]/.test(value)) score++;

        var level, label;
        if (score <= 1) { level = 'weak'; label = '약함'; }
        else if (score === 2) { level = 'fair'; label = '보통'; }
        else if (score <= 4) { level = 'good'; label = '좋음'; }
        else { level = 'strong'; label = '매우 강함'; }

        strengthBar.className = 'auth-password-strength ' + level;
        strengthMsg.textContent = '비밀번호 강도: ' + label;
        strengthMsg.className = 'auth-check-msg';
    }

    if (passwordInput) {
        passwordInput.addEventListener('input', checkPasswordStrength);
    }

    function checkPasswordMatch() {
        if (!passwordInput || !confirmInput || !passwordCheckMsg) return true;

        if (!confirmInput.value) {
            passwordCheckMsg.textContent = '';
            passwordCheckMsg.className = 'auth-check-msg';
            return false;
        }

        var match = passwordInput.value === confirmInput.value;
        passwordCheckMsg.textContent = match ? '비밀번호가 일치합니다.' : '비밀번호가 일치하지 않습니다.';
        passwordCheckMsg.className = 'auth-check-msg' + (match ? ' success' : ' error');
        return match;
    }

    if (passwordInput && confirmInput) {
        passwordInput.addEventListener('input', checkPasswordMatch);
        confirmInput.addEventListener('input', checkPasswordMatch);
    }

    // 학교 검색 (필수)
    var schoolSearchInput = document.getElementById('regSchoolSearchInput');
    var schoolSearchResults = document.getElementById('regSchoolSearchResults');
    var schoolSearchWrap = document.getElementById('regSchoolSearchWrap');
    var schoolNameHidden = document.getElementById('schoolName');
    var schoolCodeHidden = document.getElementById('schoolCode');
    var atptCodeHidden = document.getElementById('atptCode');
    var schoolKindHidden = document.getElementById('schoolKind');
    var schoolCheckMsg = document.getElementById('regSchoolCheckMsg');
    var gradeSelect = document.getElementById('grade');
    var classSelect = document.getElementById('classNum');
    var currentSchoolCodes = null;

    function refreshClassOptions() {
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
            placeholder: '반 선택'
        });
    }

    if (gradeSelect) {
        gradeSelect.addEventListener('change', refreshClassOptions);
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
                if (schoolCheckMsg) { schoolCheckMsg.textContent = ''; schoolCheckMsg.className = 'auth-check-msg'; }
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

    var checkedValue = null; // 마지막으로 '사용 가능' 확인이 완료된 아이디 값
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

    form.addEventListener('submit', function (e) {
        var username = usernameInput.value.trim();

        if (!USERNAME_PATTERN.test(username)) {
            e.preventDefault();
            showMessage('아이디는 영문과 숫자만 사용할 수 있습니다.', 'error');
            usernameInput.focus();
            return;
        }

        if (!isAvailable || checkedValue !== username) {
            e.preventDefault();
            showMessage('아이디 중복확인을 먼저 완료해주세요.', 'error');
            usernameInput.focus();
            return;
        }

        if (!checkPasswordMatch()) {
            e.preventDefault();
            if (confirmInput) {
                passwordCheckMsg.textContent = '비밀번호가 일치하지 않습니다.';
                passwordCheckMsg.className = 'auth-check-msg error';
                confirmInput.focus();
            }
            return;
        }

        if (schoolCodeHidden && !schoolCodeHidden.value) {
            e.preventDefault();
            if (schoolCheckMsg) {
                schoolCheckMsg.textContent = '목록에서 학교를 검색하여 선택해주세요.';
                schoolCheckMsg.className = 'auth-check-msg error';
            }
            if (schoolSearchInput) schoolSearchInput.focus();
            return;
        }

        if (classSelect && (classSelect.disabled || !classSelect.value)) {
            e.preventDefault();
            WebSchoolModal.alert('학년과 반을 선택해주세요.');
            if (gradeSelect) gradeSelect.focus();
        }
    });
});
