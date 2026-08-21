// 게시물 상세 페이지: 댓글 CRUD(AJAX) + 신고 버튼
document.addEventListener('DOMContentLoaded', function () {
    var page = document.getElementById('postDetailPage');
    if (!page) return;

    var postId = page.getAttribute('data-post-id');
    var isLoggedIn = !!document.getElementById('postCommentForm');
    var isAnonymousPost = page.getAttribute('data-post-category') === 'ANONYMOUS';
    var isQnaPost = page.getAttribute('data-post-category') === 'QNA';
    var isPostAuthor = page.getAttribute('data-post-mine') === 'true';
    var LABEL = isAnonymousPost ? '답변' : '댓글';

    function escapeHtml(str) {
        var div = document.createElement('div');
        div.textContent = str == null ? '' : str;
        return div.innerHTML.replace(/"/g, '&quot;').replace(/'/g, '&#39;');
    }

    WebSchoolTimeago.apply(document.getElementById('postCreatedAt'));

    // ---- 댓글 ----
    function loadComments() {
        var list = document.getElementById('postCommentList');
        list.innerHTML = '<div class="post-comment-empty">불러오는 중...</div>';

        fetch('/posts/' + postId + '/comments')
            .then(function (res) { return res.json(); })
            .then(renderComments)
            .catch(function () {
                list.innerHTML = '<div class="post-comment-empty">' + LABEL + '을 불러오지 못했습니다.</div>';
            });
    }

    function renderComments(comments) {
        var list = document.getElementById('postCommentList');

        if (!comments || comments.length === 0) {
            list.innerHTML = '<div class="post-comment-empty">아직 ' + LABEL + '이 없어요. 첫 ' + LABEL + '을 남겨보세요!</div>';
            return;
        }

        list.innerHTML = '';
        comments.forEach(function (c) {
            var item = document.createElement('div');
            item.className = 'post-comment-item' + (c.blind ? ' post-comment-item-blind' : '')
                + (c.accepted ? ' post-comment-item-accepted' : '');

            var editedBadge = c.edited ? ' <span class="post-comment-edited">(수정됨)</span>' : '';
            var blindBadge = c.blind ? ' <span class="post-comment-blind-badge">블라인드</span>' : '';
            var acceptedBadge = c.accepted ? ' <span class="post-comment-accepted-badge"><i class="fa-solid fa-check"></i> 채택된 답변</span>' : '';
            var nicknameHtml = c.authorLinkable
                ? '<a href="/users/' + c.authorId + '" class="post-comment-nickname">' + escapeHtml(c.nickname) + '</a>'
                : '<span class="post-comment-nickname">' + escapeHtml(c.nickname) + '</span>';
            var canBlock = !c.mine && isLoggedIn && !isAnonymousPost && c.authorLinkable;
            // QNA 답변 채택 버튼 - 질문 작성자에게만, 블라인드/삭제 예정 댓글이 아닌 경우에 노출
            // 버그수정 프롬포트 요청 - 아이콘 전용 버튼에 title만 있고 aria-label이 없어서 스크린리더
            // 사용자에게 "버튼"으로만 들렸다. title과 동일한 문구를 aria-label로도 명시한다.
            var acceptLabel = c.accepted ? '채택 취소' : '답변으로 채택';
            var acceptBtnHtml = (isQnaPost && isPostAuthor && !c.blind)
                ? '<button type="button" class="post-comment-accept-btn' + (c.accepted ? ' active' : '') + '" title="' +
                  acceptLabel + '" aria-label="' + acceptLabel + '"><i class="fa-solid fa-check"></i></button>'
                : '';
            var actionsHtml = acceptBtnHtml + (c.mine
                ? '<button type="button" class="post-comment-edit-btn" title="수정" aria-label="수정"><i class="fa-solid fa-pen"></i></button>' +
                  '<button type="button" class="post-comment-delete-btn" title="삭제" aria-label="삭제"><i class="fa-solid fa-xmark"></i></button>'
                : (isLoggedIn
                    ? ((canBlock ? '<button type="button" class="post-comment-block-btn" title="차단" aria-label="차단"><i class="fa-solid fa-user-slash"></i></button>' : '') +
                       (c.reportedByMe
                        ? '<button type="button" class="post-comment-report-btn" title="이미 신고했어요" aria-label="이미 신고했어요" disabled><i class="fa-solid fa-flag"></i></button>'
                        : '<button type="button" class="post-comment-report-btn" title="신고" aria-label="신고"><i class="fa-solid fa-flag"></i></button>'))
                    : ''));
            var likeBookmarkHtml = isLoggedIn
                ? '<button type="button" class="post-comment-like-btn' + (c.likedByMe ? ' active' : '') + '" title="좋아요" aria-label="좋아요">' +
                      '<i class="fa-solid fa-heart"></i> <span class="post-comment-like-count">' + c.likeCount + '</span></button>' +
                  '<button type="button" class="post-comment-bookmark-btn' + (c.bookmarkedByMe ? ' active' : '') + '" title="북마크" aria-label="북마크">' +
                      '<i class="fa-solid fa-bookmark"></i></button>'
                : '';

            item.innerHTML =
                '<div class="post-comment-item-header">' +
                    nicknameHtml +
                    '<span class="post-comment-time"><span class="post-comment-time-value">' + escapeHtml(c.createdAt) + '</span>' + editedBadge + blindBadge + acceptedBadge + '</span>' +
                    likeBookmarkHtml +
                    actionsHtml +
                '</div>' +
                '<div class="post-comment-content">' + escapeHtml(c.content) + '</div>';

            WebSchoolTimeago.apply(item.querySelector('.post-comment-time-value'));

            var editBtn = item.querySelector('.post-comment-edit-btn');
            if (editBtn) {
                editBtn.addEventListener('click', function () { startEditComment(item, c); });
            }

            var delBtn = item.querySelector('.post-comment-delete-btn');
            if (delBtn) {
                delBtn.addEventListener('click', function () { deleteComment(c.id); });
            }

            var reportBtn = item.querySelector('.post-comment-report-btn');
            if (reportBtn) {
                reportBtn.addEventListener('click', function () { reportComment(c.id, reportBtn); });
            }

            var blockBtn = item.querySelector('.post-comment-block-btn');
            if (blockBtn) {
                blockBtn.addEventListener('click', function () { blockUser(c.authorId, c.nickname); });
            }

            var acceptBtn = item.querySelector('.post-comment-accept-btn');
            if (acceptBtn) {
                acceptBtn.addEventListener('click', function () { toggleAcceptAnswer(c.id); });
            }

            var likeBtn = item.querySelector('.post-comment-like-btn');
            if (likeBtn) {
                likeBtn.addEventListener('click', function () { toggleCommentLike(c.id, likeBtn); });
            }

            var bookmarkBtn = item.querySelector('.post-comment-bookmark-btn');
            if (bookmarkBtn) {
                bookmarkBtn.addEventListener('click', function () { toggleCommentBookmark(c.id, bookmarkBtn); });
            }

            list.appendChild(item);
        });
    }

    function startEditComment(item, c) {
        var contentEl = item.querySelector('.post-comment-content');
        contentEl.innerHTML =
            '<form class="post-comment-edit-form">' +
                '<input type="text" class="post-comment-edit-input" maxlength="500" value="' + escapeHtml(c.content) + '">' +
                '<button type="submit">저장</button>' +
                '<button type="button" class="post-comment-edit-cancel">취소</button>' +
            '</form>';

        var input = contentEl.querySelector('.post-comment-edit-input');
        input.focus();
        input.setSelectionRange(input.value.length, input.value.length);

        contentEl.querySelector('.post-comment-edit-cancel').addEventListener('click', function () {
            loadComments();
        });

        contentEl.querySelector('.post-comment-edit-form').addEventListener('submit', function (e) {
            e.preventDefault();
            var newContent = input.value.trim();
            if (!newContent) return;

            fetch('/posts/' + postId + '/comments/' + c.id, {
                method: 'PUT',
                headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                body: 'content=' + encodeURIComponent(newContent)
            })
                .then(function (res) {
                    if (!res.ok) return res.json().then(function (body) { throw new Error(body.error || '수정 실패'); });
                    loadComments();
                })
                .catch(function (err) {
                    WebSchoolModal.alert(err.message || (LABEL + ' 수정에 실패했습니다.'));
                });
        });
    }

    async function deleteComment(id) {
        if (!(await WebSchoolModal.confirm(LABEL + '을 삭제할까요?', { danger: true }))) return;

        fetch('/posts/' + postId + '/comments/' + id, { method: 'DELETE' })
            .then(function (res) {
                if (!res.ok) throw new Error('삭제 실패');
                loadComments();
            })
            .catch(function () {
                WebSchoolModal.alert(LABEL + ' 삭제에 실패했습니다.');
            });
    }

    async function reportComment(id, btn) {
        if (!(await WebSchoolModal.confirm('이 ' + LABEL + '을 신고하시겠어요?'))) return;
        var reason = (await WebSchoolModal.prompt(
            '신고 사유를 입력해주세요 (선택 사항입니다. 비워두고 확인해도 신고가 접수됩니다).',
            { inputPlaceholder: '신고 사유 (선택)' }
        )) || '';

        fetch('/posts/' + postId + '/comments/' + id + '/report', {
            method: 'POST',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
            body: 'reason=' + encodeURIComponent(reason)
        })
            .then(function (res) {
                return res.json().then(function (body) {
                    if (!res.ok) throw new Error(body.error || '신고 처리에 실패했습니다.');
                    return body;
                });
            })
            .then(function (body) {
                WebSchoolModal.alert(body.blind ? ('신고가 접수되었습니다. 신고 누적으로 ' + LABEL + '이 블라인드 처리되었습니다.') : '신고가 접수되었습니다.');
                loadComments();
            })
            .catch(function (err) {
                WebSchoolModal.alert(err.message || '신고 처리에 실패했습니다.');
                if (btn) {
                    btn.disabled = true;
                }
            });
    }

    // ---- 사용자 차단 ----
    // 익명 게시물에는 차단 버튼 자체가 렌더링되지 않으므로(canBlock 계산 참고) 여기선 별도로
    // isAnonymousPost를 다시 확인하지 않는다.
    async function blockUser(targetId, nickname) {
        if (!(await WebSchoolModal.confirm(
            nickname + '님을 차단하시겠어요? 이 사람은 회원님의 모든 게시글/댓글에 댓글을 달 수 없게 돼요.',
            { danger: true }
        ))) return;
        var daysInput = await WebSchoolModal.prompt(
            '차단 기간(일)을 입력하세요. 영구 차단은 비워두고 확인을 누르세요.',
            { inputPlaceholder: '예: 7 (비워두면 영구 차단)' }
        );
        if (daysInput === null) return;
        var days = daysInput.trim() === '' ? '' : parseInt(daysInput, 10);

        var body = 'targetId=' + encodeURIComponent(targetId);
        if (days) body += '&durationDays=' + encodeURIComponent(days);

        fetch('/users/blocks', {
            method: 'POST',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
            body: body
        })
            .then(function (res) {
                return res.json().then(function (b) {
                    if (!res.ok) throw new Error(b.error || '차단 처리에 실패했습니다.');
                    return b;
                });
            })
            .then(function () {
                WebSchoolModal.alert(nickname + '님을 차단했습니다.');
                loadComments();
            })
            .catch(function (err) {
                WebSchoolModal.alert(err.message || '차단 처리에 실패했습니다.');
            });
    }

    var postAuthorBlockBtn = document.getElementById('postAuthorBlockBtn');
    if (postAuthorBlockBtn) {
        postAuthorBlockBtn.addEventListener('click', function () {
            var targetId = postAuthorBlockBtn.getAttribute('data-author-id');
            var nickname = document.querySelector('.post-detail-meta .post-list-nickname');
            blockUser(targetId, nickname ? nickname.textContent : '작성자');
        });
    }

    // ---- QNA 답변 채택 ----
    function toggleAcceptAnswer(id) {
        fetch('/posts/' + postId + '/comments/' + id + '/accept', { method: 'POST' })
            .then(function (res) {
                return res.json().then(function (body) {
                    if (!res.ok) throw new Error(body.error || '채택 처리에 실패했습니다.');
                    return body;
                });
            })
            .then(function () {
                loadComments(); // 채택 상태는 댓글 목록 전체의 정렬/배지에 영향을 주므로 통째로 다시 불러온다
            })
            .catch(function (err) {
                WebSchoolModal.alert(err.message || '채택 처리에 실패했습니다.');
            });
    }

    // ---- 댓글 좋아요/북마크 ----
    function toggleCommentLike(id, btn) {
        fetch('/posts/' + postId + '/comments/' + id + '/like', { method: 'POST' })
            .then(function (res) {
                if (!res.ok) throw new Error('처리 실패');
                return res.json();
            })
            .then(function (body) {
                btn.classList.toggle('active', body.liked);
                btn.querySelector('.post-comment-like-count').textContent = body.likeCount;
            })
            .catch(function () {
                WebSchoolModal.alert('좋아요 처리에 실패했습니다.');
            });
    }

    function toggleCommentBookmark(id, btn) {
        fetch('/posts/' + postId + '/comments/' + id + '/bookmark', { method: 'POST' })
            .then(function (res) {
                if (!res.ok) throw new Error('처리 실패');
                return res.json();
            })
            .then(function (body) {
                btn.classList.toggle('active', body.bookmarked);
            })
            .catch(function () {
                WebSchoolModal.alert('북마크 처리에 실패했습니다.');
            });
    }

    var commentForm = document.getElementById('postCommentForm');
    if (commentForm) {
        commentForm.addEventListener('submit', function (e) {
            e.preventDefault();
            var input = document.getElementById('postCommentInput');
            var content = input.value.trim();
            if (!content) return;

            fetch('/posts/' + postId + '/comments', {
                method: 'POST',
                headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                body: 'content=' + encodeURIComponent(content)
            })
                .then(function (res) {
                    if (!res.ok) return res.json().then(function (body) { throw new Error(body.error || '등록 실패'); });
                    return res.json();
                })
                .then(function () {
                    input.value = '';
                    loadComments();
                })
                .catch(function (err) {
                    WebSchoolModal.alert(err.message || (LABEL + ' 등록에 실패했습니다.'));
                });
        });
    }

    loadComments();

    // ---- 신고 ----
    var reportBtn = document.getElementById('reportBtn');
    if (reportBtn) {
        reportBtn.addEventListener('click', async function () {
            if (!(await WebSchoolModal.confirm('이 게시물을 신고하시겠어요?'))) return;
            var reason = (await WebSchoolModal.prompt(
                '신고 사유를 입력해주세요 (선택 사항입니다. 비워두고 확인해도 신고가 접수됩니다).',
                { inputPlaceholder: '신고 사유 (선택)' }
            )) || '';

            fetch('/posts/' + postId + '/report', {
                method: 'POST',
                headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                body: 'reason=' + encodeURIComponent(reason)
            })
                .then(function (res) {
                    return res.json().then(function (body) {
                        if (!res.ok) throw new Error(body.error || '신고 처리에 실패했습니다.');
                        return body;
                    });
                })
                .then(function (body) {
                    if (body.blind) {
                        WebSchoolModal.alert('신고가 접수되었습니다. 신고 누적으로 게시물이 블라인드 처리되었습니다.')
                            .then(function () { location.href = '/posts'; });
                        return;
                    }
                    WebSchoolModal.alert('신고가 접수되었습니다.');
                    reportBtn.disabled = true;
                    reportBtn.innerHTML = '<i class="fa-solid fa-flag"></i> 신고 완료';
                })
                .catch(function (err) {
                    WebSchoolModal.alert(err.message || '신고 처리에 실패했습니다.');
                });
        });
    }

    // ---- 게시물 좋아요/북마크 ----
    var postLikeBtn = document.getElementById('postLikeBtn');
    if (postLikeBtn) {
        postLikeBtn.addEventListener('click', function () {
            fetch('/posts/' + postId + '/like', { method: 'POST' })
                .then(function (res) {
                    if (!res.ok) throw new Error('처리 실패');
                    return res.json();
                })
                .then(function (body) {
                    postLikeBtn.classList.toggle('active', body.liked);
                    document.getElementById('postLikeCount').textContent = body.likeCount;
                })
                .catch(function () {
                    WebSchoolModal.alert('좋아요 처리에 실패했습니다.');
                });
        });
    }

    var postBookmarkBtn = document.getElementById('postBookmarkBtn');
    if (postBookmarkBtn) {
        postBookmarkBtn.addEventListener('click', function () {
            fetch('/posts/' + postId + '/bookmark', { method: 'POST' })
                .then(function (res) {
                    if (!res.ok) throw new Error('처리 실패');
                    return res.json();
                })
                .then(function (body) {
                    postBookmarkBtn.classList.toggle('active', body.bookmarked);
                    postBookmarkBtn.querySelector('span').textContent = body.bookmarked ? '북마크됨' : '북마크';
                })
                .catch(function () {
                    WebSchoolModal.alert('북마크 처리에 실패했습니다.');
                });
        });
    }
});
