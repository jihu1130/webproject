// 학교/학년 기준으로 실제 반 목록을 불러와 select를 채우는 공용 헬퍼
// (캘린더/회원가입/마이페이지 수정에서 사용)
// params: { atptCode, schoolCode, grade, selectEl, placeholder, preserveValue, onLoaded }
function loadClassOptions(params) {
    var atptCode = params.atptCode;
    var schoolCode = params.schoolCode;
    var grade = params.grade;
    var selectEl = params.selectEl;
    var placeholder = params.placeholder || '반 선택';
    var preserveValue = params.preserveValue;
    var onLoaded = params.onLoaded || function () {};

    selectEl.disabled = true;
    selectEl.innerHTML = '<option value="">불러오는 중...</option>';

    var url = '/school/api/classes?atptCode=' + encodeURIComponent(atptCode)
        + '&schoolCode=' + encodeURIComponent(schoolCode)
        + (grade ? '&grade=' + encodeURIComponent(grade) : '');

    fetch(url)
        .then(function (res) { return res.json(); })
        .then(function (classes) {
            fillClassSelect(selectEl, placeholder, (classes && classes.length > 0) ? classes : fallbackClassList());
            restoreValue();
            onLoaded();
        })
        .catch(function () {
            fillClassSelect(selectEl, placeholder, fallbackClassList());
            restoreValue();
            onLoaded();
        });

    function restoreValue() {
        if (preserveValue && selectEl.querySelector('option[value="' + preserveValue + '"]')) {
            selectEl.value = preserveValue;
        } else if (selectEl.options.length > 1) {
            // 별도로 유지할 값이 없으면 안내 문구(첫 옵션)를 건너뛰고 1반을 기본 선택
            selectEl.selectedIndex = 1;
        }
    }
}

function fillClassSelect(selectEl, placeholder, classList) {
    selectEl.innerHTML = '';
    selectEl.disabled = false;

    var ph = document.createElement('option');
    ph.value = '';
    ph.textContent = placeholder;
    selectEl.appendChild(ph);

    // 화면 표시는 초/중/고 상관없이 "1반, 2반..." 순서로 통일한다.
    // 일부 초등학교는 NEIS에 학급명이 "가람반"처럼 이름으로 등록되어 있는데,
    // 시간표/급식 조회에는 그 실제 학급명이 필요하므로 value는 실제 값을 그대로 쓰고
    // 화면에 보이는 텍스트만 순번으로 바꾼다.
    classList.forEach(function (c, idx) {
        var opt = document.createElement('option');
        opt.value = c;
        opt.textContent = (idx + 1) + '반';
        selectEl.appendChild(opt);
    });
}

function fallbackClassList() {
    var list = [];
    for (var i = 1; i <= 20; i++) list.push(String(i));
    return list;
}
