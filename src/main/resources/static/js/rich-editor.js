// 게시글/오늘의 한마디 작성 화면 공용 리치 에디터(Quill) 초기화. 이미지/동영상/파일 삽입은 Quill
// 기본 image 포맷(base64 임베드)을 쓰지 않고 항상 /api/uploads/editor로 먼저 업로드한 뒤 반환된
// URL을 본문에 삽입한다 - base64로 넣으면 본문 HTML 자체가 거대해지고 DB에도 그대로 부풀려 저장되기
// 때문. 동영상/파일/공유카드는 Quill이 기본으로 이해하는 포맷이 아니라서 dangerouslyPasteHTML로
// 넣으면 Quill의 clipboard 매처가 인식 못 하는 태그/속성(class, download 등)을 지우거나 통째로
// 버린다(실제로 <video>가 완전히 사라지는 걸 확인함) - 그래서 아래 3개(video/file/embed)를 전부
// Quill 커스텀 블롯으로 등록해 Quill이 "이건 내가 아는 통짜 임베드"로 인식하게 만든다.
(function (global) {
    var blotsRegistered = false;

    function registerBlots() {
        if (blotsRegistered || typeof Quill === 'undefined') {
            return;
        }
        blotsRegistered = true;

        var BlockEmbed = Quill.import('blots/block/embed');

        // 동영상 - <video controls src="..."> 그대로 왕복 보존
        class RichVideoBlot extends BlockEmbed {
            static create(url) {
                var node = super.create();
                node.setAttribute('src', url);
                node.setAttribute('controls', true);
                return node;
            }
            static value(node) {
                return node.getAttribute('src');
            }
        }
        RichVideoBlot.blotName = 'richVideo';
        RichVideoBlot.tagName = 'video';
        Quill.register(RichVideoBlot);

        // 파일 다운로드 카드 - <a class="rich-file-attachment" href="..." download>📎 파일명</a>
        // <a>는 Quill 기본 link(inline) 포맷이 이미 선점한 태그라 태그명 충돌을 피하려고 <figure>로
        // 감싸고, 안쪽 <a>는 Quill이 건드리지 않는 opaque한 자식 콘텐츠로만 취급된다.
        class RichFileBlot extends BlockEmbed {
            static create(value) {
                var node = super.create();
                node.setAttribute('contenteditable', 'false');
                node.classList.add('rich-file-attachment');
                var a = document.createElement('a');
                a.setAttribute('href', value.url);
                a.setAttribute('target', '_blank');
                a.setAttribute('rel', 'noopener');
                a.setAttribute('download', '');
                a.textContent = '📎 ' + value.name;
                node.appendChild(a);
                return node;
            }
            static value(node) {
                var a = node.querySelector('a');
                return { url: a.getAttribute('href'), name: a.textContent.replace(/^📎\s*/, '') };
            }
        }
        RichFileBlot.blotName = 'richFile';
        RichFileBlot.tagName = 'figure';
        Quill.register(RichFileBlot);

        // "게시물/한마디로 바로가기" 카드 - <aside>로 감싸 <figure>(파일 카드)와도 태그가 겹치지 않게 함
        class RichEmbedBlot extends BlockEmbed {
            static create(value) {
                var node = super.create();
                node.setAttribute('contenteditable', 'false');
                var a = document.createElement('a');
                a.className = 'content-embed-card';
                a.setAttribute('href', value.url);
                var label = document.createElement('span');
                label.className = 'content-embed-label';
                label.textContent = value.label;
                var title = document.createElement('span');
                title.className = 'content-embed-title';
                title.textContent = value.title;
                a.appendChild(label);
                a.appendChild(title);
                node.appendChild(a);
                return node;
            }
            static value(node) {
                var a = node.querySelector('a');
                return {
                    url: a.getAttribute('href'),
                    label: a.querySelector('.content-embed-label').textContent,
                    title: a.querySelector('.content-embed-title').textContent
                };
            }
        }
        RichEmbedBlot.blotName = 'richEmbed';
        RichEmbedBlot.tagName = 'aside';
        Quill.register(RichEmbedBlot);
    }

    function initRichEditor(config) {
        var container = document.getElementById(config.editorId);
        var hidden = document.getElementById(config.hiddenId);
        if (!container || !hidden || typeof Quill === 'undefined') {
            return null;
        }
        registerBlots();

        var icons = Quill.import('ui/icons');
        icons['richfile-image'] = '🖼'; // 🖼
        icons['richfile-video'] = '🎬'; // 🎬
        icons['richfile-file'] = '📎';  // 📎
        icons['richfile-embed'] = '🔗'; // 🔗

        var toolbar = config.toolbar || [
            ['bold', 'italic', 'underline', 'strike'],
            [{ header: [2, 3, false] }],
            [{ list: 'ordered' }, { list: 'bullet' }],
            ['blockquote', 'link'],
            ['richfile-image', 'richfile-video', 'richfile-file', 'richfile-embed'],
            ['clean']
        ];

        var quill = new Quill(container, {
            theme: 'snow',
            placeholder: config.placeholder || '',
            modules: {
                toolbar: {
                    container: toolbar,
                    handlers: {
                        'richfile-image': function () { triggerUpload(quill, 'image/*'); },
                        'richfile-video': function () { triggerUpload(quill, 'video/*'); },
                        'richfile-file': function () { triggerUpload(quill, '*/*'); },
                        'richfile-embed': function () { insertEmbedCard(quill); }
                    }
                }
            }
        });

        if (hidden.value) {
            quill.root.innerHTML = hidden.value;
            // innerHTML 직접 대입은 Quill을 거치지 않아 text-change가 안 뜨고, 그러면 아래 설명대로
            // ql-blank가 그대로 남아 수정 화면 진입 순간 placeholder가 기존 본문 위에 겹쳐 보인다.
            syncBlankClass();
        }

        // Quill은 placeholder를 CSS(.ql-editor.ql-blank::before)로만 그리고, 그 ql-blank 클래스는
        // 오직 text-change 이벤트에서만 갱신한다(quill 2.0.2: EDITOR_CHANGE 핸들러에서
        // root.classList.toggle('ql-blank', editor.isBlank())). 그런데 한글 IME로 입력하면
        // compositionstart 시점에 Quill의 Composition 모듈이 scroll.batchStart()를 걸어 조합이
        // 끝날 때까지 text-change 자체가 발생하지 않는다 - 그래서 첫 글자를 조합하는 동안
        // "내용을 입력하세요" placeholder가 안 지워진 채 입력 중인 글자와 겹쳐 보였다(사용자 신고).
        // 조합이 시작되면 클래스를 직접 떼고, 조합이 끝난 뒤 Quill이 모델을 따라잡으면 실제로
        // 비어있는지를 다시 계산해서 되돌린다.
        function isEditorBlank() {
            // 빈 에디터는 <p><br></p> 하나뿐이라 getLength()가 1이다. 이미지/동영상 같은 임베드만
            // 들어있어도 길이가 2 이상이 되므로 "글자는 없지만 사진은 있는" 경우도 blank가 아니다.
            return quill.getLength() <= 1 && quill.root.textContent.trim() === '';
        }

        function syncBlankClass() {
            quill.root.classList.toggle('ql-blank', isEditorBlank());
        }

        quill.root.addEventListener('compositionstart', function () {
            quill.root.classList.remove('ql-blank');
        });
        quill.root.addEventListener('compositionend', function () {
            // Quill의 compositionend 핸들러가 queueMicrotask로 batchEnd()를 돌려 모델을 갱신하므로,
            // 그보다 뒤에 실행되도록 우리도 마이크로태스크로 미룬다(리스너 등록이 Quill보다 늦어서
            // 같은 이벤트에서 우리 마이크로태스크가 항상 뒤에 큐잉된다).
            queueMicrotask(syncBlankClass);
        });

        function syncHidden() {
            var html = quill.root.innerHTML;
            hidden.value = (html === '<p><br></p>') ? '' : html;
        }
        quill.on('text-change', syncHidden);
        syncHidden();

        // 폼 제출 직전에도 한 번 더 동기화(text-change가 안 뜨는 IME 조합 중 제출되는 경우 대비)
        var form = container.closest('form');
        if (form) {
            form.addEventListener('submit', function (e) {
                syncHidden();
                // 설문(투표)을 첨부했으면 그 자체가 내용 역할을 하므로 본문이 비어도 통과시킨다
                // (post/form.html, school/comment-form.html의 #pollToggle 체크박스 - 서버(PostService/
                // ScheduleCommentService)도 동일한 규칙으로 검증하니 여기서만 막으면 안 된다).
                var pollToggle = document.getElementById('pollToggle');
                var pollQuestion = document.getElementById('pollQuestion');
                var pollAttached = !!(pollToggle && pollToggle.checked && pollQuestion && pollQuestion.value.trim() !== '');
                if (!pollAttached && config.required && quill.getText().trim() === '' && hidden.value.indexOf('<img') === -1
                        && hidden.value.indexOf('<video') === -1 && hidden.value.indexOf('<figure') === -1
                        && hidden.value.indexOf('<aside') === -1) {
                    e.preventDefault();
                    WebSchoolModal.alert('내용을 입력해주세요.');
                }
            });
        }

        return quill;
    }

    function triggerUpload(quill, accept) {
        var input = document.createElement('input');
        input.type = 'file';
        input.accept = accept;
        input.style.display = 'none';
        document.body.appendChild(input);

        input.addEventListener('change', function () {
            var file = input.files[0];
            document.body.removeChild(input);
            if (!file) return;
            uploadAndInsert(quill, file);
        });

        input.click();
    }

    function uploadAndInsert(quill, file) {
        var range = quill.getSelection(true) || { index: quill.getLength() };
        var formData = new FormData();
        formData.append('file', file);

        fetch('/api/uploads/editor', { method: 'POST', headers: WebSchoolCsrf.headers(), body: formData })
            .then(function (res) {
                return res.json().then(function (data) {
                    if (!res.ok) throw new Error(data.error || '업로드에 실패했습니다.');
                    return data;
                });
            })
            .then(function (data) {
                if (data.kind === 'image') {
                    quill.insertEmbed(range.index, 'image', data.url, 'user');
                } else if (data.kind === 'video') {
                    quill.insertEmbed(range.index, 'richVideo', data.url, 'user');
                } else {
                    quill.insertEmbed(range.index, 'richFile', { url: data.url, name: data.originalFilename || '첨부파일' }, 'user');
                }
                quill.setSelection(range.index + 1, 0, 'user');
            })
            .catch(function (err) {
                WebSchoolModal.alert(err.message || '파일 업로드에 실패했습니다.');
            });
    }

    // "게시물/한마디로 바로가기" 카드 삽입 - 사용자가 붙여넣은 URL을 서버(/api/embed/resolve)에 물어서
    // 실제로 존재하는 대상인지 확인하고, 제목(또는 한마디 내용 미리보기)을 카드에 박아넣는다.
    async function insertEmbedCard(quill) {
        var url = await WebSchoolModal.prompt(
            '공유할 게시물 또는 오늘의 한마디 링크를 붙여넣으세요.',
            { inputPlaceholder: '예) https://.../posts/xxxx-... 또는 /school/comments/123' }
        );
        if (!url) return;

        var range = quill.getSelection(true) || { index: quill.getLength() };

        fetch('/api/embed/resolve?url=' + encodeURIComponent(url))
            .then(function (res) {
                return res.json().then(function (data) {
                    if (!res.ok) throw new Error(data.error || '링크를 확인할 수 없어요.');
                    return data;
                });
            })
            .then(function (data) {
                quill.insertEmbed(range.index, 'richEmbed', data, 'user');
                quill.setSelection(range.index + 1, 0, 'user');
            })
            .catch(function (err) {
                WebSchoolModal.alert(err.message || '링크를 확인할 수 없어요.');
            });
    }

    global.initRichEditor = initRichEditor;
})(window);
