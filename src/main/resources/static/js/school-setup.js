document.addEventListener('DOMContentLoaded', function () {
    var form = document.getElementById('schoolSetupForm');
    var schoolSearchInput = document.getElementById('setupSchoolSearchInput');
    var schoolSearchResults = document.getElementById('setupSchoolSearchResults');
    var schoolSearchWrap = document.getElementById('setupSchoolSearchWrap');
    var schoolNameHidden = document.getElementById('schoolName');
    var schoolCodeHidden = document.getElementById('schoolCode');
    var atptCodeHidden = document.getElementById('atptCode');
    var schoolKindHidden = document.getElementById('schoolKind');
    var schoolCheckMsg = document.getElementById('setupSchoolCheckMsg');
    var gradeSelect = document.getElementById('grade');
    var classSelect = document.getElementById('classNum');

    if (!form || !schoolSearchInput) return;

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

    form.addEventListener('submit', function (e) {
        if (schoolCodeHidden && !schoolCodeHidden.value) {
            e.preventDefault();
            if (schoolCheckMsg) {
                schoolCheckMsg.textContent = '목록에서 학교를 검색하여 선택해주세요.';
                schoolCheckMsg.className = 'auth-check-msg error';
            }
            schoolSearchInput.focus();
            return;
        }

        if (classSelect && (classSelect.disabled || !classSelect.value)) {
            e.preventDefault();
            alert('학년과 반을 선택해주세요.');
            if (gradeSelect) gradeSelect.focus();
        }
    });
});
