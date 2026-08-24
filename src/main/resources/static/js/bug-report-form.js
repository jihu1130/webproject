// 버그 리포트 첨부(사진/영상) 미리보기 - 서버(BugReportService.MAX_ATTACHMENTS)와 동일하게 최대 5개.
// input.files(브라우저 FileList)는 JS로 직접 못 줄이므로, 개수 초과는 안내만 하고 실제 차단은
// 제출 시점에 한 번 더 확인한다(post-form.js의 이미지 용량 체크와 동일한 접근).
document.addEventListener('DOMContentLoaded', function () {
    var input = document.getElementById('bugReportFileInput');
    var preview = document.getElementById('bugReportFilePreview');
    var form = document.getElementById('bugReportForm');
    if (!input || !preview) return;

    var MAX_FILES = 5;

    input.addEventListener('change', function () {
        preview.innerHTML = '';

        var files = Array.prototype.slice.call(input.files || []);

        files.forEach(function (file) {
            var item = document.createElement('div');
            item.className = 'post-image-preview-item';

            if (file.type && file.type.indexOf('video/') === 0) {
                var video = document.createElement('video');
                video.src = URL.createObjectURL(file);
                video.muted = true;
                item.appendChild(video);
            } else {
                var img = document.createElement('img');
                var url = URL.createObjectURL(file);
                img.src = url;
                img.alt = file.name;
                img.onload = function () { URL.revokeObjectURL(url); };
                item.appendChild(img);
            }

            preview.appendChild(item);
        });

        if (files.length > MAX_FILES && window.WebSchoolModal) {
            WebSchoolModal.alert('첨부파일은 최대 ' + MAX_FILES + '개까지만 업로드할 수 있어요. 앞의 ' + MAX_FILES + '개만 저장돼요.');
        }
    });

    if (form) {
        form.addEventListener('submit', function (e) {
            var files = Array.prototype.slice.call(input.files || []);
            if (files.length > MAX_FILES) {
                e.preventDefault();
                if (window.WebSchoolModal) {
                    WebSchoolModal.alert('첨부파일은 최대 ' + MAX_FILES + '개까지만 업로드할 수 있어요. 파일 선택을 다시 해주세요.');
                } else {
                    alert('첨부파일은 최대 ' + MAX_FILES + '개까지만 업로드할 수 있어요.');
                }
            }
        });
    }
});
