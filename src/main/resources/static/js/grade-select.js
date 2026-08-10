// 학교 종류(초/중/고)에 맞춰 학년 select를 다시 구성하는 공용 헬퍼
// (캘린더/회원가입/마이페이지 수정에서 사용)
function maxGradeFor(schoolKind) {
    if (schoolKind && schoolKind.indexOf('초등') !== -1) return 6;
    return 3; // 중학교/고등학교/미확인 기본값
}

function buildGradeOptions(schoolKind, selectEl, preserveValue) {
    var max = maxGradeFor(schoolKind);
    var keepValue = (preserveValue !== undefined) ? preserveValue : selectEl.value;

    selectEl.innerHTML = '';
    for (var g = 1; g <= max; g++) {
        var opt = document.createElement('option');
        opt.value = g;
        opt.textContent = g + '학년';
        selectEl.appendChild(opt);
    }

    if (keepValue && selectEl.querySelector('option[value="' + keepValue + '"]')) {
        selectEl.value = keepValue;
    } else {
        selectEl.selectedIndex = 0;
    }
}
