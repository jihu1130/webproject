// 학교 검색 드롭다운 위젯 (공용 - 캘린더/회원가입/마이페이지 수정에서 사용)
// options: { input, resultsBox, wrap, onSelect(school), onClear() }
function initSchoolSearchWidget(options) {
    var input = options.input;
    var resultsBox = options.resultsBox;
    var wrap = options.wrap;
    var onSelect = options.onSelect || function () {};
    var onClear = options.onClear || function () {};

    var debounceTimer = null;
    var lastSelectedName = null;

    input.addEventListener('input', function () {
        var keyword = input.value.trim();

        if (lastSelectedName !== null && lastSelectedName !== keyword) {
            lastSelectedName = null;
            onClear();
        }

        clearTimeout(debounceTimer);

        if (keyword.length < 2) {
            closeResults();
            return;
        }

        debounceTimer = setTimeout(function () {
            searchSchools(keyword);
        }, 300);
    });

    document.addEventListener('click', function (e) {
        if (!wrap.contains(e.target)) {
            closeResults();
        }
    });

    function searchSchools(keyword) {
        resultsBox.innerHTML = '<li class="school-search-empty">검색 중...</li>';
        openResults();

        fetch('/school/api/search?keyword=' + encodeURIComponent(keyword))
            .then(function (res) { return res.json(); })
            .then(function (schools) {
                renderResults(schools);
            })
            .catch(function () {
                resultsBox.innerHTML = '<li class="school-search-empty">검색 중 오류가 발생했습니다.</li>';
            });
    }

    function renderResults(schools) {
        if (!schools || schools.length === 0) {
            resultsBox.innerHTML = '<li class="school-search-empty">일치하는 학교가 없습니다.</li>';
            return;
        }

        resultsBox.innerHTML = '';
        schools.forEach(function (school) {
            var li = document.createElement('li');
            li.className = 'school-search-item';
            li.innerHTML = `
                <span class="school-search-item-name">${school.schoolName} <em>${school.schoolKind || ''}</em></span>
                <span class="school-search-item-addr">${school.address || '주소 정보 없음'}</span>
            `;
            li.addEventListener('click', function () {
                lastSelectedName = school.schoolName;
                input.value = school.schoolName;
                closeResults();
                onSelect(school);
            });
            resultsBox.appendChild(li);
        });
    }

    function openResults() { resultsBox.classList.add('open'); }
    function closeResults() { resultsBox.classList.remove('open'); resultsBox.innerHTML = ''; }
}
