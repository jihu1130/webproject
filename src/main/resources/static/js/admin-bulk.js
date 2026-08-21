// 관리자 목록 화면 공용 일괄 처리(체크박스 다중 선택 + 선택 블라인드/삭제).
// admin/post-list.html, admin/comment-list.html, admin/schedule-comment-list.html,
// admin/report-list.html(3개 탭) 전부 이 스크립트 하나를 공유한다.
//
// 각 행이 이미 개별 액션 폼(블라인드/삭제/문제없음 처리 등)을 갖고 있어서 테이블 전체를
// <form>으로 감쌀 수 없다(HTML은 <form> 중첩을 지원하지 않음) - 그래서 체크박스는 폼 밖에
// 맨 input으로 두고, 버튼을 누른 시점에 선택된 id들만 모아 동적으로 새 폼을 만들어 제출한다.
//
// 마크업 규약:
//   <table data-bulk-table="bulkToolbar-xxx"> ... <input class="admin-bulk-check" value="id"> ...
//   <div id="bulkToolbar-xxx" class="admin-bulk-toolbar">
//     <span class="admin-bulk-count"></span>
//     <button data-bulk-action="/admin/.../bulk-blind" data-bulk-label="블라인드 처리">선택 블라인드</button>
//     <button data-bulk-action="/admin/.../bulk-delete" data-bulk-label="삭제" data-bulk-danger="true">선택 삭제</button>
//   </div>
document.addEventListener('DOMContentLoaded', function () {
    document.querySelectorAll('[data-bulk-table]').forEach(function (table) {
        var toolbar = document.getElementById(table.getAttribute('data-bulk-table'));
        if (!toolbar) return;

        var checkAll = table.querySelector('.admin-bulk-check-all');
        var countEl = toolbar.querySelector('.admin-bulk-count');
        var buttons = toolbar.querySelectorAll('button[data-bulk-action]');

        function checks() {
            return Array.from(table.querySelectorAll('.admin-bulk-check'));
        }

        function refresh() {
            var all = checks();
            var selected = all.filter(function (c) { return c.checked; });
            countEl.textContent = selected.length + '개 선택됨';
            buttons.forEach(function (b) { b.disabled = selected.length === 0; });
            if (checkAll) {
                checkAll.checked = all.length > 0 && selected.length === all.length;
                checkAll.indeterminate = selected.length > 0 && selected.length < all.length;
            }
        }

        if (checkAll) {
            checkAll.addEventListener('change', function () {
                checks().forEach(function (c) { c.checked = checkAll.checked; });
                refresh();
            });
        }

        table.addEventListener('change', function (e) {
            if (e.target.classList.contains('admin-bulk-check')) refresh();
        });

        buttons.forEach(function (btn) {
            btn.addEventListener('click', async function () {
                var selected = checks().filter(function (c) { return c.checked; }).map(function (c) { return c.value; });
                if (selected.length === 0) return;

                var label = btn.getAttribute('data-bulk-label') || '처리';
                var danger = btn.getAttribute('data-bulk-danger') === 'true';
                if (!(await WebSchoolModal.confirm(selected.length + '개 항목을 ' + label + '할까요?', { danger: danger }))) return;

                var form = document.createElement('form');
                form.method = 'post';
                form.action = btn.getAttribute('data-bulk-action');

                selected.forEach(function (id) {
                    var input = document.createElement('input');
                    input.type = 'hidden';
                    input.name = 'ids';
                    input.value = id;
                    form.appendChild(input);
                });

                var returnUrl = document.createElement('input');
                returnUrl.type = 'hidden';
                returnUrl.name = 'returnUrl';
                returnUrl.value = location.pathname + location.search;
                form.appendChild(returnUrl);

                document.body.appendChild(form);
                form.submit();
            });
        });

        refresh();
    });
});
