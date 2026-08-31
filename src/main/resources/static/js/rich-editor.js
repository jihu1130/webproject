// 게시글/오늘의 한마디 작성 화면 공용 리치 에디터(Quill) 초기화. 이미지/동영상/파일 삽입은 Quill
// 기본 image 포맷(base64 임베드)을 쓰지 않고 항상 /api/uploads/editor로 먼저 업로드한 뒤 반환된
// URL을 본문에 삽입한다 - base64로 넣으면 본문 HTML 자체가 거대해지고 DB에도 그대로 부풀려 저장되기
// 때문. 동영상/파일/공유카드는 Quill이 기본으로 이해하는 포맷이 아니라서 dangerouslyPasteHTML로
// 넣으면 Quill의 clipboard 매처가 인식 못 하는 태그/속성(class, download 등)을 지우거나 통째로
// 버린다(실제로 <video>가 완전히 사라지는 걸 확인함) - 그래서 아래 3개(video/file/embed)를 전부
// Quill 커스텀 블롯으로 등록해 Quill이 "이건 내가 아는 통짜 임베드"로 인식하게 만든다.
(function (global) {
    var blotsRegistered = false;

    // 글자 크기 - 예전엔 Quill 기본 header(제목2/제목3/본문) 포맷을 "글자 크기 설정"처럼
    // 라벨만 바꿔 쓰고 있었는데(의미상 맞지 않음 - h2/h3는 제목 태그다), 실제 크기(px) 단위
    // 선택으로 바꾼다. Quill의 style attributor는 선택值을 그대로 inline style="font-size:...px"로
    // 써주므로 별도 렌더링 CSS가 필요 없다.
    var SIZE_WHITELIST = ['14px', '18px', '24px', '32px'];

    function registerBlots() {
        if (blotsRegistered || typeof Quill === 'undefined') {
            return;
        }
        blotsRegistered = true;

        var SizeStyle = Quill.import('attributors/style/size');
        SizeStyle.whitelist = SIZE_WHITELIST;
        Quill.register(SizeStyle, true);

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
        icons['richfile-attach'] = '📎'; // 📎 - 사진/동영상/파일/링크카드 4종을 한 버튼으로 통합(아래 setupAttachPopover)

        // 예전엔 삽입 버튼이 🖼/🎬/📎/🔗 4개가 서식 버튼들과 나란히 늘어서 있어서 "삽입"과 "서식"이
        // 구분 안 됐다(사용자 지적) - 이제 삽입은 "📎" 버튼 하나 + 팝오버로 묶는다.
        var toolbar = config.toolbar || [
            ['bold', 'italic', 'underline', 'strike'],
            [{ size: [false].concat(SIZE_WHITELIST) }],
            [{ list: 'ordered' }, { list: 'bullet' }],
            ['blockquote', 'link'],
            ['richfile-attach'],
            ['clean']
        ];

        var quill = new Quill(container, {
            theme: 'snow',
            placeholder: config.placeholder || '',
            modules: {
                toolbar: {
                    container: toolbar,
                    handlers: {
                        'richfile-attach': function () { toggleAttachPopover(quill, this); }
                    }
                }
            }
        });

        setupAttachPopover(quill);
        setupSelectionToolbar(quill);
        setupMarkdownShortcuts(quill);

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

    // "📎" 버튼 하나에 사진/동영상/파일/링크카드 4개를 모아두는 팝오버 - 예전엔 이 4개가 각자
    // 툴바 버튼으로 늘어서 있어서 뭐가 "서식"이고 뭐가 "삽입"인지 구분이 안 됐다(사용자 지적).
    var activeAttachPopover = null;

    function toggleAttachPopover(quill, toolbarModule) {
        var button = toolbarModule.container.querySelector('.ql-richfile-attach');
        if (!button) return;

        if (activeAttachPopover && activeAttachPopover.button === button) {
            closeAttachPopover();
            return;
        }
        closeAttachPopover();

        var formats = button.closest('.ql-formats');
        formats.classList.add('rich-attach-anchor');

        var popover = document.createElement('div');
        popover.className = 'rich-attach-popover';

        var options = [
            { label: '사진', icon: '🖼', accept: 'image/*' },
            { label: '동영상', icon: '🎬', accept: 'video/*' },
            { label: '파일', icon: '📎', accept: '*/*' },
            { label: '링크 카드', icon: '🔗', embed: true }
        ];
        options.forEach(function (opt) {
            var item = document.createElement('button');
            item.type = 'button';
            item.className = 'rich-attach-popover-item';
            item.innerHTML = '<span class="rich-attach-popover-icon">' + opt.icon + '</span>' + opt.label;
            item.addEventListener('click', function (e) {
                e.preventDefault();
                closeAttachPopover();
                if (opt.embed) {
                    insertEmbedCard(quill);
                } else {
                    triggerUpload(quill, opt.accept);
                }
            });
            popover.appendChild(item);
        });

        formats.appendChild(popover);
        activeAttachPopover = { popover: popover, button: button, formats: formats };

        // 팝오버 바깥을 클릭하거나 Esc를 누르면 닫는다 - 리스너는 이번에 연 팝오버 하나에만
        // 걸었다가 닫힐 때 같이 떼어내서(document에 계속 쌓이지 않게) 누적을 막는다.
        function onDocClick(e) {
            if (!popover.contains(e.target) && e.target !== button) {
                closeAttachPopover();
            }
        }
        function onKeydown(e) {
            if (e.key === 'Escape') closeAttachPopover();
        }
        // 이 클릭 자체(버튼을 누른 클릭)로 바로 닫히지 않도록 다음 이벤트 루프부터 리스닝한다.
        setTimeout(function () {
            document.addEventListener('click', onDocClick);
            document.addEventListener('keydown', onKeydown);
        }, 0);
        activeAttachPopover.cleanup = function () {
            document.removeEventListener('click', onDocClick);
            document.removeEventListener('keydown', onKeydown);
        };
    }

    function closeAttachPopover() {
        if (!activeAttachPopover) return;
        activeAttachPopover.cleanup();
        activeAttachPopover.popover.remove();
        activeAttachPopover.formats.classList.remove('rich-attach-anchor');
        activeAttachPopover = null;
    }

    function setupAttachPopover() {
        // 팝오버 DOM 자체는 toggleAttachPopover가 열릴 때마다 새로 만들고 닫힐 때 지우므로,
        // 여기서는 별도 초기화가 필요 없다(핸들러 등록은 initRichEditor의 toolbar handlers에서 함).
    }

    // 드래그로 텍스트를 선택하면 선택 영역 위에 뜨는 디스코드/노션 스타일 플로팅 서식 툴바.
    // 상단 고정 툴바는 그대로 두고(발견성을 위해), 텍스트를 고르는 동안 커서를 툴바까지 옮기지
    // 않아도 되는 지름길로 덧붙인다.
    function setupSelectionToolbar(quill) {
        var bubble = document.createElement('div');
        bubble.className = 'rich-selection-toolbar';
        bubble.hidden = true;
        document.body.appendChild(bubble);

        var actions = [
            { format: 'bold', html: '<b>B</b>', label: '굵게' },
            { format: 'italic', html: '<i>I</i>', label: '기울임' },
            { format: 'strike', html: '<s>S</s>', label: '취소선' },
            { format: 'link', html: '🔗', label: '링크' }
        ];

        var buttons = actions.map(function (action) {
            var btn = document.createElement('button');
            btn.type = 'button';
            btn.className = 'rich-selection-toolbar-btn';
            btn.innerHTML = action.html;
            btn.title = action.label;
            btn.setAttribute('aria-label', action.label);
            // mousedown에서 막지 않으면 클릭 순간 에디터가 blur되며 선택 영역이 풀려서
            // 정작 클릭 시점엔 서식을 적용할 대상이 없어진다.
            btn.addEventListener('mousedown', function (e) { e.preventDefault(); });
            btn.addEventListener('click', function () {
                var range = quill.getSelection();
                if (!range || range.length === 0) return;

                if (action.format === 'link') {
                    var current = quill.getFormat(range);
                    if (current.link) {
                        quill.format('link', false, 'user');
                    } else {
                        WebSchoolModal.prompt('연결할 링크 주소를 입력하세요.', { inputPlaceholder: 'https://...' }).then(function (url) {
                            if (!url) return;
                            quill.format('link', url, 'user');
                        });
                    }
                    return;
                }

                var isActive = !!quill.getFormat(range)[action.format];
                quill.format(action.format, !isActive, 'user');
                updateActiveStates(range);
            });
            bubble.appendChild(btn);
            return { el: btn, format: action.format };
        });

        function updateActiveStates(range) {
            var formats = quill.getFormat(range);
            buttons.forEach(function (b) {
                b.el.classList.toggle('is-active', !!formats[b.format]);
            });
        }

        function positionBubble(range) {
            var bounds = quill.getBounds(range.index, range.length);
            var editorRect = quill.root.getBoundingClientRect();
            bubble.hidden = false; // 크기를 재려면 먼저 보여야 한다
            var bubbleWidth = bubble.offsetWidth;
            var top = editorRect.top + window.scrollY + bounds.top - bubble.offsetHeight - 8;
            var left = editorRect.left + window.scrollX + bounds.left + bounds.width / 2 - bubbleWidth / 2;
            // 에디터 왼쪽 경계 밖으로 나가지 않게 살짝 보정
            left = Math.max(editorRect.left + window.scrollX, left);
            bubble.style.top = top + 'px';
            bubble.style.left = left + 'px';
            updateActiveStates(range);
        }

        quill.on('selection-change', function (range, oldRange, source) {
            if (range && range.length > 0) {
                positionBubble(range);
            } else {
                bubble.hidden = true;
            }
        });

        // 스크롤하면 좌표가 다 어긋나므로(재계산 대신) 간단히 숨긴다.
        window.addEventListener('scroll', function () {
            if (!bubble.hidden) bubble.hidden = true;
        }, true);
    }

    // 마크다운을 몰라도 되게 화면에 따로 안내하진 않지만, 아는 사람은 타이핑만으로 서식을 바로
    // 적용할 수 있게 한다(디스코드처럼) - **굵게**, *기울임*, ~~취소선~~, `코드`, "## "/"### "
    // 제목, "> " 인용. 목록("- ", "1. ")은 Quill 기본 키보드 모듈에 이미 내장돼 있어 따로 구현할
    // 필요가 없다.
    function setupMarkdownShortcuts(quill) {
        registerBlockShortcuts(quill);

        // quill.getSelection()으로 "지금 커서 위치"를 물어보는 대신, 방금 들어온 delta 자체에서
        // 삽입 위치를 직접 계산한다 - 실제 타이핑 중엔 브라우저가 네이티브 커서를 이미 옮겨둔
        // 뒤라 getSelection()도 대개 맞지만, 그 타이밍에 100% 기대지 않는 편이 더 안전하다
        // (delta는 이번 변경이 정확히 어디서 일어났는지 그 자체로 알려주는 유일한 근거다).
        quill.on('text-change', function (delta, oldDelta, source) {
            if (source !== 'user') return;
            var ops = delta.ops || [];
            var index = 0;
            var insertedChar = null;
            var insertedAt = -1;
            ops.forEach(function (op) {
                if (op.retain) {
                    index += (typeof op.retain === 'number' ? op.retain : 1);
                } else if (typeof op.insert === 'string') {
                    if (op.insert.length === 1) {
                        insertedChar = op.insert;
                        insertedAt = index;
                    }
                    index += op.insert.length;
                }
            });
            if (insertedChar === null || '*`~'.indexOf(insertedChar) === -1) return;
            applyInlineMarkdown(quill, insertedChar, insertedAt + 1);
        });
    }

    function registerBlockShortcuts(quill) {
        // 제목(header) 드롭다운을 글자 크기(px) 선택으로 교체하면서 ##/### 단축키도 함께
        // 제거했다 - 툴바에 없는 서식을 단축키로만 남겨두면 발견 불가능한 숨은 기능이 된다.
        quill.keyboard.addBinding({ key: ' ', collapsed: true }, { prefix: /^>$/ }, function (range, context) {
            this.quill.deleteText(range.index - 1, 1, 'user');
            this.quill.formatLine(range.index - 1, 1, 'blockquote', true, 'user');
            this.quill.setSelection(range.index - 1, 0, 'silent');
            return false;
        });
    }

    function applyInlineMarkdown(quill, triggerChar, cursor) {
        var lineInfo = quill.getLine(cursor);
        if (!lineInfo || !lineInfo[0]) return;
        var lineStart = cursor - lineInfo[1];
        var textBefore = quill.getText(lineStart, cursor - lineStart);

        var rules = triggerChar === '`'
            ? [{ regex: /`([^`]+)`$/, format: 'code' }]
            : triggerChar === '~'
                ? [{ regex: /~~([^~]+)~~$/, format: 'strike' }]
                : [
                    { regex: /\*\*([^*]+)\*\*$/, format: 'bold' },
                    { regex: /(^|[^*])\*([^*]+)\*$/, format: 'italic', group: 2, prefixGroup: 1 }
                ];

        for (var i = 0; i < rules.length; i++) {
            var rule = rules[i];
            var m = rule.regex.exec(textBefore);
            if (!m) continue;
            var group = rule.group || 1;
            var content = m[group];
            if (!content) continue;

            var prefixLen = rule.prefixGroup ? m[rule.prefixGroup].length : 0;
            var matchStart = lineStart + m.index + prefixLen;
            var matchLength = m[0].length - prefixLen;

            quill.deleteText(matchStart, matchLength, 'user');
            quill.insertText(matchStart, content, rule.format, true, 'user');
            quill.setSelection(matchStart + content.length, 0, 'user');
            // 커서가 방금 삽입한 서식 글자 바로 뒤에 있으면 그 서식이 "펜 끝에 묻어" 다음
            // 타이핑까지 계속 이어진다(Quill의 일반적인 동작) - **중요** 뒤에 이어 쓴 일반
            // 문장까지 전부 굵게 나오는 버그로 실제 확인됨. 커서가 여전히 collapsed일 때
            // format(name, false)를 걸면 이미 삽입된 글자는 그대로 두고 "다음에 입력할 서식"만
            // 끈다.
            quill.format(rule.format, false, 'user');
            return;
        }
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
            { inputPlaceholder: '예) https://.../posts/xxxx-... 또는 /school/comments/xxxx-...' }
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
