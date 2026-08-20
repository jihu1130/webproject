// 버그수정 프롬포트 요청 - 커스텀 디자인 시스템을 곳곳에서 쓰면서 정작 삭제/신고 확인창만
// 브라우저 기본 confirm()/alert()로 튀어나와서 톤이 깨졌다(74곳). theme.css 토큰 기반 모달로
// 교체 - 다크모드도 자동으로 따라간다(별도 다크 처리 불필요, var() 참조라서).
//
// 사용법:
//   WebSchoolModal.confirm('정말 삭제할까요?').then(function (ok) { if (ok) { ... } });
//   await WebSchoolModal.confirm('...', { danger: true }) // 위험 액션은 확인 버튼을 danger 톤으로
//   await WebSchoolModal.alert('처리됐습니다.');
//
// prompt()는 이 모달로 옮기지 않았다(입력창까지 필요한 곳은 신고 사유 하나뿐이라 별도로 다룸,
// WebSchoolModal.prompt() 참고).
(function () {
    var overlay = null;
    var styleInjected = false;

    function injectStyle() {
        if (styleInjected) return;
        styleInjected = true;
        var style = document.createElement('style');
        style.textContent = [
            '.ws-modal-overlay {',
            '  position: fixed; inset: 0; background: rgba(17, 16, 36, 0.45);',
            '  display: flex; align-items: center; justify-content: center;',
            '  z-index: 2000; padding: 20px; opacity: 0; transition: opacity 0.15s ease;',
            '}',
            '.ws-modal-overlay.ws-modal-open { opacity: 1; }',
            '.ws-modal-card {',
            '  background: var(--surface); color: var(--ink); border-radius: 16px;',
            '  box-shadow: 0 24px 60px rgba(20, 18, 45, 0.35); max-width: 380px; width: 100%;',
            '  padding: 24px; transform: translateY(8px); transition: transform 0.15s ease;',
            '  font-family: inherit;',
            '}',
            '.ws-modal-overlay.ws-modal-open .ws-modal-card { transform: translateY(0); }',
            '.ws-modal-message { font-size: 0.95rem; line-height: 1.55; margin: 0 0 20px; white-space: pre-wrap; }',
            '.ws-modal-input {',
            '  width: 100%; box-sizing: border-box; padding: 10px 12px; border-radius: 9px;',
            '  border: 1.5px solid var(--border); background: var(--surface); color: var(--ink);',
            '  font-family: inherit; font-size: 0.9rem; margin: -8px 0 20px; outline: none;',
            '}',
            '.ws-modal-input:focus { border-color: var(--brand-1); }',
            '.ws-modal-actions { display: flex; justify-content: flex-end; gap: 8px; }',
            '.ws-modal-btn {',
            '  padding: 9px 18px; border-radius: 9px; font-weight: 700; font-size: 0.88rem;',
            '  cursor: pointer; border: 1.5px solid var(--border); background: var(--surface);',
            '  color: var(--ink); transition: background 0.15s ease;',
            '}',
            '.ws-modal-btn:hover { background: var(--surface-soft, #f8f9fc); }',
            '.ws-modal-btn-primary { background: var(--brand-gradient); border-color: transparent; color: #fff; }',
            '.ws-modal-btn-primary:hover { filter: brightness(1.06); }',
            '.ws-modal-btn-danger { background: var(--danger); border-color: transparent; color: #fff; }',
            '.ws-modal-btn-danger:hover { filter: brightness(1.08); }'
        ].join('\n');
        document.head.appendChild(style);
    }

    // options: { danger: boolean, confirmText: string, cancelText: string, input: boolean, inputPlaceholder: string }
    function open(message, options) {
        injectStyle();
        options = options || {};

        return new Promise(function (resolve) {
            overlay = document.createElement('div');
            overlay.className = 'ws-modal-overlay';

            var card = document.createElement('div');
            card.className = 'ws-modal-card';

            var messageEl = document.createElement('p');
            messageEl.className = 'ws-modal-message';
            messageEl.textContent = message;
            card.appendChild(messageEl);

            var inputEl = null;
            if (options.input) {
                inputEl = document.createElement('input');
                inputEl.type = 'text';
                inputEl.className = 'ws-modal-input';
                if (options.inputPlaceholder) inputEl.placeholder = options.inputPlaceholder;
                // 공유 링크 복사처럼 "미리 채워진 값을 그대로 복사"시키는 용도 - placeholder(회색 안내
                // 문구)와 달리 실제 입력값으로 들어가고 포커스 시 자동 선택된다.
                if (options.inputValue) inputEl.value = options.inputValue;
                if (options.inputReadonly) inputEl.readOnly = true;
                card.appendChild(inputEl);
            }

            var actions = document.createElement('div');
            actions.className = 'ws-modal-actions';

            function close(result) {
                overlay.classList.remove('ws-modal-open');
                setTimeout(function () {
                    if (overlay && overlay.parentNode) overlay.parentNode.removeChild(overlay);
                    overlay = null;
                }, 150);
                document.removeEventListener('keydown', onKeydown);
                resolve(result);
            }

            function onKeydown(e) {
                if (e.key === 'Escape') close(options.alertOnly ? undefined : false);
                if (e.key === 'Enter' && options.input) close(inputEl.value);
            }

            if (!options.alertOnly) {
                var cancelBtn = document.createElement('button');
                cancelBtn.type = 'button';
                cancelBtn.className = 'ws-modal-btn';
                cancelBtn.textContent = options.cancelText || '취소';
                cancelBtn.addEventListener('click', function () { close(options.input ? null : false); });
                actions.appendChild(cancelBtn);
            }

            var confirmBtn = document.createElement('button');
            confirmBtn.type = 'button';
            confirmBtn.className = 'ws-modal-btn ' + (options.danger ? 'ws-modal-btn-danger' : 'ws-modal-btn-primary');
            confirmBtn.textContent = options.confirmText || (options.alertOnly ? '확인' : '확인');
            confirmBtn.addEventListener('click', function () {
                close(options.alertOnly ? undefined : (options.input ? inputEl.value : true));
            });
            actions.appendChild(confirmBtn);

            card.appendChild(actions);
            overlay.appendChild(card);
            document.body.appendChild(overlay);
            document.addEventListener('keydown', onKeydown);

            // 오버레이 바깥 클릭 = 취소(alert는 바깥 클릭으로 안 닫음 - 사용자가 반드시 확인을 읽게)
            overlay.addEventListener('click', function (e) {
                if (e.target === overlay && !options.alertOnly) close(options.input ? null : false);
            });

            requestAnimationFrame(function () {
                overlay.classList.add('ws-modal-open');
                (inputEl || confirmBtn).focus();
                if (inputEl && options.inputValue) inputEl.select();
            });
        });
    }

    window.WebSchoolModal = {
        confirm: function (message, options) {
            return open(message, options || {});
        },
        alert: function (message) {
            return open(message, { alertOnly: true });
        },
        // resolve(null) = 취소, resolve('') = 빈 값으로 확인(기존 prompt()의 "비워두고 확인"과 동일 의미)
        prompt: function (message, options) {
            options = options || {};
            options.input = true;
            return open(message, options);
        }
    };

    // 템플릿 쪽 일괄 적용용 - 예전엔 <form onsubmit="return confirm('...')">를 폼마다 하나하나
    // 인라인으로 박아뒀는데(admin/*-list.html, my-activity.html 등 8개 파일 40곳), 전부 이 모달로
    // 바꾸면서 하드코딩된 인라인 스크립트 대신 data-confirm 속성 하나로 통일했다 - 새 폼을 추가할
    // 때도 이 속성만 붙이면 자동으로 모달이 붙는다. data-confirm-danger="true"면 확인 버튼이 danger
    // 톤(삭제/차단/탈퇴/정지처럼 되돌리기 어려운 조치용).
    document.addEventListener('submit', function (e) {
        var form = e.target;
        if (!(form instanceof HTMLFormElement) || !form.hasAttribute('data-confirm')) return;
        if (form.dataset.confirmed === 'true') return; // 이미 확인받고 재제출하는 경우 통과

        e.preventDefault();
        WebSchoolModal.confirm(form.getAttribute('data-confirm'), {
            danger: form.getAttribute('data-confirm-danger') === 'true'
        }).then(function (ok) {
            if (ok) {
                form.dataset.confirmed = 'true';
                form.requestSubmit ? form.requestSubmit() : form.submit();
            }
        });
    });
})();
