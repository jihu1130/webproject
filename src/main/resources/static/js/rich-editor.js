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
        }

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
                if (config.required && quill.getText().trim() === '' && hidden.value.indexOf('<img') === -1
                        && hidden.value.indexOf('<video') === -1 && hidden.value.indexOf('<figure') === -1
                        && hidden.value.indexOf('<aside') === -1) {
                    e.preventDefault();
                    alert('내용을 입력해주세요.');
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

        fetch('/api/uploads/editor', { method: 'POST', body: formData })
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
                alert(err.message || '파일 업로드에 실패했습니다.');
            });
    }

    // "게시물/한마디로 바로가기" 카드 삽입 - 사용자가 붙여넣은 URL을 서버(/api/embed/resolve)에 물어서
    // 실제로 존재하는 대상인지 확인하고, 제목(또는 한마디 내용 미리보기)을 카드에 박아넣는다.
    function insertEmbedCard(quill) {
        var url = prompt('공유할 게시물 또는 오늘의 한마디 링크를 붙여넣으세요.\n예) https://.../posts/xxxx-...\n예) https://.../school/comments/123');
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
                alert(err.message || '링크를 확인할 수 없어요.');
            });
    }

    global.initRichEditor = initRichEditor;
})(window);
