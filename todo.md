# TODO — 앞으로 할 일

이 파일은 CLAUDE.md에 흩어져 있던 백로그와, 사용자가 다음 작업으로 지정한 새 기능
요청을 한곳에 모아둔 목록이다. 완료한 항목은 지우지 말고 체크만 하고, 새 요청은
날짜와 함께 이 파일 위쪽(우선순위 높은 쪽)에 추가할 것.

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
같이 뛰어넘고 다음 기능 추가하자"고 명시적으로 요청함(6번 섹션에 별도 기록).

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

## 3. 방학 D-Day 개선 (부분 완료)

`SchoolService.getVacationDday()`로 기본 구현은 완료(2026-08-12). 다만 학교마다
NEIS 학사일정 등록 방식이 달라서, "방학식"/"개학"처럼 하루짜리 일정만 등록하는
학교(예: 서울고)는 방학식~개학 사이 기간에도 "방학 중"으로 안 잡히고 다음 방학
D-Day가 뜨는 경우가 있음. 필요하면 방학식~개학 사이 기간까지 자동으로 방학
중으로 판단하도록 보강.

## 4. 기획 대비 미구현 기능

- **투표 기능**: 기획서에 언급만 있고 설계는 없음 — 요구사항부터 정리 필요
- **포인트/티어 시스템**: `User`에 point/tier 컬럼 추가, QnA 답변 채택(`isAccepted`)
  개념부터 설계, 일일 획득 한도(어뷰징 방지)

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
- **이메일 인증 및 비밀번호 찾기**: `User`에 이메일 필드 자체가 없음. 필드 추가 +
  인증 메일 발송(SMTP) + 재설정 토큰 흐름 설계 필요
- **DB 백업/운영**: 정기 백업 스케줄(mysqldump 등), 복구 절차 문서화
- **로그/모니터링**: 현재 Spring Boot 기본 로그만 있음
- **파일 저장소 분리**: 게시글 이미지가 로컬 `uploads/`에 저장 중 — S3 등 외부
  스토리지로 분리 필요
- **클라우드 서버 환경**: 현재 로컬(Windows) 개발 환경에서만 구동
- **HTTPS/보안 강화**: CSRF도 개발 편의상 꺼져 있음(`SecurityConfig`) — 배포 전
  재활성화 + HTTPS 인증서 + 보안 헤더 점검
- **CI/CD**: 원격 저장소는 이제 있음(`github.com/jihu1130/webproject`) — 파이프라인
  자체는 아직 없음
- 파일 저장소(S3)·클라우드 서버·CI/CD는 순서상 서로 의존적(CI/CD는 원격 저장소
  필요 - 이건 이제 충족됨, 클라우드 서버가 있어야 HTTPS도 의미 있음)
- **2026-08-19 착수 순서 확정**: 외부 계정/비용 결정이 필요 없는 것부터
  로그/모니터링 → CI(빌드+테스트) → HTTPS 코드측(CSRF 재활성화) → DB 백업
  스크립트 → 이메일 인증/비밀번호 찾기(Gmail SMTP로 시작, 추후 AWS SES 등
  으로 교체 가능하게 `JavaMailSender` 추상화) 순으로 진행. 클라우드 서버 →
  S3 → 실제 HTTPS 인증서 → CD(배포)는 프로바이더 결정 전까지 보류.

## 6. 그 외 자잘한 후보

- **대댓글(1depth 답글)**: `PostComment.parentComment` 추가 필요. 2026-08-19에
  "다음 기능 뛰어넘고"라는 사용자 지시로 이번 라운드에서 명시적으로 제외됨
  (관리자 화면의 익명글 실제 작성자 조회는 `AdminPostDetailDto`/
  `admin/post-detail.html`에 이미 구현되어 있음 — 예전 메모가 낡았던 것,
  2026-08-19 확인)
- ~~게시글 리치 에디터~~(✅ 2026-08-19 완료, 아래 "리치 에디터 + 파일 업로드
  전면 확장" 항목 참고)
- **캘린더 "기간별 일정 밑줄 표시"**: `CalendarEventDto`가 단일 `date` 필드만
  가짐(기간 표현 불가), `CalendarEvent` 엔티티/레포지토리는 정의만 있고
  어디서도 참조되지 않는 죽은 코드로 확정(2026-08-19 확인) — 구현하려면
  DTO에 기간 필드 추가부터, 아니면 죽은 코드 삭제 검토
- ~~특성화고/마이스터고 시간표 미조회~~(✅ 2026-08-19 완료 — `NeisApiService`에
  `resolveTimetableEndpoint(schoolKind)` 추가해 고등학교 계열은 `hisTimetable`,
  특수학교는 `spsTimetable`로 분기. 실제 마이스터고/일반 중학교 두 케이스로
  회귀 없이 검증)
- **조회수 어뷰징 방지 강화**: 지금은 세션 기반이라 우회 가능 — IP+쿠키 또는 DB
  조회 이력 방식 검토
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
- **리치 에디터로 올린 파일 중 "저장 안 된 임시 업로드" 정리 안 됨**: `/api/uploads/
  editor`로 올린 파일은 즉시 디스크에 저장되는데, 사용자가 그 파일을 삽입한
  글 작성을 중간에 취소하면 파일만 `uploads/editor/`에 고아로 남는다(2026-08-19,
  파일 업로드 기능 추가하면서 확인된 설계상 한계 — 이번 라운드 범위 밖으로
  남겨둠). 필요해지면 일정 기간 지난 미참조 파일을 정리하는 배치 작업 검토.
