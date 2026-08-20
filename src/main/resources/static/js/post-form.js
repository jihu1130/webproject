// 게시물 작성/수정 폼: 카테고리별 안내 문구/placeholder 동적 전환 + 이미지 첨부 미리보기
document.addEventListener('DOMContentLoaded', function () {
    // ---- 카테고리별 안내 문구 ----
    var radioGroup = document.getElementById('postCategoryRadioGroup');
    var categoryHint = document.getElementById('postCategoryHint');
    var contentGuide = document.getElementById('postContentGuide');
    var titleInput = document.getElementById('postTitleInput');

    var CATEGORY_TEXT = {
        FREE: {
            hint: '',
            guide: '',
            placeholder: '제목을 입력하세요'
        },
        ANONYMOUS: {
            hint: '작성자 닉네임은 \'익명\'으로 표시됩니다.',
            guide: '',
            placeholder: '제목을 입력하세요'
        },
        QNA: {
            hint: '',
            guide: '질문 요약 / 상세 내용을 함께 적어주시면 답변받기 더 쉬워요.',
            placeholder: '무엇이 궁금한가요?'
        }
    };

    function applyCategoryText(category) {
        var text = CATEGORY_TEXT[category] || CATEGORY_TEXT.FREE;

        if (categoryHint) {
            categoryHint.textContent = text.hint;
            categoryHint.style.display = text.hint ? 'block' : 'none';
        }
        if (contentGuide) {
            contentGuide.textContent = text.guide;
            contentGuide.style.display = text.guide ? 'block' : 'none';
        }
        if (titleInput) {
            titleInput.setAttribute('placeholder', text.placeholder);
        }
    }

    if (radioGroup) {
        var radios = radioGroup.querySelectorAll('input[type="radio"]');
        radios.forEach(function (radio) {
            radio.addEventListener('change', function () {
                if (radio.checked) applyCategoryText(radio.value);
            });
        });

        var checked = radioGroup.querySelector('input[type="radio"]:checked');
        applyCategoryText(checked ? checked.value : 'FREE');
    }

    // ---- 이미지 첨부 미리보기 ----
    var imageInput = document.getElementById('postImageInput');
    var imagePreview = document.getElementById('postImagePreview');
    var imageSizeWarning = document.getElementById('postImageSizeWarning');
    // 서버 쪽 PostImageService.MAX_FILE_SIZE(5MB)와 동일한 값 - 여기서 걸러도 최종 검증은
    // 여전히 서버가 하지만, 제출 전에 바로 알려줘서 다 쓴 글 내용을 날릴 위험을 줄인다.
    var MAX_IMAGE_SIZE = 5 * 1024 * 1024;

    if (imageInput && imagePreview) {
        imageInput.addEventListener('change', function () {
            imagePreview.innerHTML = '';
            var oversized = [];

            var files = Array.prototype.slice.call(imageInput.files || []);
            files.forEach(function (file) {
                if (!file.type || file.type.indexOf('image/') !== 0) return;

                if (file.size > MAX_IMAGE_SIZE) {
                    oversized.push(file.name);
                    return;
                }

                var url = URL.createObjectURL(file);
                var item = document.createElement('div');
                item.className = 'post-image-preview-item';

                var img = document.createElement('img');
                img.src = url;
                img.alt = file.name;
                img.onload = function () { URL.revokeObjectURL(url); };

                item.appendChild(img);
                imagePreview.appendChild(item);
            });

            if (imageSizeWarning) {
                if (oversized.length > 0) {
                    imageSizeWarning.textContent = '5MB를 초과해 제외됨: ' + oversized.join(', ');
                    imageSizeWarning.style.display = 'block';
                } else {
                    imageSizeWarning.style.display = 'none';
                }
            }
        });
    }
});
