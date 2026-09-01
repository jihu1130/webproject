// 설문(투표) 결과 위젯 - post/detail.html, 캘린더 한마디 상세에서 공용으로 쓴다. 게시글/한마디에
// 설문이 첨부돼 있으면 GET /polls/by-post/{id} 또는 /polls/by-comment/{id}로 조회해서 렌더링하고,
// 투표는 POST /polls/{id}/vote로 보낸다(WebSchoolCsrf 헬퍼로 CSRF 토큰 동봉 - csrf.js를 먼저
// 로드해야 함). 설문이 없는 게시글/한마디에서는 204를 받아 위젯 자체를 숨긴다.
function initPollWidget(container, fetchUrl) {
    if (!container) {
        return;
    }

    function formatDeadline(isoLike) {
        var d = new Date(isoLike);
        if (isNaN(d.getTime())) {
            return isoLike;
        }
        var pad = function (n) { return String(n).padStart(2, '0'); };
        return (d.getMonth() + 1) + '/' + d.getDate() + ' ' + pad(d.getHours()) + ':' + pad(d.getMinutes());
    }

    function escapeHtml(text) {
        var div = document.createElement('div');
        div.textContent = text == null ? '' : text;
        return div.innerHTML;
    }

    function render(poll) {
        if (!poll) {
            container.hidden = true;
            container.innerHTML = '';
            return;
        }
        container.hidden = false;

        var totalVotes = poll.options.reduce(function (sum, opt) { return sum + opt.voteCount; }, 0);
        var inputType = poll.allowMultiple ? 'checkbox' : 'radio';

        var html = '<div class="poll-widget-question">' + escapeHtml(poll.question) + '</div>';
        html += '<div class="poll-widget-meta">' + poll.totalVoters + '명 참여';
        if (poll.anonymous) {
            html += ' · 익명 투표';
        }
        if (poll.expiresAt) {
            html += ' · ' + (poll.expired ? '마감됨' : '마감 ' + formatDeadline(poll.expiresAt));
        }
        html += '</div>';

        html += '<div class="poll-widget-options">';
        poll.options.forEach(function (opt) {
            var pct = totalVotes > 0 ? Math.round((opt.voteCount / totalVotes) * 100) : 0;
            html += '<label class="poll-widget-option' + (opt.votedByMe ? ' voted' : '') + '">'
                + '<input type="' + inputType + '" name="pollOption" value="' + opt.id + '"' + (opt.votedByMe ? ' checked' : '') + '>'
                + '<span class="poll-widget-option-label">' + escapeHtml(opt.label) + '</span>'
                + '<span class="poll-widget-option-bar"><span style="width:' + pct + '%"></span></span>'
                + '<span class="poll-widget-option-count">' + opt.voteCount + '표 (' + pct + '%)</span>'
                + '</label>';
        });
        html += '</div>';

        if (poll.allowCustomOption) {
            html += '<div class="poll-widget-custom-row">'
                + '<input type="text" class="poll-widget-custom-input" placeholder="기타 (직접 입력해서 추가)" maxlength="100">'
                + '</div>';
        }

        if (!poll.expired) {
            html += '<button type="button" class="poll-widget-submit btn btn-primary">투표하기</button>';
        }
        container.innerHTML = html;

        if (poll.expired) {
            container.querySelectorAll('input[name="pollOption"]').forEach(function (input) { input.disabled = true; });
            return;
        }

        container.querySelector('.poll-widget-submit').addEventListener('click', function () {
            var selected = Array.prototype.slice
                .call(container.querySelectorAll('input[name="pollOption"]:checked'))
                .map(function (input) { return Number(input.value); });
            var customInput = container.querySelector('.poll-widget-custom-input');
            var customText = customInput ? customInput.value.trim() : '';

            fetch('/polls/' + poll.id + '/vote', {
                method: 'POST',
                headers: Object.assign({ 'Content-Type': 'application/json' }, WebSchoolCsrf.headers()),
                body: JSON.stringify({ optionIds: selected, customOptionText: customText })
            }).then(function (res) {
                if (!res.ok) {
                    return res.json().then(function (body) {
                        throw new Error(body.error || '투표에 실패했습니다.');
                    });
                }
                return res.json();
            }).then(render).catch(function (err) {
                alert(err.message);
            });
        });
    }

    fetch(fetchUrl, { headers: WebSchoolCsrf.headers() })
        .then(function (res) {
            if (res.status === 204 || !res.ok) {
                return null;
            }
            return res.json();
        })
        .then(render)
        .catch(function () {
            container.hidden = true;
        });
}
