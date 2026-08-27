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

    // 서버(PostImageService.MAX_FILE_SIZE)와 동일한 5MB 제한 - 예전엔 제출 버튼을 눌러야만
    // "파일이 너무 큽니다" 에러를 알 수 있었다. 초과 파일은 미리보기에서 빼고 안내만 하되,
    // input.files 자체(브라우저 FileList)는 JS로 직접 못 바꾸므로 실제 제출 차단은 폼 submit
    // 시점에 한 번 더 확인한다(아래 postForm submit 리스너).
    var MAX_IMAGE_SIZE = 5 * 1024 * 1024;

    if (imageInput && imagePreview) {
        imageInput.addEventListener('change', function () {
            imagePreview.innerHTML = '';

            var files = Array.prototype.slice.call(imageInput.files || []);
            var oversized = [];
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

            if (oversized.length > 0) {
                WebSchoolModal.alert('다음 이미지는 5MB를 초과해 제출 시 저장되지 않아요: ' + oversized.join(', '));
            }
        });
    }

    // 대표 이미지 드롭존 - input[type=file]이 드롭존 전체를 투명하게 덮고 있어서(post.css)
    // 클릭은 브라우저가 알아서 파일 선택창을 열고, 드래그해서 놓는 것도 브라우저가 기본으로
    // input.files를 채우고 change 이벤트를 쏴준다(별도 drop 처리 불필요) - 여기서는 드래그
    // 중이라는 시각 피드백(.is-dragover)만 붙였다 뗀다.
    var imageDropzone = document.getElementById('postImageDropzone');
    if (imageDropzone) {
        ['dragenter', 'dragover'].forEach(function (evt) {
            imageDropzone.addEventListener(evt, function (e) {
                e.preventDefault();
                imageDropzone.classList.add('is-dragover');
            });
        });
        ['dragleave', 'drop'].forEach(function (evt) {
            imageDropzone.addEventListener(evt, function () {
                imageDropzone.classList.remove('is-dragover');
            });
        });
    }

    // ---- 글쓰기 중 실수로 페이지 이탈 시 경고 ----
    // 제목 입력이나 리치 에디터(contenteditable) 안에서 타이핑이 감지되면 dirty로 표시하고,
    // 폼이 정상 제출되는 경우(submit 이벤트)에는 경고를 띄우지 않는다.
    var postForm = document.getElementById('postForm');
    if (postForm) {
        var formDirty = false;
        var formSubmitting = false;

        postForm.addEventListener('input', function () {
            formDirty = true;
        });

        postForm.addEventListener('submit', function () {
            formSubmitting = true;
        });

        window.addEventListener('beforeunload', function (e) {
            if (formDirty && !formSubmitting) {
                e.preventDefault();
                e.returnValue = '';
            }
        });
    }

    // ---- 글쓰기 임시저장(draft) ----
    // 이탈 경고만으로는 로그인 만료 등으로 강제로 튕겨나가는 경우까지 못 막는다. 새 글 작성
    // (수정 화면은 이미 저장된 내용이 있으니 제외 - window.__POST_FORM_MODE__)에서만 제목/본문을
    // localStorage에 주기적으로 자동 저장해두고, 작성 화면에 다시 들어오면 이어서 쓸지 물어본다.
    var DRAFT_KEY = 'webschool_post_draft_v1';
    var hiddenContent = document.getElementById('postContentHidden');

    if (postForm && titleInput && hiddenContent && window.__POST_FORM_MODE__ !== 'edit') {
        function currentCategory() {
            var checked = radioGroup && radioGroup.querySelector('input[type="radio"]:checked');
            return checked ? checked.value : 'FREE';
        }

        function saveDraft() {
            var data = {
                title: titleInput.value,
                content: hiddenContent.value,
                category: currentCategory()
            };
            if (!data.title && !data.content) {
                localStorage.removeItem(DRAFT_KEY);
                return;
            }
            localStorage.setItem(DRAFT_KEY, JSON.stringify(data));
        }

        function clearDraft() {
            localStorage.removeItem(DRAFT_KEY);
        }

        function restoreDraft(draft) {
            titleInput.value = draft.title || '';
            hiddenContent.value = draft.content || '';
            if (window.__postQuill) {
                window.__postQuill.root.innerHTML = draft.content || '<p><br></p>';
            }
            if (draft.category && radioGroup) {
                var radio = radioGroup.querySelector('input[value="' + draft.category + '"]');
                if (radio) {
                    radio.checked = true;
                    applyCategoryText(draft.category);
                }
            }
            formDirty = true;
        }

        var savedRaw = localStorage.getItem(DRAFT_KEY);
        if (savedRaw) {
            try {
                var draft = JSON.parse(savedRaw);
                if (draft && (draft.title || draft.content)) {
                    WebSchoolModal.confirm('이전에 쓰다 만 글이 있어요. 이어서 작성하시겠어요?').then(function (ok) {
                        if (ok) {
                            restoreDraft(draft);
                        } else {
                            clearDraft();
                        }
                    });
                }
            } catch (e) {
                clearDraft();
            }
        }

        var draftTimer = null;
        postForm.addEventListener('input', function () {
            clearTimeout(draftTimer);
            draftTimer = setTimeout(saveDraft, 1500);
        });

        postForm.addEventListener('submit', clearDraft);
    }
});
