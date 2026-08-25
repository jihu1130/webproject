# TODO — 앞으로 할 일

이 파일은 CLAUDE.md에 흩어져 있던 백로그와, 사용자가 다음 작업으로 지정한 새 기능
요청을 한곳에 모아둔 목록이다. 완료한 항목은 지우지 말고 체크만 하고, 새 요청은
날짜와 함께 이 파일 위쪽(우선순위 높은 쪽)에 추가할 것.

---

## -3. 버그 리포트 상세 페이지 (2026-08-24 요청, ✅ 완료 2026-08-25)

**요구사항 원문**: "리포트 올리면 자세히 보기는 아직 안된거야?" — 버그 리포트 기능(제출 폼
`/bug-reports/new`, 관리자 목록 `/admin/bug-reports`) 자체는 완료됐지만, 지금은 제목/내용/
첨부/제출자 정보를 전부 목록 테이블에 인라인으로만 보여주고 있고 별도 상세 페이지가 없다.
공지사항 관리(`/admin/notices/{id}`, `admin/notice-detail.html`)와 동일한 패턴으로
`/admin/bug-reports/{id}` 전용 화면을 추가할 것 — 목록이 길어지거나 내용이 많아질 때를
대비해 리스트는 요약만, 상세는 별도 페이지로 분리.

- [x] `AdminBugReportController`에 `GET /admin/bug-reports/{id}` 상세 조회 추가
      (`BugReportService.getDetail()` 신규, 존재하지 않는 id는 공지 상세와 동일하게
      목록으로 리다이렉트)
- [x] `admin/bug-report-detail.html` 신규 템플릿(공지 상세 페이지 패턴 그대로 - breadcrumb +
      "관리자 목록으로" 링크 + `admin-detail-card`) - 첨부는 목록의 40px 썸네일 대신
      `admin-bug-report-detail-attachments`(그리드, 셀당 160px 높이) + 파일명으로 크게 표시
- [x] 목록의 제목 셀을 상세 페이지로 가는 링크로 변경(`admin/notice-list.html`의 제목 링크 패턴과
      동일하게 `admin-table-title` 클래스 + page/size 쿼리 유지)
- [x] 해결됨 표시/삭제 액션은 상세 페이지에도 동일 폼으로 추가(목록에서 바로 처리 가능한 기존
      방식도 그대로 유지)

브라우저로 검증: 버그 리포트 제출 → 목록에서 제목 클릭 → 상세 페이지 진입(배지/작성자
링크/시간/본문 정상 표시) → 상세에서 "해결됨으로 표시" → 목록에 반영 확인 → 상세 재진입 시
"처리중으로 되돌리기"로 바뀐 것 확인 → 상세에서 삭제(확인 모달) → 목록에서 사라지는 것까지
end-to-end 확인. 존재하지 않는 id로 직접 접근 시 목록으로 리다이렉트되는 것도 확인.

---

## -2. 오늘의 한마디 작성/수정 전용 페이지 분리 (2026-08-19 요청, ✅ 완료)

**요구사항 원문**: "오늘의 한마디는 작성 페이지 따로 보이는거 따로 해서 작성페이지를
추가해 수정페이지도 추가하면 좋겠어" — 바로 아래 -1번 항목에서 한마디에 리치
에디터(사진/동영상/파일/바로가기 카드 삽입)를 붙였더니, 캘린더 날짜 패널 안 좁은
공간에 인라인으로 끼워넣은 폼이 답답해졌다는 후속 요청.

- [x] 캘린더 날짜 패널의 인라인 작성 폼(`commentForm`)과 인라인 수정 폼
  (`startEditComment()`가 댓글 아이템 안에 동적으로 만들던 에디터)을 전부 제거하고,
  게시글 작성 화면(`post/form.html`)과 동일한 패턴의 전용 페이지로 분리했다:
  `GET/POST /school/comments/new`(작성), `GET/POST /school/comments/{id}/edit`(수정).
  새 템플릿 `templates/school/comment-form.html` 하나를 mode(create/edit)로 분기해서
  둘 다 렌더링(post.css의 `post-form-card`/`post-field`/`post-form-actions` 클래스
  재사용).
- [x] 작성 페이지는 어느 학교/날짜/학년/반에 쓰는 한마디인지(캘린더에서 이미 선택된
  컨텍스트)를 읽기 전용으로 보여주고 숨긴 필드로만 들고 다닌다 - 사용자가 그 값을
  바꿀 방법은 없다(URL을 직접 조작하지 않는 한).
- [x] 작성/수정 성공 시 각각 새로 만든/수정한 한마디의 퍼머링크(`GET
  /school/comments/{id}`, -1번 항목에서 이미 만든 캘린더 리다이렉트+하이라이트
  로직)로 리다이렉트하도록 해서, 게시물의 "바로가기" 임베드 카드나 공유 링크와
  똑같이 캘린더로 돌아가며 방금 쓴/고친 한마디가 자동으로 스크롤+하이라이트된다
  (별도 리다이렉트 로직을 새로 안 만들고 기존 걸 재사용).
- [x] 검증 실패(내용 없음 등) 시 `PostController.create()`/`update()`와 동일하게
  컨트롤러 메서드 안에서 직접 `IllegalArgumentException`을 잡아 에러 메시지와 함께
  같은 폼을 다시 그린다 - `SchoolController`의 클래스 레벨
  `@ExceptionHandler(IllegalArgumentException.class)`는 JSON API용이라 페이지
  요청에 그대로 걸리면 안 됨(JSON이 그대로 화면에 노출되는 문제).
  브라우저에서 빈 내용으로 직접 POST해서 에러 배너 렌더링 확인.
- [x] 캘린더 페이지(`calendar.html`)에서 Quill CDN/`rich-editor.js` 스크립트와
  인라인 에디터용 DOM을 전부 제거하고 "새 한마디 작성" 버튼(날짜 패널이 아니라
  새 페이지로 이동)만 남김, 댓글 목록의 수정 버튼도 JS 핸들러 대신 `/school/
  comments/{id}/edit`로 가는 `<a>` 링크로 교체. 브라우저로 작성→목록에 뜨는지,
  수정→"(수정됨)" 배지 뜨는지, 검증 에러 재렌더까지 전부 확인.

---

## -1. 리치 에디터 + 파일 업로드 전면 확장 + QnA 채택 + 게시물↔한마디 임베드 (2026-08-19 요청, ✅ 완료)

**요구사항 원문 요약**: 글쓰기/오늘의 한마디 작성 시 모든 파일형식 업로드(사진·동영상
미리보기, 파일 최대용량은 알아서 결정), 파일을 본문 중간에 삽입하는 블로그 스타일
에디터, QnA는 네이버 지식인처럼 질문자가 답변을 채택하는 방식, 한마디↔커뮤니티
게시글 상호 "바로가기"(제목 미리보기 카드).

- [x] **파일 업로드 인프라**(`global.upload.FileUploadService`/`EditorUploadController`,
  `POST /api/uploads/editor`): 위험한 실행/스크립트 확장자만 차단(exe/bat/sh/jar/
  ps1/php 등)하고 나머지는 전부 허용(사용자 확정). 크기 제한은 이미지 15MB/동영상
  300MB/기타 파일 50MB로 직접 정함(`spring.servlet.multipart.max-file-size`도
  300MB로 상향). 실제 업로드(정상/위험확장자 차단/미인증 차단) 전부 curl로 검증.
- [x] **HTML 새니타이저**(`global.util.HtmlSanitizer`, Jsoup 기반): `Post.content`/
  `ScheduleComment.content`를 `VARCHAR`→`MEDIUMTEXT`로 확장하고 저장 전 항상 이
  새니타이저를 거친다(`th:utext`로 그대로 렌더링하므로 이게 유일한 XSS 방어선).
  `<script>`/`onerror=` 등은 제거하고 `<img>`/`<video>`/파일 링크는 보존하도록
  실제 XSS 페이로드로 검증. **알려진 함정 추가**: Jsoup `preserveRelativeLinks(true)`는
  baseUri가 빈 문자열이면 무효(상대경로 src/href가 통째로 잘림) — 반드시 더미
  baseUri("http://localhost/")를 함께 써야 함, `Jsoup.clean(html, "", ...)`처럼
  baseUri를 비워두면 안 됨.
  둘 다 `ddl-auto: update`가 컬럼 타입 변경을 못 잡아내서(CLAUDE.md 기존 함정) 수동
  `ALTER TABLE ... MODIFY COLUMN content MEDIUMTEXT`로 고쳤음 — 배포 시 운영 DB에도
  같은 조치 필요.
- [x] **Quill 리치 에디터**(`static/js/rich-editor.js`, CDN `quill@2.0.2`): 게시글
  작성(`post/form.html`)과 오늘의 한마디 작성/수정(`school/calendar.html`) 둘 다
  적용. 🖼(이미지)/🎬(동영상)/📎(파일)/🔗(바로가기 카드) 커스텀 툴바 버튼.
  **알려진 함정**: Quill의 `dangerouslyPasteHTML`은 Quill이 모르는 태그/속성
  (class, download 등)을 지우거나 - 심하면 `<video>`처럼 아예 인식 못 하는 태그는
  통째로 버린다(실제로 확인됨, "미리 테스트 없이 verified라고 하지 말 것"의 정확한
  사례). 그래서 동영상/파일카드/바로가기카드 3개를 전부 Quill 커스텀 블롯
  (`RichVideoBlot`=`<video>`, `RichFileBlot`=`<figure class="rich-file-attachment">`,
  `RichEmbedBlot`=`<aside>`, `quill.insertEmbed()`로 삽입)으로 등록해서 저장/재편집
  왕복이 안전하게 보존되도록 고쳤음 - 새 임베드 타입을 추가할 땐 이 3개와 동일하게
  커스텀 블롯으로 만들 것(dangerouslyPasteHTML로 절대 넣지 말 것).
- [x] **QnA 답변 채택**(`PostComment.accepted`, `PostCommentService.acceptAnswer()`,
  `POST /posts/{uuid}/comments/{id}/accept`): 질문 작성자만 채택 가능(서버 검증),
  새로 채택하면 기존 채택 자동 해제(공지사항 "활성 1개" 패턴과 동일), 채택된 답변은
  댓글 목록 맨 위로 정렬 + "채택된 답변" 배지, 알림 발송(`Notification.Type.ACCEPTED`
  신규 추가). QNA 아닌 카테고리·질문자 아닌 사용자가 시도하면 차단되는 것까지 curl로
  검증.
- [x] **게시물↔한마디 상호 "바로가기" 임베드**: 한마디는 자체 상세 페이지가 없어서
  새 퍼머링크 `GET /school/comments/{id}`(`SchoolController.openComment`)를 추가 -
  그 한마디의 학교/날짜/학년/반으로 캘린더를 리다이렉트하고 `highlightComment`
  파라미터로 해당 한마디를 스크롤+강조 표시(`calendar.js`
  `applySharedCommentLinkIfPresent`/`pendingHighlightCommentId`). 링크 카드 삽입은
  `GET /api/embed/resolve?url=`(`global.embed.EmbedResolveController`)로 붙여넣은
  URL이 실제 게시물/한마디를 가리키는지 확인 후 제목(게시물)/내용 미리보기(한마디)를
  스냅샷으로 카드에 박아넣는 방식(대상이 나중에 수정/삭제돼도 카드 문구는 안 바뀜 -
  실시간 동기화는 범위 밖). 한마디 목록에 🔗 "공유 링크 복사" 버튼도 추가.

**이번 라운드 범위 밖으로 명시적으로 제외**: 대댓글(답글) 기능 - 사용자가 "답글도
같이 뛰어넘고 다음 기능 추가하자"고 명시적으로 요청함(2026-08-25에 별도로 착수해
완료, 6번 섹션 참고).

---

## 0. 로그인/회원가입 버그수정 및 리뉴얼 (2026-08-14~15, ✅ 완료)

**구글 소셜 로그인 실사용까지 전부 완료됨.**

- **⚠️ `application.yml`은 이제 git 추적 대상이 아니다(2026-08-15부터)** — 원래
  DB 비밀번호/NEIS 키가 평문으로 그대로 커밋되어 있던 파일인데, 여기에 구글
  client-id/secret까지 추가해서 커밋했다가 GitHub Push Protection(GH013)에
  막혀 `git push` 자체가 거부되는 일이 있었다(구글 OAuth 시크릿은 자동 탐지
  대상 - DB 비밀번호/NEIS 키는 자체 정의값이라 탐지 안 되고 그냥 통과됨).
  근본적으로 고치기로 하고 `.gitignore`에 `src/main/resources/application.yml`을
  추가 + `git rm --cached`로 추적 해제했다. **앞으로 이 파일은 로컬에만 있고
  절대 커밋되지 않는다** - 새로 이 프로젝트를 받으면
  `application.yml.example`을 `application.yml`로 복사해서 본인 DB
  비밀번호/NEIS 키/구글 자격증명을 채워야 한다(구글 블록은 선택 - 없어도
  앱은 정상 기동하고 그 기능만 꺼짐).
- `application.yml`에 실제 구글 client-id/secret 등록 완료, 로그인/회원가입
  페이지에 "구글 계정으로 로그인" 버튼 노출 확인.
- `redirect_uri_mismatch`(400 오류) 발생 → 원인은 구글 콘솔 쪽 "승인된
  리디렉션 URI"에 `https://localhost:8888`(경로 없음, 프로토콜도 https로 오타)만
  등록돼 있던 것이었음. `http://localhost:8888/login/oauth2/code/google`로
  정정 후 저장하니 정상 통과 확인(실제 사용자 계정으로 로그인 성공, `users`
  테이블에 `provider=GOOGLE` 레코드 생성 확인).
- 로그인/회원가입 HTML 재배치: 기존엔 구글 버튼이 폼 **아래**(회원가입은
  학교/학년/반까지 다 입력한 뒤)에 있어서 원래 목적(폼 생략)에 안 맞았음 →
  로그인/회원가입 둘 다 구글 버튼을 폼 **위쪽**으로 이동(`login.html`,
  `register.html`).
- **구글 첫 로그인 시 학교 설정 강제 화면 추가**(2026-08-15): 구글로 처음
  가입하면 학교/학년/반이 빈 채로 계정이 생성되는데, 이 상태로 커뮤니티/
  캘린더 등 "우리 학교" 기준 기능을 쓰면 의미가 없어서 막았다.
  - `User.needsSchoolSetup()`: schoolCode/grade/classNum 중 하나라도
    비어있으면 true.
  - `SchoolSetupInterceptor`(`global.security`, `AdminAccessInterceptor`와
    같은 패턴): 로그인한 사용자가 `needsSchoolSetup()`이면 `/school-setup`,
    `/logout`, `/notifications/unread-count`(네비바 배지 폴링) 외 모든 경로를
    `/school-setup`으로 강제 리다이렉트. `WebConfig`에 `addPathPatterns("/**")`
    로 등록하되 정적 리소스·oauth2 엔드포인트·학교 검색/반목록 API는 제외
    (안 그러면 설정 화면 자체가 못 뜸).
  - 새 화면 `user/school-setup.html` + `AuthController`의
    `GET/POST /school-setup` + `UserService.setupSchool()`(학교/학년/반만
    다루는 전용 메서드, 아이디·비밀번호는 안 건드림) — register.html의
    "우리 학교 설정" 블록과 위젯(`school-search.js`/`class-select.js`/
    `grade-select.js`)을 그대로 재사용.
  - `user1` 테스트 계정으로 게이트 동작 검증 완료(학교 정보 임시로 비우고
    로그인 → `/school-setup`으로 강제 이동 → `/posts` 등 다른 경로 접근 시도
    시 다시 튕겨나가는 것 확인 → 설정 완료 후 정상 접근 확인 → 원래 데이터로
    복원).

아래는 최초 구현 시점(2026-08-13) 정리 내용, 참고용으로 보존:

**코드 구현은 끝났지만, 실제로 "구글로 로그인" 버튼이 뜨려면 사용자가 구글 클라우드
콘솔에서 OAuth 클라이언트를 직접 만들어 `client-id`/`client-secret`을 발급받아야
한다 - 이건 Claude가 대신 해줄 수 없는 부분(외부 콘솔 가입/설정)이라 여기 정리해둔다.**

- 지원 범위: 구글만(사용자 확정, 카카오는 나중에 필요하면 같은 패턴으로 추가).
- 계정 연동 정책: 로컬 계정(아이디/비번 가입)과 완전히 별개로 취급(사용자 확정) -
  이메일이 같아도 자동 연동하지 않음. `User`에 이메일 필드 자체를 추가하지 않았다.
- 새 필드: `User.provider`(LOCAL/GOOGLE), `User.providerId`(구글 "sub" 클레임) -
  `(provider, provider_id)` 유니크 제약.
- 처음 로그인하는 구글 계정은 그 자리에서 바로 `User` 레코드가 만들어진다(별도
  회원가입 폼 없음) - 아이디는 이메일 로컬파트에서 자동 생성(영문/숫자만, 중복이면
  숫자 접미사), 비밀번호는 본인도 모르는 임의값(BCrypt 인코딩), 닉네임은 구글
  프로필 이름, 학교/학년/반은 비워둔 채로 시작 - 나중에 마이페이지에서 채우면 됨.
- 구글 계정은 폼 로그인용 비밀번호가 없으므로 "현재 비밀번호 확인"이 필요했던
  화면(내 정보 수정, 계정 탈퇴)에서 그 절차를 건너뛰도록 이미 처리해뒀다.
- **앱은 구글 자격증명 없이도 정상 기동/동작한다** - `ClientRegistrationRepository`
  빈이 없으면(`spring.security.oauth2.client.registration.google.*` 설정 자체가
  없으면) `SecurityConfig`가 `.oauth2Login()`을 아예 안 붙이고, 로그인/회원가입
  페이지도 "구글로 로그인" 버튼을 숨긴다(`AuthController`가 같은 방식으로 판단).

**사용자가 직접 해야 할 것 (Claude는 대행 불가)**:
1. [Google Cloud Console](https://console.cloud.google.com/) → 새 프로젝트(또는
   기존 프로젝트) → "APIs & Services" → "OAuth consent screen" 설정(앱 이름,
   테스트 사용자 등 - 심사 전이면 "테스트" 모드로 충분).
2. "APIs & Services" → "Credentials" → "Create Credentials" → "OAuth client ID" →
   애플리케이션 유형 "웹 애플리케이션".
3. **승인된 리디렉션 URI**에 정확히 이 값을 등록: `http://localhost:8888/login/oauth2/code/google`
   (배포 후에는 실제 도메인으로 하나 더 추가).
4. 발급된 클라이언트 ID/보안 비밀번호를 `application.yml`에 아래처럼 추가(또는 더
   안전하게 환경변수로 분리 - `${GOOGLE_CLIENT_ID}`/`${GOOGLE_CLIENT_SECRET}` 형태로
   써두고 실행 시 환경변수 주입):
   ```yaml
   spring:
     security:
       oauth2:
         client:
           registration:
             google:
               client-id: 발급받은_클라이언트_ID
               client-secret: 발급받은_클라이언트_시크릿
               scope: email, profile
   ```
   **주의**: 이 키를 빈 문자열(`${GOOGLE_CLIENT_ID:}`처럼 기본값 빈 문자열)로
   두면 앱 자체가 기동 실패한다(`ClientRegistration.Builder`가 "clientId cannot
   be empty"로 즉시 예외) - 실제 값이 준비되기 전까지는 이 섹션을 아예
   `application.yml`에 넣지 말 것(현재 상태).
5. 추가 후 앱을 재기동(`./gradlew bootRun`, devtools 핫리로드가 아니라 새
   의존성/설정이라 완전 재기동 필요할 수 있음)하면 로그인/회원가입 페이지에
   "구글로 로그인" 버튼이 자동으로 나타난다.

---

## 1. 공지사항(Notice) 기능 재설계 (2026-08-12 요청, ✅ 완료 2026-08-13)

**완료됨** - 아래 요구사항 전부 구현/검증 완료. 기존 `Post.Category.NOTICE` 방식은
완전히 제거하고 대체함(사용자 확인받고 진행). 자세한 구현/검증 내용은 CLAUDE.md의
"2026-08-13 라운드 — 공지사항(Notice) 기능 재설계" 섹션 참고. 완료 조건 체크:
- [x] 공지사항 작성은 관리자 페이지에서만 가능하다
- [x] 새 공지 작성 시 이전 공지는 자동으로 비활성화(보관)되고, 활성 공지는 항상 1개다
- [x] 활성 공지는 사용자 화면에 고정 노출된다 (커뮤니티 목록 상단 배너)
- [x] 관리자는 과거 공지 이력을 모두 조회할 수 있다
- [x] 사용자는 별도의 "공지" 탭에서 과거 공지까지 페이지네이션으로 확인할 수 있다
- [x] 총관리자는 부관리자에게 "공지사항 작성" 권한을 개별적으로 부여/회수할 수 있다
- [x] 해당 권한이 없는 부관리자는 공지 작성 기능에 접근할 수 없다

**남겨둔 것(의도적으로 이번 라운드 범위 밖)**: 공지 수정/삭제 UI는 완료 조건에 없어서
만들지 않았다. 필요해지면 `NoticeService`에 `updateNotice()`/`deleteNotice()`를
추가하고 `AdminPostService`의 수정/삭제 패턴(소프트 삭제)을 그대로 따르면 된다.

아래는 착수 전 남겨뒀던 원본 요구사항 원문(참고용, 그대로 보존):

**⚠️ 먼저 알아둘 것**: 지금 이미 `Post.Category.NOTICE`로 "공지" 기능이 있다
(`post/domain/Post.java`, `PostService.createPost()`/`getPinnedNotices()`). 하지만
지금 있는 건 이번 요청과 설계가 다르다 — 그냥 게시글 카테고리 중 하나일 뿐이고,
관리자면 누구나 여러 개 동시에 쓸 수 있고, 최신 5개까지 커뮤니티 목록 상단에
고정되는 방식이다("활성 공지 1개" 개념도, 별도 권한도, 별도 탭도 없음). 아래
요구사항은 이 기존 방식을 **대체하거나 완전히 새 모델로 다시 만드는** 작업이다 —
착수 전에 기존 `Post.Category.NOTICE`를 그대로 유지한 채 새 시스템을 병행할지,
아니면 아예 걷어내고 교체할지부터 사용자에게 확인할 것.

### 배경/목표
- 일반 게시글과 완전히 분리된 공지사항 기능
- 공지사항은 관리자 페이지에서만 작성 가능
- 현재 활성화(고정)된 공지는 항상 1개만 노출
- 과거 공지는 삭제되지 않고 이력으로 보관/조회 가능
- 부관리자 권한 항목에 "공지사항 작성 권한" 추가

### 상세 요구사항

**1) 일반 게시글과 분리**
- 공지사항은 `Post`와 다른 별도의 데이터 모델로 관리 (예: `Notice`/`Announcement` 엔티티)
- 작성 화면도 게시글 작성 화면과 별도로 구성 (제목 + 본문 정도의 단순한 폼)
- 공지는 일반 게시글 피드/검색 결과 등에 섞여 노출되지 않음

**2) "활성 공지 1개" 로직**
- 새 공지를 작성하면 이전 공지는 자동으로 "비활성(보관)" 상태로 전환되고, 새
  공지가 "활성" 상태가 됨 (활성 공지는 항상 정확히 1개)
- 활성 공지는 사용자 화면에 고정 노출(위치는 기존 UI 구조에 맞게 자연스러운 곳에)
- 비활성(과거) 공지는 삭제하지 않고 DB에 계속 보관

**3) 관리자 페이지 기능**
- 공지 작성/등록
- 과거 공지 목록(이력) 조회 (작성일, 작성자 표시)
- 과거 공지 상세 조회 (수정/삭제 권한은 기존 게시글 관리 정책과 동일하게 적용)

**4) 사용자 화면 기능**
- 별도의 "공지" 탭/메뉴 신설
- 공지 탭에서는 활성 공지뿐 아니라 과거 공지 이력도 스크롤(무한 스크롤 또는
  페이지네이션)로 확인 가능
- 최신 공지가 최상단에 오도록 정렬

**5) 권한 관리 (부관리자 권한)**
- 기존 "총관리자 → 부관리자 권한 부여" 기능(`User.canManageReports/Posts/
  ScheduleComments`, `/admin/users/admins`)에 "공지사항 작성 권한" 항목 추가
- 총관리자는 특정 부관리자에게 이 권한을 개별로 켜고/끌 수 있어야 함
- 권한 없는 부관리자에게는 공지 작성 메뉴/버튼 비노출 + API 레벨에서도 접근 차단
  (`AdminAccessInterceptor`에 새 분기 추가 필요)

### 작업 전 확인할 것
- 기존 게시글(Post) 모델 구조 (`post` 패키지 전체)
- 기존 관리자 권한(Role/Permission) 관리 방식 (`User.Role`, `canManage*` 플래그,
  `AdminAccessInterceptor`, `/admin/users/admins`)
- 기존 프론트엔드 탭/네비게이션 구조 (`fragments/navbar.html`, `post/list.html`
  카테고리 탭 패턴)

### 완료 조건 (Acceptance Criteria)
- [ ] 공지사항 작성은 관리자 페이지에서만 가능하다
- [ ] 새 공지 작성 시 이전 공지는 자동으로 비활성화(보관)되고, 활성 공지는 항상 1개다
- [ ] 활성 공지는 사용자 화면에 고정 노출된다
- [ ] 관리자는 과거 공지 이력을 모두 조회할 수 있다
- [ ] 사용자는 별도의 "공지" 탭에서 과거 공지까지 스크롤로 확인할 수 있다
- [ ] 총관리자는 부관리자에게 "공지사항 작성" 권한을 개별적으로 부여/회수할 수 있다
- [ ] 해당 권한이 없는 부관리자는 공지 작성 기능에 접근할 수 없다

---

## 2. 24시간 TTL 캐시 만료 (보류 중) -->

2026-08-12에 한 번 구현했다가 사용자 지시로 되돌림("캐시만료는 지금 구현하지마").
`Timetable`/`Meal`이 한번 캐시되면 영구 반환되는 문제는 여전히 남아있다. 다시
시작할 준비는 돼 있음 — `SchoolService.getCalendarDetails()`의 캐시 조회 분기에
`updatedAt` 컬럼 기반 24시간 만료 체크만 추가하면 됨(과거 구현 참고, 커밋
히스토리에는 안 남아있으니 이 문서 기준으로 처음부터 다시 설계).

## 3. 방학 D-Day 개선 (✅ 완료 2026-08-25)

`SchoolService.getVacationDday()`로 기본 구현은 완료(2026-08-12). 지적된
엣지케이스("방학식"/"개학"처럼 하루짜리 일정만 등록하는 학교는 방학식~개학
사이 기간에도 "방학 중"으로 안 잡히고 다음 방학 D-Day가 뜨는 문제)를
`isCurrentlyInVacation()` 신규 헬퍼로 해결했다.

- 최근 200일 안에서 "방학" 키워드가 붙은 가장 최근 날짜(마커)를 찾은 뒤: (1) 마커가
  오늘이면 방학 중, (2) 마커 하루 전날도 "방학"으로 등록돼 있으면(=매일 반복 등록되던
  구간의 꼬리, 아산배방중 패턴) 그 반복이 오늘까지 안 이어졌다는 뜻이므로 이미 방학이
  끝난 것, (3) 마커가 하루짜리 고립된 표시면(서울고의 "방학식"처럼) 명시적 "개학"
  마커가 나올 때까지 방학 중으로 판단.
- **처음 짠 버전은 (2) 없이 (1)(3)만 있어서 새 버그를 만들었었다** - 아산배방중처럼
  매일 반복 등록하다 그냥 멈추는 학교(명시적 "개학" 마커 없음)가 방학이 실제로 끝난
  지 한참 지나도 계속 "방학 중"으로 잘못 판단됨(캘린더 D-Day 배지가 아예 안 뜸,
  404). 실제 NEIS 데이터로 재검증하다가 발견해서 (2) 분기를 추가해 수정.
- 검증: `/school/api/vacation-dday`를 실제 NEIS 데이터로 직접 호출 - 아산배방중학교
  (07.26~08.13 매일 "여름방학" 반복 등록, 이미 끝남) → `inVacation:false`, 다음
  "겨울방학" D-Day 정상 표시(수정 전엔 404로 배지 자체가 안 떴음). 서울고등학교
  (07.21 "방학식" 단발 등록, 08.18 "개학" 단발 등록, 오늘 기준 이미 재개) →
  `inVacation:false`, 다음 "방학식" D-Day 정상 표시(실제 NEIS 개학일 08.18과 일치).
  방학식~개학 사이 구간 판단은 실제 날짜 데이터를 손으로 대입한 로직 검증으로 확인
  (오늘 날짜가 실제로 그 구간에 걸치는 학교를 찾지 못해 라이브 재현은 못 함).

## 4. 기획 대비 미구현 기능

- **투표 기능**: 기획서에 언급만 있고 설계는 없음 — 요구사항부터 정리 필요
- ~~**포인트/티어 시스템**~~(✅ 2026-08-25 완료) — 사용자와 설계 논의 후 확정된 방향: 소비형
  (화폐) 개념으로 설계하되 소비 기능 자체는 이번 라운드에 구현하지 않음, 티어는 하락 없이
  누적 총점 기준(하락은 나중에 별도로), 신규 가입 시 0점이 아니라 기본 10점에서 시작(활동량이
  숫자로 드러나야 한다는 요청).
  - `User.points`(int, 기본값 10 - `active` 필드와 동일하게 Java 필드 초기값으로 줌, DB
    `default 10`만으로는 신규 가입 흐름에 반영 안 됨) + `PointTier` enum(5단계, 임계값순
    누적 포인트 구간 - 티어를 별도 컬럼에 저장하지 않고 `User.getTier()`가 매번 `points`에서
    계산: 나중에 소비/하락으로 points가 줄어도 tier가 자동으로 맞게 하기 위함). 등급 이름은
    임시(새내기/반장 후보/반장/학생회 임원/전교 회장) - 사용자가 나중에 직접 정할 이름으로
    교체 예정.
  - `UserPointService.award()`가 유일한 적립 창구. 활동별 적립량: 게시글 작성 +5, 댓글/답글
    작성 +2, 좋아요 받음 +1, QnA 답변 채택됨 +15(가장 큼). 일일 획득 한도 30점(어뷰징 방지) -
    `UserPointLog`(신규, append-only 적립 이력) 테이블에서 오늘 자정 이후 합계를 조회해 남은
    한도만큼만 잘라서 준다(한도 초과분은 조용히 버림, 에러 없음 - 글쓰기/댓글 자체는 항상
    성공해야 하고 포인트를 못 받는 것만으로 충분한 제약).
  - `UserRepository.addPoints()`로 원자적 벌크 UPDATE(다른 카운터들과 동일 패턴) - 이 김에
    `User` 엔티티에 `@DynamicUpdate`가 없던 걸 새로 추가했다(Post/PostComment는 이미 있었는데
    User만 빠져있었음 - 없으면 동시에 실행되는 다른 트랜잭션이 오래된 points 값까지 포함해서
    UPDATE를 날려 방금 적립된 포인트를 조용히 덮어쓸 위험이 있었음).
  - UI: 마이페이지 프로필 카드 + 공개 프로필(`/users/{id}`)에 티어 배지 + 포인트 통계 추가.
    관리자 계정 상세 화면(`admin/user-profile.html`)에는 아직 미반영(범위 밖으로 남겨둠).
  - 실사용 시나리오로 검증: 게시글 작성 시 +5 확인(10→15), 댓글 15개 연속 작성 시 12개는
    +2씩 정상 적립되다가 13번째에 정확히 +1(한도 30점에 딱 맞춰 잘림)만 적립되고 14~15번째는
    로그 자체가 안 남는 것(완전 무시)까지 DB로 직접 확인 - 댓글 작성 자체는 15개 전부 200
    성공(포인트만 한도로 막힘, 글쓰기 자체는 안 막힘).
  - 남은 것(사용자 확정, 이번 라운드 범위 밖): 소비 기능(포인트로 뭔가를 구매/교환), 티어
    하락(비활동 감점) 로직, 등급 이름 실제 값 교체.

## 5. 2026-08-10 확정 16개 장기 백로그 중 미착수/부분 완료 항목

(전부 사용자가 "이것들 전부 할거야"라고 확정한 목록 — 착수 순서는 미정)

- **좋아요·북마크 기능**(✅ 2026-08-19 완료): 게시글/한마디는 이미 완전
  구현돼 있었고, 댓글(PostComment)만 빠져있던 마이페이지 조회를 추가함
  (`CommentLikeRepository`/`CommentBookmarkRepository`에 사용자별 조회
  메서드, `MyActivityService.getLikedComments`/`getBookmarkedComments`,
  `my-activity.html` "댓글" 서브탭). 실제 좋아요/북마크 → 마이페이지 노출 →
  해제까지 브라우저로 end-to-end 검증 완료.
- **소셜 로그인**: 코드는 완료, 실제 활성화는 사용자의 구글 자격증명 발급이
  남아있음 - 맨 위 "0. 소셜 로그인(구글)" 섹션 참고
- **이메일 인증 및 비밀번호 찾기**(✅ 2026-08-24 완료): `User.email` 필드 추가,
  `EmailToken`(`VERIFY_EMAIL`/`RESET_PASSWORD` 두 용도, 만료시간·1회용) +
  `EmailTokenService` + `MailService`(SMTP 미설정 시 로그만 남기고 스킵 -
  구글 OAuth `ClientRegistrationRepository`와 동일한 `ObjectProvider` 패턴이라
  SMTP 없어도 앱 정상 기동). `/find-username`·`/forgot-password`·
  `/reset-password`·`/verify-email`·`/mypage/resend-verification` 전부 신규.
  기존 계정(이메일 필드 생기기 전 가입자)은 `EmailSetupInterceptor`
  (`SchoolSetupInterceptor`와 동일 패턴)가 로그인 시 `/email-setup`으로 강제
  이동시켜 이메일을 채우게 한다. 계정 존재 여부와 무관하게 항상 같은 응답을
  줘서 계정 열거를 방지(아이디/비번 찾기 둘 다).
  **실사용자 계정(`jungjihu1130`)의 실제 Gmail 앱 비밀번호로 SMTP까지 붙여
  실메일 수신 확인 완료** — 토큰 발급/소비, 토큰 재사용 차단, 강제 게이트
  우회 차단(다른 경로 접근 시도 시 `/email-setup`으로 재리다이렉트)까지 전부
  브라우저로 end-to-end 검증. 발견/수정된 버그: `TestDataSeeder`가 새로 필수가
  된 이메일 필드를 안 채워서 테스트가 깨져 있었음(수정 완료).
- **DB 백업/운영**(✅ 2026-08-23 완료): `scripts/backup-db.ps1`(mysqldump, 최근 14개
  보관 후 자동 정리) + `scripts/restore-db.ps1`. 실제 백업 1회 실행 → 덤프 파일에
  실데이터(16개 테이블 INSERT) 포함 확인, 스크래치 스키마(`webschool_restore_test`)에
  복구까지 성공 확인 후 정리. `CLAUDE.md` Commands 섹션에 사용법 추가.
- **로그/모니터링**(✅ 2026-08-23 완료): `application.yml`에 `logging.*`(파일 롤링,
  `logs/webschool.log`, 30일 보관) + `spring-boot-starter-actuator`
  (`/actuator/health`만 노출, `SecurityConfig` permitAll 추가) 추가. 서버 기동 →
  로그 파일 생성/기록, `curl /actuator/health` → `{"status":"UP"}` 확인.
- **파일 저장소 분리**: 게시글 이미지가 로컬 `uploads/`에 저장 중 — S3 등 외부
  스토리지로 분리 필요 (보류)
- **클라우드 서버 환경**: 현재 로컬(Windows) 개발 환경에서만 구동 (보류)
- **HTTPS/보안 강화**(CSRF 재활성화 부분만 ✅ 2026-08-23 완료): [SecurityConfig.java](src/main/java/com/webschool/webschool/global/config/SecurityConfig.java)의
  `.csrf(csrf -> csrf.disable())`를 제거해 CSRF를 다시 켰다. 템플릿 전수 조사 결과
  `th:action` 아닌 순수 폼은 0개라 대부분은 `thymeleaf-extras-springsecurity6`가
  자동으로 처리했고, JS로 직접 요청을 만드는 4개 파일(`admin-bulk.js`,
  `rich-editor.js`, `calendar.js`, `post-detail.js`)만 새 공용 헬퍼
  `static/js/csrf.js`로 토큰을 같이 보내도록 수정. 이걸로 수정사항.md #1(관리자
  일괄 처리 CSRF 누락) 항목도 같이 해소됨. 브라우저로 admin 일괄 블라인드/해제,
  게시글·댓글 좋아요/북마크/작성/삭제/신고, 캘린더 한마디 작성/좋아요/북마크/삭제,
  에디터 파일 업로드까지 전부 200으로 통과하는 것을 네트워크 로그로 직접 확인
  (403 없음). **실제 HTTPS 인증서/보안 헤더 점검은 여전히 보류**(호스팅 미정).
- **CI/CD**(빌드+테스트 부분만 ✅ 2026-08-23 완료): `.github/workflows/ci.yml` 신규
  (MySQL 8 서비스 컨테이너 + `./gradlew build`). 이 김에
  `src/main/resources/application.yml.example`도 새로 만들었다(`CLAUDE.md`가 이미
  참조하고 있었는데 실제 파일이 없던 문서/repo 불일치였음). 로컬에서 `./gradlew build`
  통과까지 확인, 실제 GitHub Actions 통과 확인은 push 후 사용자가 직접 확인 필요.
  **CD(배포)는 여전히 보류**(호스팅 미정).
- 파일 저장소(S3)·클라우드 서버·CD는 순서상 서로 의존적이라 호스팅 프로바이더
  결정 전까지 보류 유지.
- **2026-08-19 착수 순서 확정, 2026-08-24 진행 상황**: 로그/모니터링 → CI(빌드+
  테스트) → HTTPS 코드측(CSRF 재활성화) → DB 백업 스크립트 → 이메일 인증/
  비밀번호 찾기까지 5개 전부 완료. 클라우드 서버 → S3 → 실제 HTTPS 인증서 →
  CD(배포)는 호스팅 프로바이더 결정 전까지 계속 보류(이 4개는 순서상 서로
  의존적).

## 6. 그 외 자잘한 후보

- ~~**감사 로그에 IP 기록**~~(2026-08-25 완료, 사용자 요청) — `AdminActionLog`에 `ip` 컬럼 추가,
  `AdminActionLogService.log()`가 `RequestContextHolder`로 현재 요청에서 클라이언트 IP를 꺼내
  자동으로 채운다(호출부마다 `HttpServletRequest`를 새로 threading할 필요 없음 - 회원가입 같은
  로그인 전 호출도 여전히 요청 스레드 안이라 정상 동작). 프록시 뒤에 배포될 경우를 대비해
  `X-Forwarded-For` 헤더를 우선 확인하는 공용 유틸(`global.util.ClientIpUtils`)로 분리해
  조회수 어뷰징 방지 기능과 공유한다. 감사 로그 화면에 "IP" 컬럼 추가, 기존 로그는 컬럼이 없던
  시절 기록이라 "-"로 표시됨을 확인. 비밀번호 등 민감정보는 아니지만 개인 식별 가능 정보라 이
  화면 자체가 총관리자/`canViewAuditLog` 권한자 전용인 것으로 노출 범위를 제한한다.

- ~~**대댓글(1depth 답글)**~~(✅ 2026-08-25 완료) — 2026-08-19엔 "다음 기능
  뛰어넘고"라는 사용자 지시로 제외됐다가 이번 라운드에서 착수. `PostComment.
  parentComment`(자기 참조 `@ManyToOne`, nullable) 추가, 1depth만 허용(답글의
  답글은 서버에서 거부). 댓글 목록 API는 여전히 평평한(flat) 목록을 그대로
  반환하고(`PostCommentDto.parentId` 추가) `post-detail.js`가 클라이언트에서
  `parentId` 기준으로 부모 밑에 답글을 묶어 그린다 - QNA 채택 답변이 정렬로
  맨 위에 올라가도(`getComments()`의 기존 정렬) 그 답글이 정확히 따라붙는 것까지
  확인. 답글에는 "답글" 버튼 자체를 안 보여줘서 1depth 제한을 UI에서도 드러낸다.
  알림도 새로 추가(`Notification.Type.REPLY`, "답글") - 답글 작성 시 부모 댓글
  작성자에게 알리고, 게시글 작성자와 겹치지 않을 때만 게시글 작성자에게도 추가로
  알린다(같은 사람이면 중복 알림 방지).
  - 삭제 가드: 답글이 달린 댓글은 삭제할 수 없다(`existsByParentComment_IdAndDeletedFalse`) -
    삭제된 댓글은 목록 쿼리에서 아예 빠지는데(`deletedFalse` 필터), 그 밑에 남은
    답글만 부모 없이 붕 뜨는 걸 막기 위한 단순한 방어(답글까지 함께 지우는
    cascade보다 안전 - 답글 작성자 동의 없이 남의 글이 같이 사라지면 안 됨).
  - QNA 채택 가드: 답글은 답변으로 채택할 수 없다(`acceptAnswer()`에 `parentComment
    != null`이면 거부하는 체크 추가) - "답변"은 항상 최상위 댓글만을 의미해야
    하므로.
  - **배포 시 주의**: `Notification.type`이 MySQL 네이티브 `ENUM` 컬럼이라(CLAUDE.md
    "알려진 함정" - enum 값 추가는 `ddl-auto: update`가 못 잡아냄) `REPLY` 값을
    새로 추가한 것이 로컬 DB엔 자동 반영 안 돼서 직접
    `ALTER TABLE notifications MODIFY COLUMN type ENUM(...) NOT NULL;`로 반영했다.
    다른 환경(운영 DB 등)에 배포할 때도 이 ALTER를 반드시 먼저 실행해야 한다 -
    안 하면 답글 작성 시 알림 저장이 "Data truncated for column 'type'" 에러로 실패한다.
  - 실사용 시나리오로 end-to-end 검증: user2가 게시글에 최상위 댓글 → user1(게시글
    작성자)이 그 댓글에 답글 → user2에게 REPLY 알림 정상 수신, 게시글 작성자
    중복 알림 없음(본인이라 스킵) 확인. 답글에 다시 답글을 시도(직접 API 호출로
    UI 우회) → 400 "답글에는 답글을 달 수 없습니다" 확인. 답글 달린 댓글 삭제
    시도 → 400 "답글이 달린 댓글은 삭제할 수 없습니다" 확인. QNA 게시글에서
    답글을 답변으로 채택 시도 → 400 "답글은 답변으로 채택할 수 없습니다" 확인,
    같은 게시글의 진짜 최상위 답변 채택은 정상 동작(회귀 없음) 확인.
- ~~게시글 리치 에디터~~(✅ 2026-08-19 완료, 아래 "리치 에디터 + 파일 업로드
  전면 확장" 항목 참고)
- ~~**캘린더 "기간별 일정 밑줄 표시"**~~(2026-08-25 확인 결과 이미 구현돼 있었음) — `CalendarEventDto`가
  단일 `date` 필드만 가진 건 맞지만(기간 표현 불가), NEIS가 여러 날짜짜리 일정을 "하루 단위로
  같은 이름의 행을 반복"해서 내려주는 걸 이용해 `static/js/calendar.js`의
  `applyMonthEventChips()`(`buildWeekEpisodes`로 같은 주 안 연속된 같은 이름 날짜를 하나의
  구간으로 묶고, `assignRows`/`renderWeek`로 여러 날짜에 걸친 연속된 색깔 띠로 렌더링)가 이미
  기간별 표시를 프론트에서 처리하고 있었다. 브라우저로 "여름방학"(여러 주 연속) 같은 실제
  다일짜리 일정이 여러 칸에 걸친 하나의 띠로, "광복절" 같은 하루짜리 일정은 시작=끝 칸 하나로
  렌더링되는 것을 DOM에서 직접 확인 - `CalendarEvent` 엔티티/레포지토리(죽은 코드 확정, 아래
  기록 그대로 유효)와는 별개로 이 프론트 기능은 살아있었다. `CalendarEventDto`에 기간 필드를
  추가하는 작업은 불필요.
- ~~특성화고/마이스터고 시간표 미조회~~(✅ 2026-08-19 완료 — `NeisApiService`에
  `resolveTimetableEndpoint(schoolKind)` 추가해 고등학교 계열은 `hisTimetable`,
  특수학교는 `spsTimetable`로 분기. 실제 마이스터고/일반 중학교 두 케이스로
  회귀 없이 검증)
- ~~**조회수 어뷰징 방지 강화**~~(2026-08-25 완료) — 세션 기반 판단(`PostController`)에 더해
  IP 기반 판단(`PostViewService`/`PostView`/`PostViewRepository` 신규)을 한 겹 추가했다. 같은
  IP가 최근 24시간 안에 이미 조회한 글이면 세션이 달라도(새 브라우저, 세션 만료, 로그아웃 후
  재방문 등) 조회수를 다시 올리지 않는다. 로그아웃으로 세션을 강제로 새로 만든 뒤 같은 IP로
  재방문해도 조회수가 그대로인 것을 DB(`post_views` 테이블)와 함께 확인. 학교 공용 네트워크처럼
  여러 사용자가 같은 IP를 공유하면 오탐(진짜 다른 사람의 조회를 못 세는 경우) 가능성은 있음 -
  어뷰징 방지 목적상 감수하는 트레이드오프.
- ~~개인 대 개인 차단(UserBlock) 기능에 버그가 있음~~(✅ 2026-08-22 완료 — 원인
  특정: `UserBlockService.getMyBlocks()`(마이페이지 "차단 목록" 탭)가
  `findByBlocker_IdOrderByCreatedAtDesc()`로 만료 여부와 무관하게 전부
  반환하고 있었다. 기간제 차단이 만료되면 `existsActiveBetween()`은 이미
  차단 아님으로 판단해 댓글 작성을 막지 않는데, 목록에는 과거 만료일이
  "OO까지"로 여전히 표시돼 지금도 차단 중인 것처럼 보였다 - "차단했는데
  댓글이 달린다"/"차단이 안 풀린다" 둘 다로 오해할 수 있는 원인이었음.
  `findActiveBlockedUserIds()`와 동일한 만료 조건의 새 쿼리
  (`findActiveByBlocker_IdOrderByCreatedAtDesc`)로 교체해 만료된 차단은
  목록에서 빠지도록 수정. 이미 만료된 차단 레코드를 직접 심어서 목록에서
  사라지는 것까지 확인. 차단/신고 기능 실사용 테스트 중 별도로
  `static/js/modal.js`의 레이스 컨디션 버그(confirm→prompt 연쇄 호출 시
  두 번째 모달이 첫 모달의 지연 정리 타이머에 의해 지워지는 문제 - 신고
  사유 입력창이 뜨자마자 사라지는 증상으로 재현됨)도 함께 발견해 수정함.)
- ~~**리치 에디터로 올린 파일 중 "저장 안 된 임시 업로드" 정리 안 됨"**~~(✅ 완료) —
  `global.upload.EditorUploadCleanupService` 신규(이 프로젝트 첫 `@Scheduled`
  작업, 매일 새벽 4시). Post/한마디 본문(소프트 삭제 포함)에서 실제 참조되는
  파일 URL을 모아두고, 디스크에서 그 목록에 없으면서 24시간 이상 지난 파일만
  삭제(방금 올렸지만 저장 전인 파일 보호용 유예). 자세한 검증 기록은
  `수정사항.md` 히스토리(git log로 확인 가능) 참고 — 이 문서에 같은 항목이
  중복으로 남아있었는데 여기 사본만 취소선 처리가 누락돼 있었음.
