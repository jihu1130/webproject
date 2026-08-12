# WebSchool 프로젝트 — Claude 작업 메모

다음 세션의 Claude(나 자신)가 맥락을 다시 파악하지 않고 바로 이어서 코딩할
수 있도록 남기는 메모다. 기획 문서(`../계획/`)와 실제 코드를 직접 대조해서
작성했다 (2026-08-04 기준, **같은 날 커뮤니티 리뉴얼 작업 이후 갱신**).
**코드가 계속 바뀔 것이므로, 이 문서의 설명과 실제 파일 내용이 다르면
무조건 실제 코드를 신뢰할 것.** 


## 0. 저장소 위치 - 매번 헷갈리는 부분이니 꼭 먼저 확인할 것

이 프로젝트는 세션마다 사용자가 다른 폴더를 열어서 작업하는 바람에
동시에 여러 군데에 사본이 존재한다. **이 `CLAUDE.md`가 있는 폴더
(`C:\Users\jjhjj\OneDrive\Desktop\webproject-main\webproject-main`)가
2026-08-04 기준 가장 최신 상태의 진짜 활성 작업 폴더다** (post 커뮤니티
기능까지 여기에만 있음). 코딩 작업을 시작하기 전에:

1. 지금 사용자가 어느 경로에서 작업 중인지(터미널 cwd, IDE가 연 폴더)
   먼저 확인할 것.
2. 아래에 알려진 다른 사본들이 있는데, 전부 최신 상태보다 뒤처져 있을
   가능성이 높다:
   - `C:\Users\jjhjj\OneDrive\Desktop\webproject-main\` (바깥쪽) — build.gradle 등이
     여기 있었다가 없어졌다가 하는 등 상태가 계속 바뀌었던 폴더. 지금은
     `.claude`, `.idea`만 있고 실제 소스는 없을 수 있음 - 매번 `ls`로 확인.
   - `C:\Users\jjhjj\OneDrive\Desktop\webproject\webproject\` — GitHub
     리포를 별도로 클론해서 git push 문제를 테스트했던 스냅샷. 2026-07-30
     시점 기준으로 이미 여기(`webproject-main\webproject-main`)보다
     한참 뒤처져 있었음(마이페이지/한줄댓글/커뮤니티 기능 없음).
   - `C:\Users\jjhjj\OneDrive\Desktop\webproject\` (바깥쪽) — 원격 연결 없는
     별도의 작은 git 저장소(`master` 브랜치, 커밋 "작업 내용" 1개)가 하나
     더 있음. 이번 프로젝트와 무관해 보임.
3. **코딩 전에 항상 파일 목록/수정시간을 실제로 찍어서 어느 폴더가
   최신인지 재확인한 뒤 시작할 것.** (예: 여러 후보 경로의 같은 파일을
   `diff` 해서 줄 수가 더 많고 최신 기능이 들어있는 쪽을 진짜로 판단했음.)
- git 원격: `https://github.com/jihu1130/webproject.git` (main 브랜치).
  **2026-08-11 라운드에서 해결됨**: 이 폴더가 `git init`이 새로 된 상태(커밋 1개,
  origin과 히스토리가 전혀 이어지지 않는 unrelated history)였던 걸 발견해서,
  `git merge origin/main --allow-unrelated-histories`로 52개 충돌 파일을 전부
  수동 병합(양쪽 기능 다 보존 — 관리자 권한 체계 + 좋아요/북마크/마이페이지/프로필)한
  뒤 `git push`로 origin/main에 반영 완료(자세한 내용은 맨 아래 "2026-08-11 라운드"
  섹션 참고). 이제 정상적으로 커밋 히스토리가 이어져 있으니, 작업 마무리 시 커밋/푸시가
  필요한지 사용자에게 확인할 것(원래 규칙 그대로 유지).
- `.gitignore`로 `build/`, `.gradle/`, `.idea/`는 추적 제외.


## 1. 기획 의도 (`../계획/` 폴더 요약)

- **`기획.txt`**: "학교일정을 확인하고 같은 학교 학생들끼리 소통할 수 있는
  웹사이트". 핵심기능 4가지: ①학교 찾기(동명학교 주소 구분) ②학교일정
  API(시간표/급식/학사일정) ③로그인·회원가입 ④소통(게시글/댓글/투표/익명).
  부가기능: 질의응답 답변에 포인트 지급 + 티어 시스템.
  리스크 대책: API 캐싱, 24시간 TTL 만료, 신고 3회 자동 블라인드 + 금지어
  필터, 방학중 D-Day 표시 및 시간표 요청 차단, 포인트 어뷰징 방지.
- **`ERD.txt`**: User/School/School_Data(캐시)/Post/Post_Comment/
  School_Data_Comment 6개 테이블 설계. 실제 코드와 차이:
  - School_Data(캐시+TTL) 대신 Timetable/Meal 엔티티로 분리 구현, TTL
    컬럼 없음(3번 항목 참고).
  - **Post에 category(자유/익명/질의응답), report_count, is_blind가
    2026-08-04 커뮤니티 리뉴얼로 추가 완료.** ERD의 upload_count(첨부
    파일 수)만 아직 없음 — 첨부파일 업로드 기능 자체가 미구현.
  - **Post_Comment(게시글 댓글)도 리뉴얼로 구현 완료** (`PostComment`
    엔티티, ScheduleComment와 동일 패턴). School_Data_Comment는 기존대로
    `ScheduleComment`로 구현됨(날짜별 한줄 댓글).
  - ERD에는 없지만 신고 중복 방지를 위해 `PostReport`(post_id +
    reporter_id 유니크) 테이블을 별도로 추가함 — report_count는 Post에
    비정규화된 값으로 유지하고, PostReport는 "이 유저가 이미 신고했는지"
    체크용으로만 쓴다.
- **`파일 구조도/개발순서.txt`** (전체 로드맵, 진행 상황 갱신):
  ```
  [1] DB Entity & Repository 구축                         ✅ 완료
  [2] 학교 검색 기능 (학교명+주소 동적 조회, DB 저장)         ✅ 완료
  [3] 시간표·급식·학사일정 API 연동 + DB 캐싱(24h TTL) + 방학 D-DAY
                                                            🟡 API연동/캐싱만 완료, TTL·D-Day 미구현
  [4] 회원가입/로그인 + 내 학교·학년·반 자동 세팅              ✅ 완료
  [5] 날짜별 일정 한줄 댓글 (Schedule Comment)                ✅ 완료
  [6] 커뮤니티(자유/익명/QnA) + 포인트·티어 시스템             🟡 자유/익명/QnA 카테고리·댓글·신고
                                                               자동블라인드까지 완료, 포인트·티어만 남음
  [7] 신고·자동 블라인드 & 관리자(Admin) 페이지                🟡 신고 3회 자동블라인드 완료,
                                                               관리자 전용 화면은 아직 없음
                                                               (블라인드 글은 작성자 본인만 직접 URL로 열람 가능)
  ```
- **`파일구조도.txt`**: 도메인별 패키지 설계(user/school/post/comment/
  report/point/global). 실제 코드는 `report`/`comment`를 별도 패키지로
  분리하지 않고 전부 `post` 패키지 안에 넣었다(`post.domain.PostComment`,
  `post.domain.PostReport`, `post.service.PostCommentService` 등) —
  게시글이라는 하나의 애그리거트에 강하게 종속된 하위 기능이라 굳이
  패키지를 쪼개지 않기로 판단함. `point` 패키지는 아직 없다(포인트/티어
  미구현).


## 2. 현재 실제 구현 상태 (기획 대비 전체 표)

| 기획 기능 | 상태 |
|---|---|
| 학교 찾기(주소로 동명학교 구분) | ✅ 완료 (`/school/api/search`, NEIS schoolInfo 연동) |
| 시간표/급식/학사일정 조회 | ✅ 완료 (DB 캐시 우선 조회 패턴, TTL 없음) |
| 로그인/회원가입 | ✅ 완료 (BCrypt, Spring Security 세션 인증) |
| 회원가입 시 학교/학년/반 자동 세팅 | ✅ 완료 (검색으로 선택 강제, 로그인 후 캘린더에 자동 반영) |
| 마이페이지(프로필 수정) | ✅ 완료 (아이디/별명/비번/학교 변경) |
| 계정 소프트 삭제(탈퇴) | ✅ 완료 (2026-08-05(3차) 추가 — 10번 항목 참고. `User.deleted`/`deletedAt`, 마이페이지 수정 화면 하단 "계정 삭제"(비밀번호 재확인), 탈퇴 계정은 `CustomUserDetailsService`에서 `disabled(true)`로 로그인 차단. 작성 글/댓글은 그대로 남되 닉네임이 "탈퇴한 사용자"로 표시됨(관리자 화면은 예외 — 실제 닉네임 계속 노출)) |
| 날짜별 일정 한줄 댓글 | ✅ 완료 (같은 학교·학년·반끼리만 공유, 본인 글만 수정/삭제). **2026-08-05(4차) 변경**: 소프트 딜리트로 전환(`ScheduleComment.deleted/deletedAt`) — 5번 항목 참고 |
| 오늘의 한마디 신고 3회 자동 블라인드 | ✅ 완료 (2026-08-05(4차) 추가 — 5번 항목 참고. `ScheduleCommentReport`(schedule_comment_id+reporter_id 유니크), `ScheduleComment.reportCount`/`blind`/`reportCleared`, `PostComment`/`CommentReport`와 완전히 동일한 패턴. 관리자 페이지에서 게시글처럼 전체 조회/블라인드 토글/문제없음 처리/강제삭제/복구 가능(`/admin/schedule-comments`) |
| 커뮤니티 - 게시글 CRUD | ✅ 완료 (목록 페이지네이션/상세/조회수/작성/수정/삭제. **2026-08-05(2차) 변경**: 삭제가 소프트 딜리트로 전환됨 — 10번 항목 참고) |
| 커뮤니티 - 카테고리(자유/익명/QnA 구분) | ✅ 완료 (목록 탭 필터 + 상세/목록 배지, 익명이면 닉네임 대신 "익명" 표시. **2026-08-05 추가**: 작성 폼에서 카테고리별 안내 문구/placeholder가 JS로 동적 전환 — 익명은 "닉네임이 '익명'으로 표시됩니다" 안내, QnA는 제목 placeholder "무엇이 궁금한가요?"+내용 가이드 문구. 카테고리 값 자체·DB 스키마는 변경 없음) |
| 커뮤니티 - 게시글 댓글(Post_Comment) | ✅ 완료 (`/posts/{id}/comments` AJAX, ScheduleComment와 동일 패턴. **2026-08-05(2차) 변경**: 삭제가 소프트 딜리트로 전환됨) |
| 댓글 신고 3회 자동 블라인드 | ✅ 완료 (2026-08-05(2차) 추가 — 10번 항목 참고. `CommentReport`(comment_id+reporter_id 유니크), `PostComment.reportCount`/`blind`, PostReport와 완전히 동일한 패턴. 블라인드되면 작성자 본인·관리자를 제외한 사용자에게는 content 자체가 "신고 누적으로 블라인드 처리된 댓글입니다."로 서버 단 치환됨) |
| 커뮤니티 - 게시글 이미지 첨부 | ✅ 완료 (2026-08-05 추가 — 9번 항목 참고. 여러 장 업로드/미리보기, 로컬 파일시스템 저장(프로젝트 외부 `app.upload.dir`), 상세 페이지 갤러리, 수정 시 개별 삭제. **2026-08-05(2차) 변경**: 게시글이 소프트 삭제돼도 이미지 파일은 지우지 않고 보존함) |
| 커뮤니티 - 투표 기능 | ❌ 미구현 |
| 신고 3회 자동 블라인드 | ✅ 완료 (서로 다른 사용자 3명, 본인 글 신고 불가, 중복 신고 불가. **2026-08-05 추가**: `PostReport.reason` 필드로 신고 사유 선택 입력 가능 — 관리자 페이지에서 확인) |
| 신고 "문제없음" 관리자 판결 | ✅ 완료 (2026-08-05(3차) 추가 — 10번 항목 참고. `Post`/`PostComment.reportCleared`. 관리자가 "문제없음 처리"하면 그 게시물/댓글이 수정되기 전까지 재신고해도 카운트가 안 오르고 "이미 검토되어 문제없다고 판정된...입니다" 안내만 나감. 게시물/댓글 수정 시 또는 관리자가 다시 블라인드 처리하면 자동으로 리셋됨) |
| 금지어 필터 | ✅ 완료 (`BannedWordFilter`, 게시글 제목/내용 + 댓글 내용에 적용. 목록은 예시 수준이라 운영 전 확장 필요) |
| 관리자(Admin) 페이지 | ✅ 완료 (2026-08-05 추가 — 9번 항목 참고. `/admin/posts`, ROLE_ADMIN만 접근 가능. 신고 누적/블라인드 게시물 목록, 신고자·사유 상세, 수동 블라인드 On/Off. **2026-08-05(2차) 추가**: "삭제됨" 탭에서 소프트 삭제된 게시물(및 삭제된 댓글 포함 전체 댓글) 조회 + 복구, 게시물 상세에 첨부 이미지 갤러리도 표시. **2026-08-05(3차) 추가**: "전체 게시글" 탭(신고 여부 무관 전체 조회), 신고 "문제없음" 처리 버튼(게시물/댓글 각각), `/admin/users` 전체 계정 관리(권한 승격/해제, 탈퇴 처리/복구) 페이지, `/posts/{id}`↔`/admin/posts/{id}` 상호 이동 링크) |
| 관리자 권한 체계(총관리자/부관리자) | ✅ 완료 (2026-08-10(2차) 추가 — 8번 항목 "2026-08-10(2차) 라운드" 참고. `User.Role`에 `ROLE_SUPER_ADMIN` 추가 — username="admin" 계정 전용, DB에서 직접 승격시킴. 그 외 관리자는 전부 `ROLE_ADMIN`(부관리자)이고 서로의 권한을 승격/해제할 수 없음(계정 관리 자체가 총관리자 전용). 총관리자가 부관리자별로 신고/게시글/한마디 관리 권한 3개를 개별로 켜고 끌 수 있는 전용 대시보드(`/admin/users/admins`) 추가. `AdminAccessInterceptor`가 `/admin/**` 하위 경로별로 실제 접근을 강제) |
| 관리자 페이지 기본 진입점 | ✅ 완료 (2026-08-10(2차) 추가. "/admin"으로 들어가면 계정이 접근 가능한 첫 메뉴로 자동 이동 — 총관리자/신고 권한 보유자는 신고 관리부터, 그 외엔 게시글 관리 → 한마디 관리 순으로 폴백. 권한이 하나도 없으면 안내 화면(`/admin/access-denied`)) |
| 계정 비활성화(정지) | ✅ 완료 (2026-08-10(2차) 추가. `User.active` — 탈퇴(`deleted`)와 별개로 총관리자가 계정과 작성 글은 그대로 둔 채 로그인만 즉시 차단/해제할 수 있는 가벼운 조치. 계정 관리 페이지에서 활성화/비활성화 버튼으로 토글) |
| 계정 프로필 확인(관리자용) | ✅ 완료 (2026-08-10(2차) 추가. 계정 관리에서 닉네임 클릭 → 학교/학년/반/권한, 작성 게시글·댓글 수, 최근 게시글 5개를 보여주는 상세 화면) |
| 게시글/댓글 소프트 딜리트 | ✅ 완료 (2026-08-05(2차) 추가 — 10번 항목 참고. `Post`/`PostComment`에 `deleted`/`deletedAt` 추가, 하드 삭제로 인한 FK 500 에러 근본 해결, 관리자 강제 삭제도 소프트로 통일 + 복구 기능) |
| 커뮤니티 게시글 조회수 어뷰징 방지 | ✅ 완료 (HttpSession 기반, 같은 세션에서 같은 글 재조회 시 미증가) |
| 포인트/티어 시스템 | ❌ 미구현 (User 엔티티에 point/tier 컬럼 자체가 없음) |
| 24시간 TTL 캐시 만료 | ❌ 미구현 (Timetable/Meal에 updatedAt 없음, 한번 캐시되면 영구 반환) |
| 방학 D-Day 표시 / 방학중 시간표 차단 | ❌ 미구현 |

**→ 다음 우선순위 후보(8번 항목 참고): 투표 기능, 포인트/티어,
TTL/방학 D-Day. 어느 걸 먼저 할지 사용자에게 먼저 확인할 것.**


## 3. 기술 스택

- Java 21 / Spring Boot 4.1.0 (data-jpa, security, thymeleaf, webmvc)
- Hibernate 7.4.1 + MySQL 8 (`jdbc:mysql://localhost:3306/webschool`,
  계정 root/1234 — `application.yml`에 평문, 사용자가 그대로 두기로 결정함)
- **총관리자(ROLE_SUPER_ADMIN) 테스트 계정**: `username=admin / password=admin`
  (id=1). **2026-08-10(2차) 변경**: `User.Role`에 `ROLE_SUPER_ADMIN`이
  추가되면서 이 계정이 `ROLE_ADMIN`에서 `ROLE_SUPER_ADMIN`으로 승격됐다
  (SQL: `UPDATE users SET role='ROLE_SUPER_ADMIN' WHERE username='admin';`,
  또는 아래 `SuperAdminSeeder.java`로도 동일하게 처리 가능). 앱 안에는
  총관리자를 새로 만들거나 바꿀 방법이 전혀 없으므로(다른 계정을 총관리자로
  만드는 UI/API 자체가 없음) `admin`이 유일한 총관리자로 고정돼 있다. 그
  외의 관리자는 `/admin/users`(계정 관리, 총관리자 전용)에서 승격시킨
  `ROLE_ADMIN`(부관리자) — 회원가입 화면에서는 role을 선택할 방법이 없고
  `UserService.register()`가 항상 `ROLE_USER`로 고정하기 때문에, 관리자
  승격은 DB 직접 수정 또는 총관리자 계정으로 로그인 후 계정 관리 화면을
  이용하는 것 둘 중 하나뿐이다. 이 계정 정보도 DB 비밀번호와 마찬가지로
  개발용 평문이니 배포 전에는 반드시 바꿀 것.
  **`src/test/java/com/webschool/webschool/SuperAdminSeeder.java`
  (2026-08-10(3차) 추가)**: username="admin" 계정을 `ROLE_SUPER_ADMIN`으로
  승격시키는 전용 시더(`TestDataSeeder.java`와 같은 폴더, 별도 파일 — 계정
  생성이 아니라 기존 admin 계정의 역할만 바꾸는 책임이라 분리함). "admin"
  계정이 없으면 회원가입부터 하라는 안내와 함께 `IllegalStateException`을
  던지고, 이미 총관리자면 아무 것도 안 하므로(멱등) 여러 번 실행해도
  안전하다. `UserRepository`를 직접 써서 `AdminUserService.setRole()`의
  본인/총관리자 대상 방어 로직을 우회한다 — 그 방어 로직은 "런타임에
  관리자 화면에서 다른 계정을 조작할 때"를 위한 것이지 "부트스트랩으로
  admin 계정 자체를 총관리자로 만드는" 이 시더의 목적과는 다르기 때문.
  실행: `./gradlew test --tests "com.webschool.webschool.SuperAdminSeeder"`.
  **검증(2026-08-10(3차))**: `admin`을 SQL로 일부러 `ROLE_ADMIN`으로
  강등시켜본 뒤 이 시더를 실행해 다시 `ROLE_SUPER_ADMIN`으로 정상 승격되는
  것을 DB로 확인했고, 이미 총관리자인 상태에서 재실행해도 에러 없이
  그대로 유지되는 것도 확인했다.
  **일반 사용자 테스트 계정(2026-08-10 추가)**: `user1`~`user5`(아이디=
  비밀번호, 예: `user1/user1`), ROLE_USER. `src/test/java/com/webschool/
  webschool/TestDataSeeder.java`를 실행하면 자동 생성되고, 자유/익명/
  질의응답 카테고리별로 `user1`이 작성한 테스트 게시글 3개씩(총 9개)도
  함께 생성된다 — 8번 항목 "2026-08-10 라운드" 참고.
  **주의(2026-08-05 확인)**: 이 문서(3번 항목)엔 이 계정이 이미
  `ROLE_ADMIN`으로 승격되어 있다고 적혀 있었지만, 실제 DB를 확인해보니
  `ROLE_USER`로 되어 있었다(관리자 페이지 작업 중 `/admin/posts` 접근 시
  403이 떠서 발견함) — **DB 상태는 이 문서보다 훨씬 더 자주, 그리고 문서
  갱신 없이 바뀔 수 있으니 항상 실제 DB를 먼저 확인할 것.** 위 UPDATE문으로
  다시 `ROLE_ADMIN`으로 승격해뒀다. mysql 클라이언트가 PATH에 없어서
  PowerShell에서 `& "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe"
  -u root -p1234 -D webschool -e "..."` 형태로 직접 실행해야 했다(전체 경로
  필요, `mysql` 명령 자체가 PATH에 없음).
- **Jackson 3** — Spring Boot 4.1부터 패키지가 `com.fasterxml.jackson.*`이
  아니라 **`tools.jackson.*`** 로 바뀌었다. `NeisApiService`에서
  `tools.jackson.databind.ObjectMapper/JsonNode` import 하는 걸 볼 수 있음.
  새 코드에서 Jackson 쓸 때 이 네임스페이스 헷갈리지 말 것.
- java.net.http.HttpClient(JDK 내장)로 NEIS Open API 직접 호출.
- 프론트: Thymeleaf + Bootstrap5(CDN, 일부 페이지만) + FontAwesome +
  Pretendard + FullCalendar, 순수 Vanilla JS (프레임워크 없음).
- NEIS Open API 키: `application.yml`의 `neis.api.key`.
- Spring Data JPA `Page`/`Pageable`를 커뮤니티 목록 페이지네이션에 사용
  (`PostRepository.findAllByBlindFalseOrderByCreatedAtDesc` /
  `findAllByCategoryAndBlindFalseOrderByCreatedAtDesc`).
- **게시글 이미지 업로드 설정(2026-08-05 추가)**: `application.yml`의
  `spring.servlet.multipart.max-file-size: 5MB` /
  `max-request-size: 30MB`, `app.upload.dir: ../uploads`(프로젝트
  루트 `webproject-main/webproject-main` 기준 한 단계 밖, 즉
  `webproject-main\uploads\posts\{postId}\`에 실제 이미지 파일 저장 —
  git 저장소 밖이라 별도 `.gitignore` 처리 불필요). `WebConfig`
  (`global.config`)가 `/uploads/**` 요청을 이 디렉토리로 정적 서빙,
  `SecurityConfig` permitAll에도 `/uploads/**` 추가됨.


## 4. 패키지/파일 구조 (실제, 2026-08-05 기준, 관리자/이미지업로드/카테고리폼 반영)

```
com.webschool.webschool
├── WebschoolApplication.java
├── main.controller.HomeController        : "/" -> index.html
├── main.controller.AdminHomeController   : "/admin" 진입점 - 계정 권한에 따라 접근 가능한 첫 메뉴로
│                                            자동 리다이렉트(총관리자/신고권한 보유자는 /admin/reports,
│                                            그 외엔 /admin/posts → /admin/schedule-comments 순서로 폴백,
│                                            아무 권한도 없으면 /admin/access-denied). "/admin/access-denied"
│                                            (권한 없음 안내 화면)도 여기서 처리 (2026-08-10(2차) 추가)
├── global
│   ├── config.SecurityConfig             : /admin/**는 hasAnyRole("ADMIN","SUPER_ADMIN")까지 통과,
│   │                                        /uploads/**는 permitAll. accessDeniedHandler가 /admin/**
│   │                                        접근 거부 시 whitelabel 403 대신 /admin/access-denied로
│   │                                        리다이렉트 (2026-08-10(2차) 변경)
│   ├── config.WebConfig                  : /uploads/** -> app.upload.dir 정적 리소스 매핑 (2026-08-05 추가).
│   │                                        AdminAccessInterceptor를 "/admin/**"에 등록 (2026-08-10(2차) 추가)
│   ├── security.AdminAccessInterceptor   : "/admin/**" 안에서 부관리자별 세부 권한(신고/게시글/한마디
│   │                                        관리)을 검사하는 HandlerInterceptor. ROLE_SUPER_ADMIN은
│   │                                        무조건 통과, "/admin/users/**"는 총관리자 전용, 나머지는
│   │                                        User의 canManageReports/Posts/ScheduleComments 플래그로
│   │                                        판단. 권한 없으면 AccessDeniedException → SecurityConfig의
│   │                                        accessDeniedHandler가 처리 (2026-08-10(2차) 추가)
│   ├── advice.GlobalModelAdvice           : 모든 화면에 loginUser 자동 주입 (User 엔티티 전체 - 템플릿에서
│   │                                        loginUser.role/canManageReports 등 권한 플래그 바로 사용 가능)
│   └── util.PageUtils                    : (2026-08-10(3차) 추가) 관리자 목록들은 검색어 필터링을 DB 쿼리가
│                                            아니라 메모리 List에서 처리하는데(AdminPostService 등의 matches()
│                                            헬퍼), 그 필터링된 List를 커뮤니티와 동일한 페이지네이션 UI로
│                                            보여주기 위해 List를 Page로 잘라 감싸는 유틸(paginate()) +
│                                            "페이지당 N개" 요청값을 5~100 사이로 정규화하는 normalizeSize().
│                                            실제 DB 페이지 쿼리가 아니므로 목록이 아주 커지면 리포지토리 단
│                                            Pageable 쿼리로 바꿔야 함 - 8번 항목 2026-08-10(3차) 라운드 참고
├── user
│   ├── controller.AuthController         : 로그인/회원가입/마이페이지/중복확인/계정삭제
│   │                                        (POST /mypage/delete, 2026-08-05(3차) 추가)
│   ├── controller.AdminUserController    : /admin/users (총관리자 ROLE_SUPER_ADMIN 전용,
│   │                                        AdminAccessInterceptor가 강제) — 전체 계정 목록/권한
│   │                                        승격·해제/탈퇴 처리/복구 (2026-08-05(3차) 추가).
│   │                                        **2026-08-10(2차) 추가**: GET /admin/users/admins(부관리자
│   │                                        권한 토글 대시보드), GET /admin/users/{id}/profile(프로필),
│   │                                        POST .../permissions·deactivate·activate
│   ├── controller.AdminProfileController : GET /admin/profiles/{id} (2026-08-10(5차) 신규) — 게시글/
│   │                                        댓글/한마디 관리 화면에서 작성자 이름을 눌러 프로필을 보기
│   │                                        위한 경로. /admin/users/{id}/profile과 화면·데이터를
│   │                                        100% 재사용(AdminUserService.getUserProfile() +
│   │                                        admin/user-profile.html)하지만 별도 경로로 분리해서
│   │                                        신고/게시글/한마디 관리 권한이 하나라도 있는 부관리자까지
│   │                                        접근을 넓혔다(AdminAccessInterceptor에 별도 분기 추가) -
│   │                                        /admin/users/**는 여전히 총관리자 전용 그대로
│   ├── controller.UserProfileController  : GET /users/{id} (2026-08-10(5차) 신규) — 커뮤니티(일반
│   │                                        사용자) 공개 프로필. SecurityConfig에서 permitAll(GET).
│   │                                        닉네임 + 작성 게시글 목록만 보여주는 최소 정보 화면(학교/
│   │                                        학년/반 없음 - 사용자에게 직접 확인한 설계 결정)
│   ├── service.CustomUserDetailsService   : UserDetails.disabled(user.isDeleted() ||
│   │                                        !user.isActive())로 탈퇴/비활성화 계정 로그인 차단
│   │                                        (2026-08-05(3차) 추가, active 조건은 2026-08-10(2차) 추가)
│   ├── entity.User                        : username/password/nickname/
│   │                                        schoolName/schoolCode/atptCode/
│   │                                        schoolKind/grade/classNum/role/
│   │                                        deleted/deletedAt(2026-08-05(3차) 추가, 소프트 딜리트)/
│   │                                        **active(2026-08-10(2차) 추가, 총관리자의 계정 비활성화용 -
│   │                                        deleted와 별개), canManageReports/canManagePosts/
│   │                                        canManageScheduleComments(2026-08-10(2차) 추가, 부관리자별
│   │                                        권한 플래그), isSuperAdmin()/isAdmin() 헬퍼**
│   │                                        (point/tier 컬럼 아직 없음)
│   ├── dto.RegisterDto / MyPageUpdateDto
│   ├── dto.AdminUserSummaryDto            : 관리자 계정 목록 조회 전용 DTO (2026-08-05(3차) 추가).
│   │                                        **2026-08-10(2차)**: active/canManageReports/canManagePosts/
│   │                                        canManageScheduleComments 필드 추가
│   ├── dto.AdminUserProfileDto / AdminUserProfilePostDto : 계정 프로필 화면 전용 DTO(학교 정보,
│   │                                        권한, 작성 글/댓글 수, 최근 게시글 5개) (2026-08-10(2차) 추가)
│   └── repository.UserRepository          : findAllByOrderByIdAsc() 추가(관리자 목록용, 2026-08-05(3차))
├── school
│   ├── controller.SchoolController        : 캘린더 페이지 + 시간표/급식/학교검색/
│   │                                        반목록/한줄댓글(CRUD+신고) API. 신고는
│   │                                        POST /api/comments/{id}/report (2026-08-05(4차) 추가)
│   ├── controller.AdminScheduleCommentController : /admin/schedule-comments (ROLE_ADMIN 전용) —
│   │                                        AdminPostController와 동일 패턴(전체/삭제됨 탭,
│   │                                        블라인드 토글/문제없음 처리/강제삭제/복구) (2026-08-05(4차) 추가)
│   ├── service.NeisApiService              : NEIS 호출 전담
│   ├── service.SchoolService               : 시간표/급식 DB캐시 우선 조회
│   ├── service.ScheduleCommentService      : 날짜별 한줄 댓글 CRUD (본인 글만 수정/삭제,
│   │                                        내용 안 바뀌면 "수정됨" 안 뜨게 처리됨).
│   │                                        **2026-08-05(4차) 변경**: deleteComment()가 소프트
│   │                                        딜리트로 전환, reportComment() 추가(PostCommentService와
│   │                                        완전히 동일한 패턴 — 5번 항목 참고), toDto()가 blind
│   │                                        placeholder 치환 처리
│   ├── service.AdminScheduleCommentService  : 관리자 전용 한마디 관리 로직 — AdminPostService와
│   │                                        동일 패턴으로 완전히 분리 (2026-08-05(4차) 추가)
│   ├── domain.School / Timetable / Meal
│   ├── domain.ScheduleComment               : school/targetDate/grade/classNm/user/content/
│   │                                        createdAt/updatedAt + **deleted/deletedAt/reportCount/
│   │                                        blind/reportCleared(2026-08-05(4차) 추가, PostComment와
│   │                                        동일 구조)**
│   ├── domain.ScheduleCommentReport         : schedule_comment_id+reporter_id 유니크 제약,
│   │                                        CommentReport와 완전히 동일한 구조 (2026-08-05(4차) 추가)
│   ├── domain.CalendarEvent / Event        : (정의만 있고 미사용 — 정리 후보)
│   ├── dto.TimetableDto / SchoolCalendarDto / SchoolSearchResultDto / ScheduleCommentDto
│   │                                        (ScheduleCommentDto에 blind 필드 2026-08-05(4차) 추가)
│   ├── dto.ScheduleCommentReportResultDto / AdminScheduleCommentSummaryDto
│   │                                        (2026-08-05(4차) 추가, 관리자·신고 결과 전용 DTO)
│   └── repository.SchoolRepository / TimetableRepository / MealRepository /
│                  ScheduleCommentRepository / ScheduleCommentReportRepository(2026-08-05(4차) 추가) /
│                  CalendarEventRepository(미사용)
└── post                                    : 커뮤니티 — 자유/익명/QnA 카테고리 + 댓글 + 신고/블라인드 +
    │                                        이미지 첨부 + 관리자 화면(전부 이 패키지 안에 있음)
    ├── controller.PostController           : /posts 목록(카테고리 필터)·상세(조회수 세션중복방지)·
    │                                        작성·수정·삭제·신고(POST /posts/{id}/report, @ResponseBody).
    │                                        작성/수정/삭제 시 PostImageService 호출도 여기서 오케스트레이션
    ├── controller.PostCommentController    : /posts/{postId}/comments 목록/작성/수정/삭제/신고
    │                                        (POST .../{commentId}/report, 전부 @ResponseBody,
    │                                        SchoolController 댓글 API와 동일 스타일. 신고는 2026-08-05(2차) 추가)
    ├── controller.AdminPostController      : /admin/posts (ROLE_ADMIN 전용) — 목록(?status=all/deleted,
    │                                        기본은 신고 관리)/상세/블라인드 On-Off/강제삭제(소프트)/
    │                                        복구(POST .../restore)/문제없음 처리(POST .../clear-report,
    │                                        게시물·댓글 각각). 기존 PostController와 분리된 별도
    │                                        컨트롤러(2026-08-05 추가, 삭제됨 탭·복구는 2026-08-05(2차),
    │                                        전체 게시글 탭·문제없음 처리는 2026-08-05(3차) 추가).
    │                                        **2026-08-10(3차) 추가**: page/size 파라미터 + PageUtils
    │                                        페이지네이션. **2026-08-10(4차) 추가**: 상세 화면이 목록의
    │                                        검색/필터/page/size(listStatus 등 model 속성)를 들고 다니며
    │                                        모든 액션(blind/unblind/restore/clear-report)이 그 상태를
    │                                        유지한 채 상세로, delete만 목록으로 리다이렉트(내부 static
    │                                        클래스 `ListState` 참고)
    ├── controller.AdminCommentController   : /admin/comments (ROLE_ADMIN 전용, canManagePosts와 같은
    │                                        권한 - 2026-08-10(4차) 신규) — 전체/삭제됨 탭 + 검색 +
    │                                        페이지네이션 + 인라인 액션(블라인드/삭제/복구/문제없음
    │                                        처리). AdminScheduleCommentController와 동일 패턴이지만
    │                                        대상이 PostComment. 예전엔 신고된 댓글만 신고 관리에서
    │                                        보였고 평범한 댓글은 게시글 상세를 하나씩 열어야 봤는데,
    │                                        이 화면은 게시글과 무관하게 댓글만 전체 훑어볼 수 있다
    │                                        (사용자 지적 - 6번/8번 항목 참고). 목록 응답은
    │                                        `AdminCommentReportSummaryDto`를 재사용(postId/postTitle이
    │                                        이미 있어서 "소속 게시글" 링크에 그대로 씀,
    │                                        deleted/deletedAt 필드는 이번에 추가)
    ├── service.PostService                 : 목록(카테고리 필터+페이지네이션, deleted=false만)/상세(블라인드
    │                                        가시성 판단+조회수 증가, **deleted=true면 작성자 본인도 예외
    │                                        없이 404 취급**)/CRUD/신고(reportPost, 3명 누적 시 자동 블라인드),
    │                                        내용 안 바뀌면 "수정됨" 안 뜨게 처리됨(comment와 동일 패턴).
    │                                        **2026-08-05(2차) 변경**: deletePost()가 소프트 딜리트로 전환됨
    │                                        (Post.deleted=true로만 표시, 물리 삭제 없음 — 6번 항목 버그#7 참고).
    │                                        **2026-08-05(3차) 변경**: reportPost()가 post.reportCleared를
    │                                        먼저 체크해서 이미 "문제없음" 판결난 글이면 안내만 하고 카운트
    │                                        안 올림, updatePost()는 내용이 실제로 바뀌면 reportCleared를
    │                                        false로 리셋(재검토 필요), displayNickname()은 탈퇴한 작성자면
    │                                        "탈퇴한 사용자"로 치환(익명 카테고리는 그대로 "익명" 우선)
    ├── service.PostCommentService          : 게시글 댓글 CRUD, ScheduleCommentService와 동일 패턴.
    │                                        **2026-08-05(2차) 변경**: deleteComment()도 소프트 딜리트로
    │                                        전환, getComments()는 deleted=false만 반환. 게시물 자체가
    │                                        deleted=true면 댓글 조회/작성 모두 차단. reportComment()
    │                                        추가 — PostService.reportPost()와 완전히 동일한 패턴(서로
    │                                        다른 3명 신고 시 자동 블라인드, 본인 댓글 신고 불가, 중복
    │                                        신고 불가). toDto()가 blind=true이고 작성자 본인도 관리자도
    │                                        아니면 content를 안내 문구로 서버 단 치환(displayNickname
    │                                        패턴과 동일한 방식), 작성자가 탈퇴했으면 닉네임도 "탈퇴한
    │                                        사용자"로 치환(2026-08-05(3차)). **2026-08-05(3차) 변경**:
    │                                        reportComment()도 comment.reportCleared 체크, updateComment()도
    │                                        내용이 실제로 바뀌면 reportCleared 리셋 (PostService와 동일 패턴)
    ├── service.PostImageService             : 게시글 첨부 이미지 업로드/검증/삭제(개별 이미지 단위), 실제
    │                                        파일은 app.upload.dir(프로젝트 외부)에 저장, DB엔 경로만 저장
    │                                        (2026-08-05 추가). **2026-08-05(2차)**: 게시물 전체 삭제 시
    │                                        이미지를 지우던 deleteAllImagesForPost()는 소프트 딜리트
    │                                        전환으로 더 이상 필요 없어져 제거함(이미지는 항상 보존)
    ├── service.AdminPostService             : 관리자 전용 로직 — 신고누적/블라인드 글 조회, 신고 상세,
    │                                        블라인드 토글. 기존 PostService는 건드리지 않고 완전히 분리
    │                                        (2026-08-05 추가). **2026-08-05(2차) 변경**: deletePost()/
    │                                        restorePost() 모두 소프트 딜리트 기반(Post.deleted 토글)으로
    │                                        동작 — getDeletedPosts()(삭제됨 탭), getPostDetail()에 이미지·
    │                                        댓글(삭제된 것 포함) 목록도 함께 내려줌. **2026-08-05(3차)
    │                                        변경**: getAllPosts()(전체 게시글 탭) 추가, clearReport()/
    │                                        clearCommentReport()(문제없음 처리, blind도 함께 false로)
    │                                        추가, setBlind(true)는 reportCleared를 false로 리셋(재블라인드는
    │                                        "문제없음" 판결을 뒤집는 것과 같음)
    ├── domain.Post                         : title/content/author/category(FREE/ANONYMOUS/QNA)/
    │                                        viewCount/reportCount/blind/createdAt/updatedAt/
    │                                        deleted/deletedAt(2026-08-05(2차) 추가, 소프트 딜리트)/
    │                                        **reportCleared(2026-08-05(3차) 추가, 신고 "문제없음" 판결)**
    ├── domain.PostComment                  : post/author/content/createdAt/updatedAt/
    │                                        deleted/deletedAt(2026-08-05(2차) 추가, 소프트 딜리트)/
    │                                        **reportCleared(2026-08-05(3차) 추가)**
    ├── domain.PostReport                   : post_id+reporter_id 유니크 제약 (중복 신고 방지용,
    │                                        report_count 자체는 Post에 비정규화 저장). reason(신고 사유,
    │                                        선택 입력) 컬럼 2026-08-05 추가
    ├── domain.PostImage                    : post/storedPath(app.upload.dir 기준 상대경로)/
    │                                        originalFilename/sortOrder/createdAt (2026-08-05 추가)
    ├── domain.CommentReport                : comment_id+reporter_id 유니크 제약, reason 선택 입력.
    │                                        PostReport와 완전히 동일한 구조 (2026-08-05(2차) 추가)
    ├── dto.PostListItemDto / PostDetailDto / PostFormDto / PostCommentDto / PostReportResultDto
    ├── dto.PostImageDto / AdminPostSummaryDto / AdminPostDetailDto / AdminReportItemDto
    │                                        (2026-08-05 추가, 전부 관리자·이미지 조회 전용 DTO).
    │                                        AdminPostSummaryDto/AdminPostDetailDto에 deleted/deletedAt
    │                                        필드, AdminPostDetailDto에 images/comments 필드 2026-08-05(2차) 추가.
    │                                        둘 다 reportCleared 필드 2026-08-05(3차) 추가
    ├── dto.AdminCommentItemDto              : 관리자용 댓글 조회 DTO(삭제된 댓글도 deleted 플래그로 포함).
    │                                        reportCount/blind 필드 추가 — 관리자에게는 블라인드 여부와
    │                                        무관하게 항상 원본 content를 보여줌(2026-08-05 추가,
    │                                        reportCount/blind는 2026-08-05(2차), reportCleared는
    │                                        2026-08-05(3차) 추가)
    ├── dto.CommentReportResultDto           : PostReportResultDto와 동일한 모양(reportCount, blind)이지만
    │                                        댓글 신고 전용으로 이름을 분리(2026-08-05(2차) 추가)
    ├── repository.PostRepository            : findAllByBlindFalseAndDeletedFalse.../findAllByCategory...
    │                                        (일반 목록, deleted 제외) / findReportedOrBlindPosts()
    │                                        (관리자 신고관리 탭, deleted 제외) / findAllByDeletedTrueOrder
    │                                        ByDeletedAtDesc()(관리자 삭제됨 탭) — 2026-08-05(2차) 갱신 /
    │                                        findAllByDeletedFalseOrderByCreatedAtDesc()(관리자 전체
    │                                        게시글 탭, 2026-08-05(3차) 추가)
    ├── repository.PostCommentRepository     : findByPost_IdOrderByCreatedAtAsc(관리자용, 삭제 포함) /
    │                                        findByPost_IdAndDeletedFalseOrderByCreatedAtAsc(일반用)
    │                                        — 2026-08-05(2차) 갱신
    ├── repository.PostReportRepository / PostImageRepository
    │                                        : deleteByPost_Id()는 소프트 딜리트 전환으로 더 이상
    │                                        필요 없어져 둘 다 제거함(2026-08-05(2차))
    ├── repository.CommentReportRepository   : existsByComment_IdAndReporter_Username() — PostReportRepository와
    │                                        동일 패턴 (2026-08-05(2차) 추가)
    └── util.BannedWordFilter               : 게시글/댓글 등록 시 금지어 검사 (정적 유틸,
                                             단어 목록은 최소 예시 수준 — 운영 전 확장 필요)

resources/templates
├── fragments/navbar.html                  : 공용 네비바 (th:fragment="navbar(active)"),
│                                             "커뮤니티" 메뉴(/posts) + 관리자(부관리자 포함)에게만
│                                             보이는 "관리자" 메뉴(→ /admin, sec:authorize=
│                                             "hasAnyRole('ADMIN','SUPER_ADMIN')") — 링크 대상이
│                                             /admin/posts에서 /admin으로 바뀜(2026-08-10(2차) 변경,
│                                             AdminHomeController가 권한별 첫 메뉴로 리다이렉트)
├── index.html                             : 홈 (히어로+bento 캐러셀+기능허브 그리드)
├── school/calendar.html                   : 캘린더(로그인 전용)
├── post/list.html                         : 카테고리 탭 필터 + 목록(블라인드 글은 서버에서 이미 제외됨)
├── post/detail.html                       : 상세 + 블라인드 배너(본인/관리자만 노출) + 신고 버튼 +
│                                             첨부 이미지 갤러리 + 댓글 위젯(post-detail.js가 렌더링,
│                                             댓글별 신고 버튼/블라인드 배지도 여기서 렌더링됨). 하단에
│                                             ROLE_ADMIN에게만 보이는 "관리자 페이지에서 보기"
│                                             (→ /admin/posts/{id}) 링크 2026-08-05(3차) 추가
├── post/form.html                         : 작성/수정 공용 폼 — 카테고리 라디오(변경 시 post-form.js가
│                                             안내 문구/placeholder 동적 전환) + 이미지 첨부(다중 업로드,
│                                             미리보기, 수정 모드에서 기존 이미지 삭제 체크박스)
├── admin/post-list.html                   : 관리자 대시보드 — 상단에 "게시물 관리"/"계정 관리" 큰 탭,
│                                             그 아래 "신고 관리"/"전체 게시글"/"삭제됨" 상태 탭
│                                             (post-category-tab 스타일 재사용, ?status=all|deleted 쿼리로
│                                             전환)으로 테이블 내용이 바뀜(2026-08-05 추가, 삭제됨 탭은
│                                             2026-08-05(2차), 전체 게시글 탭·문제없음 배지·상위 탭은
│                                             2026-08-05(3차) 추가)
├── admin/post-detail.html                 : 관리자 상세 — 신고자/사유 목록, 첨부 이미지 갤러리(post.css
│                                             .post-image-gallery 재사용), 댓글 목록(삭제된 것도 배지로
│                                             표시, 블라인드된 댓글은 "블라인드(신고 N회)" 배지 + 항상
│                                             원본 content 노출). 삭제 안 된 글이면 블라인드 토글+강제삭제
│                                             버튼, 삭제된 글이면 복구 버튼만 노출(2026-08-05 추가, 이미지/
│                                             댓글/복구는 2026-08-05(2차)). **2026-08-05(3차) 추가**: "문제없음
│                                             처리" 버튼(게시물 전체 + 댓글별 아이콘 버튼), "문제없음" 배지,
│                                             하단에 "게시글 페이지에서 보기"(→ /posts/{id}, 삭제된 글이면 숨김)
├── admin/user-list.html                   : 총관리자 전용(AdminAccessInterceptor가 강제) 전체 계정
│                                             목록 — 상태(활동중/비활성화됨/탈퇴함)/아이디/닉네임(→
│                                             프로필 링크)/학교/권한 배지(총관리자·부관리자·학생) +
│                                             승격·권한해제/비활성화·활성화/탈퇴 처리·복구 버튼.
│                                             로그인한 총관리자 본인 행은 "(총관리자)"만 표시하고
│                                             액션 버튼은 항상 숨김(서버 단에서도 총관리자 대상 변경은
│                                             막혀 있음) (2026-08-05(3차) 추가, 프로필 링크·비활성화·
│                                             3단계 역할 배지는 2026-08-10(2차) 추가). 상단에
│                                             "관리자 권한 관리로 →" 링크로 admin-permissions.html
│                                             이동
├── admin/admin-permissions.html           : "1개의 관리자 대시보드 페이지" - 총관리자가 부관리자별로
│                                             신고/게시글/한마디 관리 권한 3개를 체크박스로 켜고 끄는
│                                             화면(변경 시 onchange="this.submit()"으로 즉시 저장,
│                                             별도 저장 버튼 없음). 총관리자 행은 "모든 권한 보유
│                                             (변경 불가)"로 읽기 전용 표시. 계정 관리(/admin/users)와
│                                             똑같이 총관리자 전용 (2026-08-10(2차) 추가)
├── admin/user-profile.html                : "상대방의 프로필을 확인하는 기능" - 총관리자가 계정
│                                             관리에서 닉네임을 클릭하면 여는 상세 화면. 학교/학년/반,
│                                             권한, 작성 게시글·댓글 수, 최근 작성 게시글 5개(관리자
│                                             게시글 상세로 링크)를 보여준다 (2026-08-10(2차) 추가)
├── admin/access-denied.html               : 부관리자가 권한 없는 관리자 메뉴에 접근했을 때 뜨는
│                                             안내 화면(SecurityConfig의 accessDeniedHandler가
│                                             /admin/** 요청 거부 시 여기로 리다이렉트) (2026-08-10(2차) 추가)
├── admin/schedule-comment-list.html       : 관리자 "한마디 관리" — post-list.html/user-list.html을
│                                             섞은 구조(전체/삭제됨 상태 탭 + 검색 + 행별 인라인 액션
│                                             버튼: 문제없음 처리/블라인드 토글/삭제/복구). 한마디는
│                                             내용이 짧아 별도 상세 페이지 없이 목록에서 바로 관리
│                                             (2026-08-05(4차) 추가)
├── admin/report-list.html                 : "게시물"/"댓글" 2탭이었던 것에 "오늘의 한마디" 3번째
│                                             탭 추가(2026-08-05(4차)) — 단, 이 페이지 자체는 검색
│                                             기능이 없음(8번 항목 "4차 라운드" 새 후보 목록 참고)
└── user/{login,register,mypage,mypage-edit}.html : login.html에 계정 삭제 완료 안내
                                             (`?accountDeleted=true`), mypage-edit.html 하단에 "계정 삭제"
                                             폼(비밀번호 재확인) 2026-08-05(3차) 추가

resources/static/css
├── theme.css       : 브랜드 컬러 변수(전 페이지 공통, 제일 먼저 로드)
├── navbar.css       : 네비바
├── auth.css         : 로그인/회원가입/마이페이지수정 공용. 계정 삭제 danger-zone
│                       (.mypage-delete-desc/.mypage-delete-btn) 2026-08-05(3차) 추가
├── index.css        : 홈 전용 (히어로/bento 캐러셀/기능허브)
├── calendar.css      : 캘린더 전용. .comment-report-btn/.comment-blind-badge/.comment-item-blind
│                       (한마디 신고 버튼/블라인드 배지, post.css의 .post-comment-report-btn 계열과
│                       동일한 톤으로 추가 — 2026-08-05(4차))
├── school-search.css : 학교검색 드롭다운 (공용 위젯 스타일)
├── mypage.css        : 마이페이지 전용
├── post.css          : 커뮤니티 전용 (카테고리 탭/배지, 블라인드 배너, 신고 버튼, 댓글 위젯
│                       (.post-comment-*), 카테고리 안내 문구(.post-category-hint 등), 이미지
│                       업로드 미리보기/갤러리(.post-image-*)까지 전부 이 파일 하나에 있음.
│                       댓글별 신고 버튼(.post-comment-report-btn)/블라인드 배지
│                       (.post-comment-blind-badge) 2026-08-05(2차) 추가. 상세 페이지 하단
│                       링크 줄(.post-detail-footer-links) 2026-08-05(3차) 추가 — admin/
│                       post-detail.html에서도 재사용)
└── admin.css         : 관리자 전용 — 대시보드 테이블/상태 배지/상세 카드 (2026-08-05 추가,
                        `post.css`의 `.post-category-badge` 등과 함께 사용됨). **2026-08-05(3차)
                        추가**: .admin-status-cleared(문제없음 배지, 초록), .admin-user-actions/
                        .admin-user-self-label(계정 관리 테이블), .admin-comment-clear-form
                        (댓글별 문제없음 처리 아이콘 버튼). **2026-08-05(4차)**: 새 CSS 클래스 추가 없이
                        `admin/schedule-comment-list.html`이 기존 `.admin-user-actions`/`.admin-table`/
                        `.admin-status-badge`/`.admin-comment-content-cell`를 그대로 재사용함 — 한마디
                        관리 화면 전용 스타일이 필요하면 여기부터 확인할 것. **2026-08-10(2차) 추가**:
                        `.admin-permission-list`/`.admin-permission-row`/`.admin-permission-toggle`
                        (관리자 권한 관리 페이지의 부관리자별 권한 체크박스 행), `.admin-profile-grid`/
                        `.admin-profile-field`/`.admin-profile-stats`/`.admin-profile-stat`(계정
                        프로필 페이지의 정보 그리드·통계 카드)

resources/static/js
├── school-search.js  : 학교검색 드롭다운 공용 위젯 (initSchoolSearchWidget)
├── class-select.js   : NEIS 실제 반 목록 조회 공용 헬퍼 (loadClassOptions)
├── grade-select.js   : 학교급(초/중/고)별 학년 select 구성 공용 헬퍼 (buildGradeOptions)
├── register.js       : 회원가입 폼 로직
├── mypage-edit.js     : 마이페이지 수정 폼 로직
├── post-detail.js     : 게시글 상세 - 댓글 CRUD(AJAX, calendar.js 댓글 로직과 동일 패턴) + 신고 버튼
│                        (2026-08-05: 신고 시 사유 prompt() 입력 추가, 비워도 신고 가능). **2026-08-05(2차)**:
│                        `#postCommentForm` DOM 존재 여부로 로그인 상태를 판단해(isLoggedIn) 댓글별 신고
│                        버튼 노출 여부 결정, reportComment() 함수 추가 — post 신고 버튼과 동일 패턴
│                        (prompt로 사유 입력 → POST .../comments/{id}/report → 결과에 따라 alert 후
│                        loadComments()로 새로고침, blind=true여도 댓글 자체는 남아있으니 리다이렉트 없음)
├── post-form.js       : 게시글 작성/수정 폼 - 카테고리별 안내 문구/placeholder 동적 전환 +
│                        이미지 첨부 미리보기 (2026-08-05 추가)
├── index.js           : 홈 "지금 확인해보세요" 캐러셀(시간표⇄학사일정, 자동재생+hover정지)
└── calendar.js         : 캘린더 렌더링 + 학교검색 + 빠른이동 + 한줄댓글 + 오늘/선택 표시.
                         **2026-08-05(4차) 변경**: renderComments()가 c.blind일 때 블라인드 배지를
                         붙이고, 본인 댓글이 아니면 신고 버튼을 렌더링(post-detail.js의 댓글 신고
                         버튼과 동일 패턴 — `/school/**`은 로그인 필수라 isLoggedIn 체크 없이 항상
                         노출). reportComment() 함수 추가(POST /school/api/comments/{id}/report)
```

**주의**: `school-search.js` / `class-select.js` / `grade-select.js`는
캘린더/회원가입/마이페이지수정 3곳에서 전부 재사용하는 공용 위젯이다.
수정할 때 한 화면만 생각하지 말고 세 화면 모두에 영향 간다는 걸 기억할 것.


## 5. 핵심 동작 원리 메모

- **브랜드 컬러**: 인디고(`--brand-1: #4f46e5`) → 스카이블루
  (`--brand-2: #0ea5e9`) 2색 그라데이션. `theme.css`의 변수만 바꾸면
  전체 반영됨.
- **로그인 게이트**: `SecurityConfig`에서 `/school/**`은 인증 필요.
  `/posts`, `/posts/*`, `/posts/*/comments`(GET, 목록·상세·댓글조회)는
  비로그인도 열람 가능하지만 `/posts/new`, `/posts/*/edit`(GET) 및
  POST/PUT/DELETE 계열(작성/수정/삭제/신고/댓글 작성-수정-삭제)은 인증
  필요. `/school/api/search`, `/school/api/classes`는 회원가입 페이지에서
  로그인 없이 학교 검색해야 해서 예외적으로 permitAll.
  `formLogin().defaultSuccessUrl("/", false)` → alwaysUse=false라서
  로그인 전 원래 가려던 보호 페이지로 자동 복귀함(Spring Security
  기본 RequestCache 기능, 별도 코드 없음).
- **"수정됨" 표시는 실제 내용이 바뀔 때만**: `ScheduleCommentService.
  updateComment()`, `PostService.updatePost()`, `PostCommentService.
  updateComment()` 셋 다 새 내용을 기존 값과 비교해서 **실제로 다를
  때만** `updatedAt`을 찍는다(Post는 제목/내용/카테고리 중 하나라도
  바뀌면 갱신). 그대로 재저장하면 "수정됨" 배지가 뜨지 않는다 (예전엔
  무조건 찍어서 버그였음 — 앞으로 비슷한 "수정" 기능을 또 만든다면 이
  패턴을 따를 것).
- **작성자 본인만 수정/삭제 가능**: Post/ScheduleComment/PostComment
  전부 `author.getUsername().equals(username)` 비교로 권한 체크,
  아니면 `IllegalArgumentException`을 던지고 컨트롤러가 잡아서
  리다이렉트하거나 에러 메시지를 보여줌.
- **게시글 신고 → 자동 블라인드**: `PostService.reportPost()` —
  서로 다른 사용자 3명이 신고하면(`PostReport`의 post_id+reporter_id
  유니크 제약으로 같은 사람 중복 신고 차단) `Post.blind = true`.
  본인 글은 신고 불가(`author.username.equals(username)`이면
  `IllegalArgumentException`). 블라인드된 글은 `PostRepository`의
  목록 조회 쿼리(`findAllByBlindFalseOrderByCreatedAtDesc` 등)에서
  자체적으로 제외되고, `PostService.getDetail()`도 요청자가 작성자
  본인이거나 `User.Role.ROLE_ADMIN`이 아니면 "게시물을 찾을 수
  없습니다" 예외를 던져 **존재하지 않는 것처럼** 처리한다(별도의
  블라인드 안내 화면 없이 목록으로 리다이렉트). 작성자/관리자가 직접
  URL로 들어가면 콘텐츠는 그대로 보이고 `post/detail.html`
  상단에 `.post-blind-banner`로 "신고 누적으로 블라인드 처리됨" 배너만
  추가로 뜬다. **2026-08-05 추가**: `/admin/posts`에서 신고 누적/블라인드
  글을 모아보고, 신고자 사유(`PostReport.reason`, 선택 입력)까지 확인하고,
  블라인드를 수동 On/Off할 수 있다(`AdminPostController`/
  `AdminPostService`, 4번 항목 참고). **강제 삭제 버튼은 2026-08-05(2차)부터
  하드 삭제가 아니라 소프트 딜리트다** — 아래 소프트 딜리트 항목 참고.
- **게시글/댓글 소프트 딜리트(2026-08-05 2차 라운드 추가)**: `Post`/
  `PostComment`에 `deleted`(boolean)/`deletedAt`(nullable) 컬럼을 추가해서,
  작성자 본인 삭제(`PostService.deletePost()`/`PostCommentService.
  deleteComment()`)든 관리자 강제 삭제(`AdminPostService.deletePost()`)든
  **물리적으로 지우지 않고 `deleted=true`로 표시만 한다.** 일반 사용자
  화면(목록/상세/댓글 조회/작성)에서는 `deleted=true`인 게시물·댓글을
  전부 "존재하지 않는 것처럼" 완전히 제외한다 — 블라인드와 달리 **작성자
  본인도 예외 없이** 못 본다(관리자가 봐야 하면 `/admin/posts`의 별도
  경로 사용). 첨부 이미지(`PostImage`)는 게시물이 삭제돼도 파일/DB
  레코드를 그대로 보존한다(사용자가 명시적으로 물어봄 → "이미지도 함께
  보존"으로 확정, 8번 항목 이전 후보 참고). 관리자 페이지엔 `/admin/
  posts?status=deleted`로 "삭제됨" 탭이 있어서 삭제된 게시물(작성자 본인
  삭제분 포함) 목록과, 상세 화면에서 삭제된 댓글까지 포함한 전체 댓글
  목록·첨부 이미지를 확인할 수 있고, **복구**(`POST /admin/posts/{id}/
  restore`, `Post.deleted`를 다시 `false`로) 버튼으로 되돌릴 수 있다.
  이 전환으로 `postCommentRepository.deleteByPost_Id()` 등 자식 레코드를
  미리 지우던 하드 삭제용 리포지토리 메서드들은 전부 제거됐다(6번 항목
  버그#7이 이걸로 근본 해결됨 — 애초에 물리 삭제를 안 하니 FK 문제
  자체가 발생하지 않음).
- **댓글 신고 → 자동 블라인드(2026-08-05 2차 라운드 추가)**:
  `PostCommentService.reportComment()` — `PostService.reportPost()`와
  완전히 동일한 패턴(`CommentReport`의 comment_id+reporter_id 유니크
  제약, 서로 다른 사용자 3명이 신고하면 `PostComment.blind = true`,
  본인 댓글 신고 불가, 중복 신고 불가, 사유 선택 입력). **다만 가시성
  처리 방식은 게시글과 다르다** — 게시글은 블라인드되면 목록에서 아예
  빠지고 상세 페이지는 작성자/관리자만 URL로 직접 열람 가능하지만, 댓글은
  다른 댓글들과 같은 스레드 안에 계속 남아있어야 자연스러우므로 **목록에서
  빼지 않고 `PostCommentService.toDto()`가 content 필드 자체를
  "신고 누적으로 블라인드 처리된 댓글입니다."로 서버 단 치환**한다(작성자
  본인이거나 `User.Role.ROLE_ADMIN`이면 원본 content 그대로 노출 —
  `displayNickname()`의 서버 단 치환 패턴과 동일한 방식). 관리자 페이지의
  게시글 상세 화면(댓글 섹션)에서는 블라인드 여부와 무관하게 항상 원본
  content와 "블라인드(신고 N회)" 배지를 함께 보여준다.
- **오늘의 한마디 신고 → 자동 블라인드(2026-08-05 4차 라운드 추가)**:
  `ScheduleCommentService.reportComment()` — `PostCommentService.reportComment()`와
  완전히 동일한 패턴(`ScheduleCommentReport`의 schedule_comment_id+reporter_id
  유니크 제약, 서로 다른 사용자 3명이 신고하면 `ScheduleComment.blind = true`,
  본인 한마디 신고 불가, 중복 신고 불가, `reportCleared` 체크/리셋도 동일).
  가시성 처리 방식도 댓글과 동일 — 목록에서 빼지 않고 `ScheduleCommentService.
  toDto()`가 content를 "신고 누적으로 블라인드 처리된 한마디입니다."로
  서버 단 치환(작성자 본인/관리자는 원본 노출). **한마디 삭제도 이번에
  하드 삭제 → 소프트 딜리트로 전환됨**(`ScheduleComment.deleted/deletedAt`) —
  `ScheduleCommentReport`가 FK로 참조하기 때문에 하드 삭제를 유지했다면
  6번 항목 버그#7과 동일한 문제가 재발했을 것. **관리자 관리 화면은
  게시글과 동등한 수준으로 구현됨**: `/admin/schedule-comments`(전체/삭제됨
  탭, 검색, 행별 블라인드 토글/문제없음 처리/강제삭제/복구 — Post처럼
  별도 상세 페이지 없이 목록에서 바로 처리) + `/admin/reports?type=schedule`
  (신고 관리 3번째 탭). 단, `ScheduleComment`는 `PostComment`와 달리
  **탈퇴 사용자 닉네임 치환("탈퇴한 사용자")과 `BannedWordFilter` 금지어
  검사가 아직 적용되지 않았다** — 의도적 축소가 아니라 이번 라운드 요청
  범위(신고/블라인드/관리자 관리) 밖이라 건드리지 않은 것뿐이니, 다음에
  한마디 관련 작업을 할 때 이 불일치를 인지하고 있을 것(8번 항목 "4차
  라운드" 새 후보 목록에도 기록해둠).
- **신고 "문제없음" 관리자 판결(2026-08-05 3차 라운드 추가)**: `Post`/
  `PostComment`에 `reportCleared`(boolean) 컬럼을 추가. 관리자가 게시물/
  댓글 상세에서 "문제없음 처리" 버튼을 누르면(`AdminPostService.
  clearReport()`/`clearCommentReport()`) `reportCleared=true`이면서
  동시에 `blind=false`로 바뀐다. 그 다음부터 `PostService.reportPost()`/
  `PostCommentService.reportComment()`는 **가장 먼저** `reportCleared`를
  체크해서, true면 신고 자체를 기록하지 않고 "이미 검토되어 문제없다고
  판정된 게시물/댓글입니다"라는 `IllegalArgumentException`을 던진다(기존
  에러 처리 파이프라인을 그대로 타므로 프론트 alert()에 그대로 표시됨,
  추가 JS 작업 필요 없었음). **`reportCleared`는 두 가지 경우에 자동으로
  다시 `false`로 리셋된다**: ① `updatePost()`/`updateComment()`에서
  내용이 실제로 바뀌었을 때(재검토가 필요하다는 뜻 — "수정됨" 판단과
  똑같은 `changed` 플래그를 재사용), ② 관리자가 다시 수동으로 블라인드
  처리(`setBlind(true)`)할 때(재블라인드는 "문제없음" 판결을 뒤집는
  것과 같음). 관리자 화면에는 "문제없음" 초록 배지(`.admin-status-cleared`)
  로 표시된다.
- **계정 소프트 삭제(탈퇴, 2026-08-05 3차 라운드 추가)**: `User`에
  `deleted`/`deletedAt` 컬럼 추가. 마이페이지 수정 화면(`/mypage/edit`)
  하단에 비밀번호 재확인 폼이 있고, `UserService.deleteAccount()`가
  비밀번호를 검증한 뒤 `deleted=true`로만 표시한다(계정 row 자체는 안
  지움). `CustomUserDetailsService.loadUserByUsername()`이
  `UserDetails.builder().disabled(user.isDeleted())`를 설정해두기 때문에,
  탈퇴한 계정으로 로그인 시도하면 Spring Security가 자동으로
  `DisabledException`을 던지고 `formLogin`의 `failureUrl`(`/login?
  error=true`)로 빠진다(계정 존재 여부를 알려주지 않기 위해 일반 로그인
  실패와 같은 메시지를 보여줌 — 별도 처리 안 함). **작성한 게시글/댓글은
  삭제되지 않고 그대로 남지만**, 일반 사용자 화면에서는 `PostService.
  displayNickname()`/`PostCommentService.toDto()`가 작성자의
  `isDeleted()`를 체크해서 닉네임을 "탈퇴한 사용자"로 치환한다(단
  `ANONYMOUS` 카테고리 게시글은 탈퇴 여부와 무관하게 항상 "익명" 우선).
  **관리자 화면(`AdminPostService`)은 이 치환을 적용하지 않고 항상 실제
  닉네임/아이디를 보여준다** — 관리자는 실제 신원을 알아야 하므로 의도적
  예외. 관리자가 직접 탈퇴 처리/복구시키는 것도 가능하다
  (`AdminUserService.deleteUser()`/`restoreUser()`, 본인 확인 비밀번호
  없이 처리하되 관리자 자기 자신의 계정은 자기 자신을 변경 못 하게
  막아뒀다 — 그렇게 안 하면 실수로 마지막 관리자 계정을 잠글 수 있음).
- **익명 카테고리 표시**: `Post.Category.ANONYMOUS`인 글은
  `PostService.displayNickname()`에서 **서버 단에서** 닉네임을
  "익명" 문자열로 치환해 DTO에 담는다(템플릿에서 조건부 렌더링하는
  게 아니라 DTO 자체가 이미 익명화된 값을 가짐). 단, `mine`(수정/삭제
  권한 판단)은 항상 실제 로그인 사용자와 실제 작성자 username을
  비교하므로 익명 글이어도 작성자 본인에게는 수정/삭제 버튼이 정상
  노출된다. **댓글은 게시글 카테고리와 무관하게 항상 실제 닉네임을
  보여준다** (ERD/기획에 댓글 익명화 언급이 없어 범위에서 제외한
  설계 결정 — 필요해지면 재검토).
- **금지어 필터**: `post.util.BannedWordFilter.validate()`를
  `PostService`(제목/내용)와 `PostCommentService`(댓글 내용) 양쪽에서
  호출. 공백 제거 후 부분 문자열 포함 여부만 검사하는 단순 구현이라
  우회가 쉽다 — 정교한 필터링(초성 우회, 특수문자 치환 등)이 필요하면
  이 클래스부터 교체할 것. 단어 목록은 최소 예시만 넣어뒀으니 운영
  전 반드시 보강해야 함.
- **캘린더 오늘 날짜 / 선택된 날짜 표시**: FullCalendar 셀에
  `.fc-day-today`(오늘, 노란 배경)와 `.fc-day-selected`(선택됨, 브랜드
  그라데이션 배경) 두 클래스를 동시에 줄 수 있는데, 둘 다 선택된 경우
  `calendar.css`의 `.fc-day-today.fc-day-selected` 조합 셀렉터가 노란
  배경은 유지하고 파란 박스섀도우 링을 추가로 씌운다. "오늘" 버튼
  (`quickTodayBtn`)을 누르면 `calendar.today()`로 뷰만 옮기는 게 아니라
  `selectedDateStr`도 오늘로 갱신하고 `applySelectedDayStyle()`을
  다시 호출해서 선택 표시까지 오늘로 옮긴다.
- **로그인 사용자의 학교 자동 반영**: `calendar.html`이 로그인 사용자의
  schoolName/schoolCode/atptCode/schoolKind/grade/classNum을
  `window.__USER_SCHOOL__`로 인라인 주입 → `calendar.js`의
  `applyMySchoolIfAvailable()`이 페이지 로드 시 자동으로 캘린더 검색조건에
  채워 넣음.
- **학교종류별 학년 범위**: `grade-select.js`의 `maxGradeFor()` —
  학교종류명에 "초등"이 포함되면 6학년까지, 그 외(중/고)는 3학년까지.
- **반 목록**: 하드코딩이 아니라 NEIS `classInfo` API로 실제 존재하는
  반만 조회(`fetchClassList`). 학교 미선택 시에만 fallback으로 1~20반.
- **초등학교 시간표 미지원**: `NeisApiService.searchSchools()`가
  학교종류명에 "초등"이 포함된 결과를 검색 결과에서 아예 제외함
  (초등학교는 시간표 API 파라미터 구조가 달라서 아직 미지원).
- **한줄 댓글 공유 범위**: 같은 학교(school_id) + 같은 날짜(targetDate) +
  같은 학년(grade) + 같은 반(classNm) 조합으로만 조회/공유됨.
- **커뮤니티 게시글 조회수 중복 방지**: `PostController.
  shouldCountView()`가 `HttpSession`에 `viewedPostIds`(Set&lt;Long&gt;)를
  들고 있다가, 이미 본 글이면 `PostService.getDetail(..., countView=false)`
  를 호출해 `viewCount`를 올리지 않는다. 세션 기반이라 로그인 여부와
  무관하게(익명 사용자도 세션은 생김) 동작하지만, **브라우저를 새로
  열거나 세션이 만료되면 다시 카운트된다** — DB에 영구 기록해서 막는
  방식이 아니라 어뷰징을 "어렵게" 만드는 수준의 경량 방지책임을
  기억할 것.
- **아이디 규칙**: 영문+숫자만 허용 (`^[A-Za-z0-9]+$`), 프론트(JS)와
  백엔드(UserService) 양쪽에서 검증.
- **NEIS 파싱 방식 2가지 공존**: 시간표/급식/학사일정(구버전, 문자열
  자르기 파싱, 특수문자 있으면 깨질 수 있는 구조적 약점 있음) vs
  학교검색·반목록(신버전, Jackson JsonNode 파싱, 더 안전함). 새 NEIS
  연동을 추가한다면 Jackson 방식을 따를 것.
- **게시글 이미지 첨부(2026-08-05 추가)**: `PostImageService`가 업로드
  전 확장자(jpg/jpeg/png/gif/webp)와 용량(장당 5MB, `application.yml`의
  `spring.servlet.multipart.max-file-size`와 별도로 서비스 단에서도
  체크)을 검증한다. 실제 파일은 `app.upload.dir`(`../uploads`, 프로젝트
  폴더 밖) 아래 `posts/{postId}/{UUID}.{ext}`로 저장하고 DB
  (`PostImage.storedPath`)엔 상대 경로만 저장 — `WebConfig`가
  `/uploads/**`를 이 디렉토리로 정적 서빙한다. `PostController`의
  작성/수정 흐름에서 **이미지 검증 → 게시물 저장 → 이미지 저장** 순서로
  호출해서, 이미지 검증 실패 시 게시물 자체가 만들어지지 않도록
  했다(고아 게시물 방지). 수정 폼에서는 기존 이미지에 삭제 체크박스를
  두고 `removeImageIds`로 전달, 저장 시점에 실제 파일도 함께 지운다.
  게시물 삭제(본인/관리자 강제삭제) 시에도 `PostImageService.
  deleteAllImagesForPost()`가 파일과 빈 디렉토리까지 정리한다.
- **카테고리별 작성 폼 안내(2026-08-05 추가)**: `post-form.js`가 카테고리
  라디오 `change` 이벤트를 감지해서 서버 재조회 없이 안내 문구/제목
  placeholder만 클라이언트에서 바꾼다 — 자유는 변화 없음, 익명은
  "닉네임이 '익명'으로 표시됩니다" 안내(`#postCategoryHint`), QnA는
  제목 placeholder가 "무엇이 궁금한가요?"로 바뀌고 내용 위에 안내
  문구(`#postContentGuide`)가 뜬다. `Post.Category` enum 값이나 DB
  스키마는 전혀 건드리지 않았다.


## 6. 알려진 버그 이력 (다시 만들지 않기 위한 기록)

1. `calendar.js`가 캘린더 제목을 `innerText` 통째로 교체하면서 아이콘이
   이모지로 바뀌어 보이던 버그 → 아이콘(`<i>`)과 텍스트(`<span
   id="calendarTitleText">`)를 분리해서 텍스트만 갱신하도록 수정 완료.
2. 회원가입 "중복확인" 버튼 클래스명을 `.btn-check`로 지었다가 —
   **Bootstrap이 라디오/체크박스 숨김용으로 이미 쓰는 예약 클래스**라서
   버튼이 `position:absolute; pointer-events:none`이 되어 클릭이
   씹히던 버그. `.btn-usercheck`로 개명해서 해결. **앞으로 커스텀 CSS
   클래스명 지을 때 Bootstrap 예약 클래스(`btn-*`, `form-*` 등)와
   겹치지 않는지 항상 확인할 것.**
3. `.auth-field input { width:100% }` 공용 규칙이 인라인 배치된
   중복확인 버튼 옆 입력창까지 덮어버리던 문제 →
   `.auth-field-inline input { flex:1; width:auto; min-width:0; }`로
   범위를 좁혀서 해결.
4. 반 선택 드롭다운 하드코딩 값 오류 → 지금은 NEIS 실제 반 목록 조회로
   대체되어 문제 자체가 사라짐.
5. `ScheduleCommentService.updateComment()`가 내용이 실제로 안
   바뀌어도 무조건 `updatedAt`을 찍어서 "수정됨"이 잘못 뜨던 버그 →
   내용이 실제로 다를 때만 갱신하도록 수정(5번 항목 패턴 참고, 이후
   `PostService.updatePost()`도 처음부터 이 패턴으로 구현되어 있음).
6. (버그는 아니고 함정) PowerShell에서 bcrypt 해시(`$2a$10$...`)를
   테스트 계정 삽입용 SQL에 넣을 때 **큰따옴표(`"..."`) 문자열은 절대
   쓰지 말 것** — `$2a`, `$10` 등을 변수 보간 시도하면서 해시가 통째로
   깨진다(백슬래시로 이스케이프하려 해도 PowerShell은 백슬래시를
   이스케이프 문자로 안 씀). **작은따옴표(`'...'`) 리터럴 문자열 +
   SQL 문자열은 `''`로 이스케이프**하는 방식만 안전하게 동작함(예:
   `$sql = 'INSERT INTO users (...) VALUES (''posttestuser'', ''$2a$10$...'')'`).
7. **(2026-08-05 발견/수정) `PostService.deletePost()`가 댓글/신고 이력이
   있는 게시물을 삭제하려 하면 500 에러가 났던 버그** — `post_comments`/
   `post_reports`가 `post_id` FK를 갖는데(ON DELETE CASCADE 없음)
   `deletePost()`는 `postRepository.delete(post)`만 호출해서 자식 레코드가
   남아있으면 `DataIntegrityViolationException`이 터졌다. 이미지 업로드
   기능(게시물 삭제 시 파일도 정리)을 붙이면서 실제로 댓글이 달린 글을
   삭제해보다가 재현해서 발견함. `deletePost()` 안에서
   `postCommentRepository.deleteByPost_Id()` / `postReportRepository.
   deleteByPost_Id()`를 먼저 호출한 뒤 게시물을 삭제하도록 수정했다.
   **2026-08-05(2차) 후속: 이 문제는 근본적으로 해결됨** — 게시글/댓글
   삭제가 소프트 딜리트로 전환되면서(5번 항목 "게시글/댓글 소프트
   딜리트" 참고) 애초에 물리 삭제(`postRepository.delete(post)`)를 하지
   않으므로 FK 문제 자체가 발생할 수 없다. 이때 만든
   `deleteByPost_Id()` 계열 메서드들은 전부 제거됨. **앞으로 Post와
   연관된 자식 엔티티(테이블)를 새로 추가한다면, 하드 삭제 대신 이
   소프트 딜리트 패턴(deleted/deletedAt 플래그)을 따르는 게 기본값이어야
   한다** — cascade 설정이 없는 한 하드 삭제는 이 문제가 똑같이 재발한다.
8. **(2026-08-10 발견/수정) `users` 테이블에 엔티티에 없는 레거시 `name`
   컬럼(NOT NULL, 기본값 없음)이 남아있어서 회원가입 자체가 막혀있던 버그**
   — `User` 엔티티(`user/entity/User.java`)에는 `name` 필드가 전혀 없는데
   (닉네임 필드로 대체된 뒤 지워지지 않고 남은 것으로 추정), 실제 DB
   스키마엔 `name VARCHAR(50) NOT NULL`이 그대로 남아있었다. `ddl-auto:
   update`는 엔티티에서 사라진 기존 컬럼의 제약조건을 자동으로 완화해주지
   않기 때문에, `UserService.register()`로 새 계정을 만들 때마다 `Field
   'name' doesn't have a default value` `SQLException`이 터졌다(테스트
   데이터 시더로 `user1` 계정을 만들다가 처음 재현됨 — 8번 항목
   "2026-08-10 라운드" 참고). **즉 이 컬럼이 남아있는 동안은 이 시더뿐
   아니라 실제 웹 회원가입 폼도 똑같이 실패했을 것이다** — 관리자 계정
   (`admin`)은 이 문제가 생기기 전에 이미 만들어져 있었거나 SQL로 직접
   삽입됐을 가능성이 높아 지금까지 발견되지 않았던 것으로 보임.
   `ALTER TABLE users MODIFY COLUMN name VARCHAR(50) NULL;`로 nullable
   처리해서 데이터 손실 없이 해결했다(컬럼 자체를 지우지는 않음 — 과거
   데이터가 남아있을 가능성을 고려해 최소 침습적으로 처리). **앞으로
   `ddl-auto: update`가 못 잡아내는 스키마 드리프트(컬럼 제약조건 변경,
   삭제된 컬럼 잔존 등)가 있을 수 있으니, 회원가입/게시글 작성 등 INSERT
   경로에서 원인을 알 수 없는 `DataIntegrityViolationException`이 나면
   엔티티 코드가 아니라 실제 DB `DESCRIBE 테이블명;` 결과부터 확인할 것.**
9. **(2026-08-10(2차) 발견/수정) `ddl-auto: update`가 `users.role` enum에
   새 값(`ROLE_SUPER_ADMIN`) 추가를 감지하지 못한 사례** — `User.Role`에
   `ROLE_SUPER_ADMIN`을 추가하고 컨텍스트를 재기동해봤더니, `active`/
   `can_manage_reports`/`can_manage_posts`/`can_manage_schedule_comments`
   컬럼(새로 추가한 boolean 필드들)은 정상적으로 자동 생성됐는데 `role`
   컬럼의 MySQL enum 정의(`enum('ROLE_ADMIN','ROLE_USER')`)는 그대로였다.
   **이전에(2026-08-04 커뮤니티 리뉴얼 때) `Post.Category`에 값을 추가했을
   땐 Hibernate가 `alter table posts modify column category enum(...)`를
   자동으로 실행해줬던 것과 대조적** — 같은 `@Enumerated(EnumType.STRING)`
   패턴인데 왜 이번엔 감지를 못 했는지 원인은 조사하지 않았다(재현성이
   낮아 보임 - 앞으로 enum 값을 추가하는 다른 필드에서도 이 문제가 있는지
   매번 실제로 `DESCRIBE`로 확인해볼 것, 자동으로 될 거라고 가정하지 말 것).
   `ALTER TABLE users MODIFY COLUMN role ENUM('ROLE_USER','ROLE_ADMIN',
   'ROLE_SUPER_ADMIN');`로 직접 고치고, 총관리자 승격 UPDATE문도 같이
   실행해서 해결했다.
10. **(2026-08-10(2차) 발견/수정) "마지막 남은 관리자는 강등/탈퇴 불가"
    가드가 총관리자 개념 도입 후 오히려 버그가 된 사례** — `AdminUserService.
    setRole()`/`deleteUser()`와 `UserService.deleteAccount()`에 있던
    `countByRoleAndDeletedFalse(ROLE_ADMIN) <= 1`이면 막는 로직은, `ROLE_ADMIN`이
    유일한 관리자 역할이던 시절엔 맞는 방어 로직이었지만 `ROLE_SUPER_ADMIN`을
    추가한 뒤에는 **유일하게 남은 부관리자를 총관리자가 권한 해제하려 해도
    "마지막 남은 부관리자 계정의 권한은 해제할 수 없습니다"라며 조용히
    막아버리는 버그**가 됐다(총관리자가 항상 별도로 존재하므로 이 가드는
    이제 항상 거짓이어야 하는데, 코드를 고치지 않고 냅뒀다가 실제로 curl로
    데모 시나리오를 재현하다가 발견함 — 부관리자 1명을 승격→권한 부여까지
    했다가 다시 권한 해제를 시도했더니 응답은 302(정상)인데 DB에는 반영이
    안 되고 있었다). 세 곳 모두 이 가드를 완전히 제거했다(그 대신
    `deleteAccount()`에는 총관리자 본인의 자진 탈퇴를 막는 새 가드를
    추가함 — 총관리자는 앱 안에서 다시 만들 방법이 없는 유일한 계정이라
    이쪽은 진짜로 막아야 함). **교훈: 권한 체계에 새로운 "항상 존재하는
    상위 역할"을 추가할 때는, 기존에 "이 역할이 마지막 하나 남았을 때"를
    가정하고 짜여진 방어 로직이 전부 그 가정 위에 있었다는 걸 의심하고
    하나하나 다시 검토할 것 — 컴파일 에러 없이 조용히 잘못 동작하는
    타입의 버그라 테스트 없이는 알아채기 어렵다.**
11. **(2026-08-10(3차) 발견/수정) `admin/fragments/nav.html`의 관리자 4단계
    탭이 총관리자를 포함해 아무에게도 안 보이던 버그** — 사용자가 "admin으로
    로그인해도 관리자 대시보드 페이지에 원래 있던 1~4번 탭이 하나도 안
    보인다"고 스크린샷으로 제보해서 발견함. 원인은 `loginUser.role ==
    'ROLE_SUPER_ADMIN'`처럼 **`loginUser`(DTO가 아니라 실제 `User` 엔티티)의
    `role` 필드(=`User.Role` enum)를 문자열 리터럴과 직접 `==` 비교**한
    부분 — 이게 항상 false로 평가돼서 4개 `th:if` 전부 실패했다. 다른
    관리자 화면(`admin/user-list.html`, `admin/admin-permissions.html`,
    `admin/user-profile.html`)이 문제없이 동작했던 이유는 거기서 비교하는
    `user.role`/`admin.role`/`profile.role`이 전부 `AdminUserSummaryDto`/
    `AdminUserProfileDto`의 `String` 필드(서비스 단에서 `user.getRole().name()`으로
    미리 변환해둠)라서 String==String 비교였기 때문 — `post.category ==
    'ANONYMOUS'`(`PostDetailDto.category`도 마찬가지로 이미 `.name()`된
    `String` 필드) 패턴을 보고 "엔티티 enum을 문자열과 직접 비교해도 되는
    패턴이 이미 검증돼 있다"고 잘못 일반화한 게 원인이었다. `loginUser.role`처럼
    **DTO를 거치지 않고 엔티티를 그대로 템플릿에서 쓸 때는 반드시
    `loginUser.role.name() == '...'`처럼 `.name()`을 명시적으로 붙여야
    한다**(엔티티 그대로 쓰는 `loginUser`는 `GlobalModelAdvice`가 모든
    화면에 주입하므로 다른 템플릿에서도 같은 실수를 반복하지 않도록 주의할
    것 — `user/mypage.html`은 원래부터 `.name()`을 붙이고 있어서 안전했다).
    같은 김에 `user/mypage.html`의 역할 배지도 "관리자/학생" 2단계뿐이라
    총관리자가 "학생"으로 잘못 표시되고 있던 걸 발견해서 "총관리자/부관리자/
    학생" 3단계로 함께 고쳤다.

- `./gradlew bootRun` (기본 포트 8888, `application.yml`의 `server.port`).
- MySQL은 로컬에 이미 떠 있고 정상 연결됨 확인됨(`webschool` 스키마,
  `ddl-auto: update`로 엔티티 변경 시 테이블 자동 반영).
- devtools가 붙어있어서 **이미 `bootRun`으로 떠 있는 프로세스가 있다면
  직접 재시작할 필요 없이 `./gradlew compileJava processResources -q`만
  실행해도 devtools가 자동으로 클래스/리소스를 다시 로드한다** (2026-08-04
  커뮤니티 리뉴얼 작업 때 새 엔티티/컨트롤러/템플릿/CSS 변경을 이 방법
  하나로 전부 반영해서 확인함 — Post에 카테고리 3개 컬럼을 추가하는
  스키마 변경까지 별도 재시작 없이 `ddl-auto: update`가 알아서 처리했음).
  그래도 반영이 안 되는 것처럼 보이면(브라우저 캐시, devtools 재시작
  타이밍 등) `bootRun` 재시작이 최후 수단.
- **8888 포트에 이미 떠 있는 프로세스를 함부로 끄지 말 것**: 검증용으로
  새로 `bootRun`을 실행하면 "Port 8888 was already in use"로 실패하는 게
  정상 — 그 프로세스가 devtools로 자동 반영되는 진짜 서버이니 그대로 두고
  위 방법(compileJava+processResources)으로 반영 여부만 확인할 것.
  꼭 새로 띄워야 하면 `--args='--server.port=8899'`처럼 다른 포트를
  쓰고, 확인 후 본인이 새로 띄운 프로세스만 종료할 것.
- 브라우저 자동화(Claude_Browser 도구) 사용 시 스크린샷 도구가 자주
  타임아웃 나는 환경이었음 — 실패하면 `read_page`/`javascript_tool`
  (getBoundingClientRect, elementFromPoint, 폼 submit 이벤트 직접
  dispatch 등)로 대체 검증하는 게 더 안정적이었다. FullCalendar 날짜
  셀은 `element.click()` 같은 합성 클릭에 반응하지 않아서, 실제
  좌표 기반 클릭(`computer` 도구의 `left_click` + `read_page`로 얻은
  ref)이 필요했다. `confirm()` 다이얼로그가 뜨는 삭제 버튼을 자동화로
  테스트할 땐 `window.confirm = function(){ return true; }`로 임시
  오버라이드하면 편하다.
- **파일 업로드(`<input type="file">`) 화면은 Claude_Browser 도구로 자동화
  테스트가 안 된다** — 네이티브 OS 파일 선택 다이얼로그를 열어야 해서
  `computer`/`javascript_tool`로 우회 불가. 2026-08-05 이미지 업로드
  기능 검증 때는 `curl -c cookies.txt -b cookies.txt -d "username=admin&
  password=admin" http://localhost:8888/login`로 로그인해서 쿠키를 받고,
  `curl -F "title=..." -F "images=@파일경로;type=image/png" .../posts`
  형태로 실제 백엔드(검증/저장/DB/파일시스템)를 직접 테스트했다. 테스트용
  이미지 파일은 PowerShell `System.Drawing.Bitmap`으로 즉석 생성함(이
  환경엔 `python3`이 없음). 브라우저 쪽은 카테고리 라디오 변경 시 안내
  문구/placeholder가 바뀌는지 등 순수 JS/DOM 동작만 `read_page`/
  `get_page_text`/`javascript_tool`로 확인.
- **mysql 클라이언트가 PATH에 없음** — PowerShell에서
  `& "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe" -u root
  -p1234 -D webschool -e "..."` 형태로 전체 경로를 지정해야 한다.
- **이 Bash(Git Bash) 환경에서 `curl --data-urlencode "필드=한글값"`으로
  한글이 포함된 `application/x-www-form-urlencoded` 요청을 보내면
  간헐적으로 값이 깨진다** (2026-08-05(2차), 회원가입 테스트 계정을
  만들다가 발견). 증상이 round 1의 "댓글 내용에 한글 넣었더니
  `MalformedInputException`" 버그(6번 항목 버그#7 근처)와는 다르게,
  이번엔 500/400 에러 없이 **조용히 실패**해서 더 헷갈린다 — 응답은
  정상적인 302로 오지만 `Location` 헤더가 원래 컨트롤러가 반환하는
  값(`/login?registered=true`)이 아니라 `/login;jsessionid=...`로
  바뀌어 있고, DB에는 아무 row도 생기지 않는다. **폼 필드 값에 실제
  한글이 필요 없는 테스트(회원가입 학교종류 등 자유 텍스트)라면 그냥
  영문(`highschool` 등)으로 대체해서 우회하는 게 제일 빠르다.** 진짜
  한글 값이 필요한 테스트(게시글 제목/내용 등)는 이미 확인된 대로
  `-F`(multipart/form-data) 방식은 문제없이 동작하니 그쪽을 쓸 것 —
  라운드 1에서 이미지 업로드 테스트할 때도 `-F "title=한글제목..."`은
  정상 동작했다. 이 curl/쉘 로케일 이슈의 근본 원인은 조사하지 않았고,
  실제 사용자가 브라우저로 폼을 제출할 때도 재현되는 서버 버그인지는
  확인 안 됨 — 다음에 이 증상을 다시 보면 먼저 이 메모부터 볼 것.


## 8. 다음 작업 후보 (사용자에게 우선순위 확인 후 진행)

**2026-08-04 커뮤니티 리뉴얼로 완료된 항목(더 이상 후보 아님)**: 게시글
카테고리 분리(자유/익명/질의응답), 게시글 댓글(Post_Comment), 익명 게시글
닉네임 표시, 신고 3회 자동 블라인드, 금지어 필터, 커뮤니티 조회수
어뷰징(세션 기반) 방지. 자세한 구현 내용은 4번/5번/2번 항목 참고.

**2026-08-05 작업으로 완료된 항목(더 이상 후보 아님)**: 관리자(Admin) 페이지
(`/admin/posts`, 신고 목록/사유 확인/블라인드 토글/강제삭제), 게시글 이미지
첨부(다중 업로드/미리보기/갤러리/파일 정리), 카테고리별 작성 폼 안내 문구
차이(익명/QnA). 신고 사유(`PostReport.reason`) 입력도 이때 함께 추가됨.
자세한 구현 내용은 4번/5번/2번/6번(버그 수정 포함) 항목 참고.

### 2026-08-05 2차 라운드 — 대규모 작업 로드맵 (사용자가 한 번에 요청, 세션을 나눠 순서대로 진행 중)

사용자가 UI/UX, 게시글·댓글 개선, 캘린더, 마이페이지, 아키텍처, 신규 기능까지
총 10단계짜리 큰 작업 목록을 한 번에 요청했고, **사용자 본인이 세션을 나눠
순서대로 진행하는 걸 권장**했다. 원본 요청은 대화 기록에 있고, 사용자가 지정한
진행 순서는 다음과 같다(괄호 안은 2026-08-05 기준 진행 상태):

1. **(✅ 완료 2026-08-05)** 게시글 삭제 500 에러 해결 + 게시글/댓글 소프트
   딜리트 전환 — 위 2번 항목 "게시글/댓글 소프트 딜리트" 표 항목과 5번/6번
   항목 참고. 관리자 페이지에 "삭제됨" 탭 + 복구 기능도 이때 함께 추가.
2. **(✅ 완료 2026-08-05(2차))** 댓글 신고 기능(`CommentReport`, `PostReport`와
   동일 패턴, 3명 신고 시 자동 블라인드) — 위 2번 항목 "댓글 신고 → 자동
   블라인드" 표/5번 항목 참고. 관리자 페이지 게시글 상세의 댓글 목록에도
   신고 수/블라인드 배지가 함께 표시됨.
3. **(✅ 완료 2026-08-05(3차))** 계정 소프트 삭제(User.deleted/deletedAt, 로그인 차단,
   마이페이지 탈퇴 버튼) + 관리자 페이지에 전체 계정 관리(`/admin/users`,
   6-7보다 범위를 넓혀 권한 승격/해제까지) 추가 — 아래 "3차 라운드" 섹션 참고.
4. **(다음 후보 — 백엔드 미착수, 아래 "4번 항목 중 디자인만" 참고)** 대댓글
   (PostComment.parentComment, 1depth 제한) — 아직 구현 안 됨(백엔드 모델
   변경이 필요해서 2026-08-06 디자인 전용 라운드 범위에서 제외함). 익명
   게시글의 "댓글"→"답변" 명칭 변경은 **2026-08-06 완료**(아래 라운드
   섹션 참고). 관리자 페이지에서만 익명 글/답변의 실제 작성자를 명시적
   액션으로 조회하는 기능도 아직 미구현.
5. **(다음 후보)** 게시글 본문 이미지 삽입 리치 에디터(Quill/Toast UI 등
   CDN 로드형, HTML 저장 + Jsoup 화이트리스트 sanitize 필수).
6. **(다음 후보)** 마이페이지 "내가 쓴 글" 목록(+ 게시글 숨기기 토글,
   Post.hidden — blind/deleted와는 다른 개념), 게시글 검색(제목+내용).
7. **(✅ 디자인만 완료 2026-08-06)** 캘린더: 오늘/선택 날짜 강조 애니메이션
   완료(아래 라운드 섹션 참고). **기간별 일정 밑줄 표시는 여전히 미구현** —
   원인은 스킵이 아니라 데이터 모델 자체가 없어서다(`Event` 엔티티는
   `eventDate` 단일 날짜만 가지고 있고, 기간을 표현할 수 있는
   `CalendarEvent`는 정의만 있고 완전히 미사용 상태 + NEIS 학사일정도
   하루 단위로만 조회함). 이 기능을 실제로 하려면 백엔드(월 단위 배치
   조회 + FullCalendar events 소스 연동)부터 손대야 해서 범위 밖으로 뺐다.
8. **(✅ 디자인만 완료 2026-08-06)** UI/애니메이션: 수정/삭제 버튼 브랜드
   톤 재정의, 상대 시간 표기(timeago), 네비바 메뉴명 간소화, 스크롤
   애니메이션 — 전부 완료. 상대 시간 표기는 **정확한 시각 툴팁**까지는
   못 갔다(원래 계획은 "timeago + 정확한 시각 툴팁"이었는데, 서버가 내려주는
   날짜 문자열 자체가 `"MM.dd HH:mm"`뿐이라 연도가 없다 — 아래 라운드
   섹션 "설계 트레이드오프" 참고).
9. **(✅ 완료 2026-08-06(2차) — 완전 재작성)** index.html/css/js. 처음엔
   부분 리프레시만 했었는데, 사용자가 같은 날 "index.html 부분 삭제하고
   처음부터 다시 만들어줘, 지금이랑 비슷하게 말고 애니메이션 기능 등 여러
   웹 메인페이지를 참고해서"라고 재요청해서 세 파일을 통째로 새로 썼다.
   아래 "2026-08-06(2차) 라운드" 섹션 참고.
10. **(다음 후보, 언제든 병행 가능)** `NeisApiService` deprecated 경고 해결,
    특성화고/마이스터고 시간표·급식 미조회 원인 분석/수정.

### 2026-08-06 라운드 — 보류됐던 UI/UX(7·8·9번) + 익명 "답변" 명칭 디자인만 구현

사용자가 "webschool 그룹의 대화내용과 CLAUDE.md를 보고 다음 기능을 구현하자,
디자인 부분을 구현해줘"라고 요청함(대화내용 파일은 실제로 찾지 못해서 무시하고
진행하기로 사용자가 확인함). **명시적으로 "디자인 부분만"이라고 범위를
한정했기 때문에, 백엔드/DB 변경이 필요한 것은 전부 제외했다**:

- **익명 게시글 "댓글"→"답변" 명칭**: `post/detail.html`의 페이지 루트 div에
  `th:data-post-category`를 추가하고, 템플릿에서 `post.category == 'ANONYMOUS'`
  조건으로 헤딩/placeholder/로그인 안내 문구를 분기, `post-detail.js`는 이
  data attribute를 읽어 `LABEL` 변수를 만들어 모든 JS 생성 문자열(빈 상태,
  alert, confirm 등)에 재사용한다. **대댓글(1depth 답글) 자체는 구현하지
  않았다** — PostComment에 parentComment 컬럼을 추가하는 백엔드 작업이라
  "디자인만" 범위 밖. 다음에 이어서 할 때는 8번 항목 4번 후보(대댓글)부터
  시작하면 됨.
- **캘린더 오늘/선택 날짜 강조 애니메이션**: `calendar.css`의
  `.fc-day-today`/`.fc-day-selected` 셀에 `day-highlight-pop`(등장 시 살짝
  튀어오르는 스케일 애니메이션) + `day-selected-ring-pulse`(선택 시 브랜드
  링이 바깥으로 한 번 번지는 효과) 키프레임 추가, `prefers-reduced-motion`
  대응 포함. **"기간별 일정 밑줄 표시"는 위 8번 항목 참고 — 데이터 모델이
  없어서 스킵**.
- **수정/삭제 버튼 브랜드 톤 재정의**: `theme.css`에 `--danger`/`--danger-dark`/
  `--danger-border`/`--danger-soft` 토큰을 신설(순수 빨강 대신 브랜드
  인디고/스카이와 어울리는 로즈 톤)하고, `.btn-outline-danger` 유틸리티
  클래스를 `.btn-outline-brand`와 동일한 형태(hover 시 위로 살짝 들리는
  transform + box-shadow)로 추가했다. `post.css`의 `.post-delete-btn`/
  `.post-comment-*-btn`, `calendar.css`의 `.comment-*-btn`이 전부 이
  토큰을 재사용하도록 교체됨 — `.post-delete-btn`은 게시글/관리자(강제삭제)/
  계정 관리(탈퇴 처리)/한마디 관리(삭제) 4곳에서 재사용되는 클래스라 색만
  바꿔도 전부 반영된다.
- **상대 시간 표기(timeago)**: 신규 공용 유틸 `resources/static/js/timeago.js`
  (`WebSchoolTimeago.format/apply/applyAll`) 추가. **설계 트레이드오프**:
  `PostService`/`PostCommentService`/`ScheduleCommentService` 등 백엔드가
  날짜를 이미 `"MM.dd HH:mm"`(연도 없음) 문자열로 포맷해서 내려주기 때문에,
  프론트에서 "현재 연도"를 가정해서 복원하고(복원한 시각이 미래로 계산되면
  작년으로 보정) 그 기준으로 "n분 전/n시간 전/n일 전"을 계산한다 — 완전히
  정확한 절대 시각을 알 방법이 없으므로 **"정확한 시각 툴팁"은 원본
  `"MM.dd HH:mm"` 문자열을 `title` 속성으로 보여주는 수준으로 타협했다**
  (연도까지 포함한 완전한 타임스탬프가 필요하면 DTO에 raw ISO 필드를
  추가하는 백엔드 작업이 필요 — 디자인 범위 밖이라 하지 않음). 적용 위치:
  `post/list.html`(`.post-list-date`), `post/detail.html`(`#postCreatedAt`),
  `post-detail.js`/`calendar.js`가 렌더링하는 댓글/한마디 각각의 시간
  (`.post-comment-time-value`/`.comment-time-value`).
- **네비바 메뉴명 간소화**: `fragments/navbar.html`의 "학사 & 급식 캘린더"를
  "캘린더"로 줄이고 `title` 속성에 원래 문구를 남겨 hover 시 확인 가능하게
  했다. 다른 메뉴명(홈/커뮤니티/관리자)은 이미 짧아서 그대로 둠.
- **스크롤 등장 애니메이션**: 신규 공용 유틸 `resources/static/js/scroll-reveal.js`
  (IntersectionObserver로 `.reveal-on-scroll` 요소에 `.is-revealed` 부여) +
  `theme.css`에 `.reveal-on-scroll`/`.is-revealed` 트랜지션 규칙(모든 페이지
  공용, `prefers-reduced-motion` 대응 포함) 추가. `post/list.html`의 각
  게시물 항목, `index.html`의 섹션 제목·기능 허브 카드에 적용.
- **index 비주얼 리프레시**: 그동안 비어있던 `.hero-visual`(우측 빈 div)에
  시간표를 흉내낸 목업 카드(`.hero-visual-card`, 브라우저 창 느낌의 헤더 +
  교시별 바 차트) + 떠다니는 배지 2개(`.hero-visual-float`, "오늘의 한마디"/
  "NEIS 실시간 연동")를 CSS만으로(이미지 에셋 없이) 채우고 은은한 float
  애니메이션을 줬다. "index.html/css/js 전면 개편"이라는 원래 표현보다는
  작은 리프레시에 가깝다 — 레이아웃 구조 자체를 갈아엎는 수준을 원하면
  범위를 다시 논의할 것.

**검증 방법**: `./gradlew processResources -q`로 이미 떠 있던 devtools
프로세스에 정적 리소스만 반영(자바 코드 변경이 없어서 컴파일은 불필요).
Claude_Browser로 로그인 → 브라우저 클릭이 간헐적으로 씹혀서(레퍼런스 기반
클릭이 실제 폼 필드에 안 들어가는 경우가 있었음) 결국 `javascript_tool`로
폼 요소를 직접 찾아 값 설정 + `form.submit()`/`dispatchEvent(submit)`
방식으로 우회했다 — 앞으로 이 환경에서 로그인/폼 제출 자동화할 때 클릭이
안 먹히면 이 방법부터 시도할 것. 익명 게시글을 하나 만들어 "답변" 명칭
전체 흐름(헤딩/placeholder/빈 상태/실제 답변 작성 후 렌더링)과 상대 시간
표기(초 단위로 "방금 전" 노출 확인)를 실제로 확인한 뒤 테스트 게시글은
삭제(소프트 딜리트)해뒀다. 스크롤 애니메이션은 `getComputedStyle`로 초기
`opacity: 0` 상태와 `IntersectionObserver.observe()` 호출(각 요소에
`transitionDelay` 인라인 스타일이 붙는지)까지만 확인했다 — **이 환경에서
`window.scrollTo`/`scrollTop` 조작이 전혀 먹히지 않아(스크린샷 타임아웃과
비슷한 자동화 한계로 보임) 실제 교차 발생 후 `is-revealed`가 붙는 것까지는
확인 못 했다.** IntersectionObserver 자체는 표준적인 패턴이라 코드 리뷰
수준으로는 문제없다고 판단했지만, 다음 세션에서 실제 브라우저로 스크롤해서
눈으로 한 번 확인해볼 것.

**2026-08-05(3차) 사용자 지시로 보류(⏸️)된 항목들**: 사용자가 "UI/UX처럼
기능적 문제가 아닌 건 CLAUDE.md에 적어두고 넘겨"라고 명시적으로 요청함.
위 7·8·9번(캘린더 시각 효과, 버튼/애니메이션/상대시간 표기, index 개편)과
아래 신고 알림 UI 항목이 여기 해당하며, **기능적으로 문제가 있는 게
아니라 단순 스킵된 것**이니 나중에 사용자가 다시 요청하면 그때 진행하면
된다:
- **신고 시 alert() 대신 작은 팝업으로 교체**: `post-detail.js`/
  `post-form.js` 등에서 신고 성공/실패를 지금은 브라우저 기본 `alert()`로
  띄우는데, 사용자는 캘린더 일정 팝업 같은 작은 커스텀 팝업/토스트로
  바꾸길 원함. 기능은 그대로(신고 자체는 정상 동작), 순수 UI 표현만
  다르므로 보류.

**다음 세션에서 이어서 할 때**: 4번(대댓글 + 익명 답변 명칭/권한 + 관리자
익명 작성자 조회)부터 시작하면 된다. 3차 라운드(아래 섹션)까지 전부
완료됐다. 설계가 애매한 부분은 진행 전에 사용자에게 먼저 확인할 것 —
지금까지 매 라운드마다 이 패턴으로 진행했다(예: 1단계 관리자 강제삭제
소프트/하드 선택, 3차 라운드의 탈퇴 계정 닉네임 표시 방식, 포스트 URL
수정 방향).

### 2026-08-06(2차) 라운드 — index 페이지 전면 재작성 (✅ 완료)

바로 위 "2026-08-06 라운드"에서 index에 비주얼 리프레시(빈 hero-visual
채우기 + 스크롤 애니메이션 적용)만 살짝 했었는데, 같은 날 사용자가
"index.html 부분 삭제하고 처음부터 다시만들어줘 지금이랑 비슷하게 말고
에니메이션 기능 등 여러 웹 메인페이지를 참고해서"라고 다시 요청해서
`index.html`/`index.css`/`index.js` **세 파일을 전부 삭제하고 처음부터
다시 썼다**(기존 히어로+2슬라이드 캐러셀+기능허브 그리드 구조를 완전히
버림). 다른 페이지(post/school/admin 등)는 전혀 건드리지 않았고,
`theme.css`의 브랜드 변수·`.reveal-on-scroll` 유틸리티·navbar 프래그먼트는
그대로 재사용함 — Bootstrap CDN 의존성은 이번에 제거함(사이트 다른 페이지들
전부 Bootstrap 없이 자체 CSS만 쓰고 있어서 index만 예외였던 걸 맞춤).

**새 구조 (위→아래)**:
1. **히어로**: `--nav-bg` 배경 위에 브랜드 컬러 블롭 3개가 각자 다른
   주기로 천천히 떠다니는 배경 애니메이션(`@keyframes blob-drift`) + 은은한
   격자 패턴 오버레이. 카피는 `.hero-pop` 클래스로 등장 시 순차 페이드인
   (`--pop-delay` 인라인 변수로 딜레이만 다르게). 오른쪽엔 시간표를 흉내낸
   목업 카드(`#heroMockup`)가 있고, `index.js`가 `mousemove`로 마우스
   위치에 따라 `rotateX/rotateY` 3D 틸트를 실시간으로 적용한다(`hover:
   hover` 미디어 쿼리로 터치 기기에서는 비활성 — 리스너 자체를 안 붙임).
   맨 아래엔 통통 튀는 스크롤 유도 화살표(`.hero-scroll-cue`).
2. **마퀴**: 기능 키워드 6개를 CSS `@keyframes marquee-scroll`로 무한
   스크롤(목록을 한 번 복제해서 이어붙이는 방식), `:hover` 시
   `animation-play-state: paused`로 정지.
3. **벤토 기능 그리드**(`#features`): 기존에 따로 있던 "지금 확인해보세요"
   캐러셀 + "기능 허브" 그리드 두 섹션을 하나로 합쳤다. `grid-auto-flow:
   dense`로 시간표·학사일정 카드만 2x2로 크게(`.bento-card-lg`), 나머지
   4개(급식/한마디/커뮤니티/마이페이지)는 그 옆 2x2 공간에 1칸씩 자동으로
   채워지고, 랭킹/티어(준비중) 카드가 마지막에 옴. 카드 하나하나에
   `.reveal-on-scroll` + `style="--reveal-delay:N"`으로 순서대로 나타나는
   스태거 효과를 줬다 — 이걸 위해 공용 유틸 `scroll-reveal.js`를 살짝
   고쳤다(아래 "변경한 공용 파일" 참고).
4. **3단계 시작하기**(신규 섹션, 이전엔 없었음): "학교 검색 → 회원가입 →
   캘린더/커뮤니티 이용" 3단계를 점선으로 이어진 카드 3개로 표현. 모바일
   (`max-width: 768px`)에서는 점선을 숨기고 세로 1열로 쌓음.
5. **CTA 밴드**(신규): 로그인 여부에 따라 문구/버튼이 갈리는 섹션
   (`th:if="${loginUser == null}"` / `!= null`) — 비로그인은 "지금 바로
   시작해보세요"+회원가입/로그인, 로그인 상태는 닉네임을 넣은 인사말
   +캘린더/커뮤니티 바로가기. `GlobalModelAdvice`가 모든 화면에 주입하는
   `loginUser`를 그대로 씀(index.html에 별도 컨트롤러 변경 없음).
6. **푸터**: 기존 한 줄짜리 카피라이트에서 브랜드 소개 + 실제 존재하는
   내부 링크(캘린더/커뮤니티/마이페이지/회원가입) 컬럼을 추가한 구조로
   확장. 가짜 소셜 링크나 없는 페이지 링크는 넣지 않음.
7. **맨 위로 버튼**(신규, index 전용): `index.js`가 `scroll` 이벤트로
   480px 이상 스크롤하면 `.is-visible` 클래스를 붙여 나타나게 하고,
   클릭 시 `window.scrollTo({behavior:'smooth'})`로 부드럽게 올라간다.

**변경한 공용 파일**: `scroll-reveal.js`가 원래는 문서 전체의
`.reveal-on-scroll` 요소 순서(`j % 6`)로만 스태거 딜레이를 계산했는데,
이번 벤토 그리드처럼 섹션마다 독립적으로 0부터 스태거를 주고 싶은 경우를
위해 **요소에 인라인 `--reveal-delay` CSS 커스텀 프로퍼티가 있으면 그
값을 우선 사용**하도록 고쳤다(`el.style.getPropertyValue('--reveal-delay')`,
없으면 기존 `j % 6` 방식으로 폴백). 다른 페이지(post/list.html 등)는
`--reveal-delay`를 안 쓰므로 동작 그대로 유지됨 — 이 유틸을 쓰는 곳이
늘어나면 이 우선순위 규칙을 기억할 것.

**검증**: `./gradlew processResources -q`로 정적 리소스만 반영 후
Claude_Browser로 확인. `get_page_text`로 전체 섹션 텍스트가 의도대로
나오는지, `javascript_tool`로 (1) 블롭/마퀴 애니메이션이 실제로
`animationName`을 갖는지, (2) `#heroMockup`에 `mousemove` 디스패치 시
`rotateX/rotateY` 인라인 transform이 실제로 바뀌는지, (3) 벤토 큰 카드가
`grid-column: span 2 / grid-row: span 2`로 렌더링되는지, (4) 로그인 후
CTA 밴드 문구가 닉네임 버전으로 바뀌는지, (5) `resize_window`로 모바일
(375px) 뷰포트에서 히어로/벤토/스텝 그리드가 전부 1열로 접히고 가로
스크롤(`body.scrollWidth`)이 안 생기는지까지 확인했다. **스크롤 등장
애니메이션의 실제 교차(intersection) 트리거는 여전히 확인 못 했다** —
이전 라운드 메모에 적힌 것과 같은 이유(이 환경에서 `window.scrollTo`가
안 먹힘)로, 이번에도 코드 검증(초기 `opacity:0`, `observer.observe()`
호출 여부)까지만 했다.

### 2026-08-11 라운드 — index 검은 얼룩 버그 + 캘린더 토요일/중복일정 표시 (✅ 완료)

**중요 정정**: 이 문서(위 두 "2026-08-06" 라운드)는 캘린더가 FullCalendar
라이브러리를 쓴다고 적어놨었는데, **실제로는 그 시점 이후 어느 세션에서
FullCalendar를 완전히 걷어내고 커스텀 그리드(`school/calendar.html`의
`.cal-grid-body`/`.cal-day`, `school/static/js/calendar.js`의
`buildCalendarDays()`/`renderGrid()`)로 갈아엎은 상태였다** — `<head>`에
FullCalendar CDN 스크립트 자체가 없고 `.fc-*` 클래스도 전혀 안 쓰인다.
언제/누가 이 전환을 했는지는 이 세션에서 확인 못 했음(컨텍스트 압축으로
과거 라운드 일부가 요약되면서 누락된 것으로 추정). **다행히 `day-highlight-pop`
애니메이션(오늘/선택 강조)은 `.cal-day-today`/`.cal-day-selected`로 이미
잘 옮겨져 있어서(`calendar.css` 주석: "등장 애니메이션은 fc- 시절 이름을
그대로 재사용") 실제로는 깨지지 않은 상태였다 — 이 문서만 낡은 설명을
달고 있었던 것. **앞으로 캘린더를 손댈 때 이 문서의 FullCalendar 관련
서술은 전부 무시하고 실제 코드(`.cal-*` 클래스 체계)를 기준으로 볼 것.**

- **index 벤토 카드 hover 시 검은 얼�룩 (사용자 스크린샷 제보) — ✅ 완료**:
  `index.css`/`calendar.css`의 `radial-gradient(rgba(...), transparent 70%)`
  형태 그라데이션들이 원인이었다. CSS에서 `transparent` 키워드는 "직전
  색의 알파 0 버전"이 아니라 **문자 그대로 `rgba(0,0,0,0)`(검정 투명)으로
  보간**되기 때문에, 색 있는 정지점에서 `transparent`로 끝나는 그라데이션은
  중간에 옅은 검은 얼룩이 낀다 — 잘 알려진 CSS 함정. `index.css`(벤토 글로우,
  CTA 블롭, 스텝 점선)와 `calendar.css`(페이지 배경 블롭 2개) 전체를
  `rgba(같은색, 0)`으로 명시하도록 고쳤다. **벤토 카드는 한 번 더
  손봤다** — 그라데이션 색만 고쳐도 브라우저별 렌더링 경계 아티팩트가
  남을 가능성이 있어서, 사용자가 "그래도 안 고쳐졌다"고 재차 지적한 뒤
  `.bento-card-glow`(카드 hover 시 뜨는 방사형 그라데이션 오버레이) 자체를
  **완전히 제거**하고 대신 `.bento-card:hover`에 브랜드색 `box-shadow`만
  주는 방식으로 바꿨다 — 그라데이션을 아예 안 그리니 이런 종류의 아티팩트가
  구조적으로 재발할 수 없음.
- **index 히어로 요소 교체(사용자가 스크린샷으로 지목) — ✅ 완료**: "NEIS
  Open API 실시간 연동" 배지(`.hero-eyebrow`), 시간표 목업 카드
  (`.hero-mockup`), 떠다니는 칩 2개(`.hero-chip`), 스크롤 유도 마우스
  아이콘(`.hero-scroll-cue`), 마퀴 띠(`.marquee-band`)를 전부 삭제하고
  **통계 카드 3개짜리 패널**(`.hero-stat-panel` — "동명학교도 정확히"/
  "NEIS 실시간 연동"/"같은 학교끼리 소통")로 교체했다. 마우스 패럴랙스
  틸트(`index.js`)는 새 패널(`#heroStatPanel`)에 그대로 유지. 가짜
  사용자 수 등 검증 안 되는 숫자는 넣지 않고 사실 기반 문구만 씀.
- **캘린더 그리드 - 토요일에 진짜 일정이 있으면 가려지던 문제 — ✅ 완료
  (한 번 정정됨)**: 처음엔 사용자 요청("토요일도 일정에 포함되어있는데
  색칠이 안되어있어")을 "토요휴업일도 다른 일정처럼 매주 보여달라"로
  오해해서 `SATURDAY_CLOSURE_NAME`('토요휴업일') 예외 처리 자체를
  통째로 지웠었다 — 그랬더니 매주 토요일마다 "토요휴업일" 배지가 뜨게
  됐는데, 사용자가 "그니깐 토요휴업일은 표시하지 않는다고"라고 정정함.
  **진짜 의도는 이거였다**: 토요휴업일이라는 이름 자체는 계속 숨기되,
  바로 아래 "하루에 여러 일정이 겹치면 전부 표시" 버그 때문에 그 토요일에
  다른 진짜 일정(예: "미국 방문 체험학습")이 같이 있어도 통째로 안 보이던
  게 문제였다. 그래서 `SATURDAY_CLOSURE_NAME` 예외 처리를 다시 살리되,
  이번엔 **날짜 전체를 건너뛰는 게 아니라 이름 배열에서 "토요휴업일"
  하나만 걸러내는 방식**(`applyMonthEventChips()` 내부 `visibleNames()`
  헬퍼)으로 바꿨다 — 토요휴업일만 있는 토요일은 여전히 배지가 안 뜨고,
  토요휴업일 + 다른 진짜 일정이 겹친 토요일은 그 진짜 일정만 뜬다.
  **앞으로 "토요일 표시" 관련 요청이 다시 오면 이 구분(휴업일 자체를
  보여달라는 건지, 휴업일 때문에 가려지는 다른 일정을 보여달라는 건지)을
  먼저 명확히 확인할 것** — 문장만으로는 헷갈리기 쉬움(이번에 실제로
  헷갈렸음).
- **캘린더 그리드 - 하루에 여러 학사일정이 겹치면 전부 표시 — ✅ 완료**:
  버그가 두 군데 있었다.
  1. **월 그리드(작은 배지)**: `calendar.js`의 `loadMonthEvents()`가
     `map[e.date] = e.eventName`로 **덮어쓰기** 방식이었다 — 백엔드
     (`NeisApiService.fetchEventsInRange`)는 이미 같은 날짜에 대해 여러
     row를 그대로 다 내려주고 있었는데, 프론트에서 마지막 하나만 남기고
     나머지를 버리고 있었던 것. `map[date]`를 문자열이 아니라 **배열**로
     바꾸고(`applyMonthEventChips()`도 함께 배열 기반으로 재작성 —
     이름별로 "이전/다음 칸에 같은 이름이 있는지"를 개별 계산해서 여러
     일정이 겹쳐도 각각 독립적으로 옆 칸까지 이어지는 띠로 그려짐), 하루에
     일정이 여러 개면 `.cal-day`가 `flex-direction: column; gap: 6px`라
     띠가 자동으로 세로로 쌓인다(레이아웃 CSS 변경 불필요, 기존 구조가
     이미 여러 줄을 지원했음).
  2. **날짜 클릭 시 상세 패널의 "학사일정: ..." 배지**:
     `NeisApiService.fetchEventFromNeis()`(단일 날짜 조회, 상세 패널 전용)가
     구버전 문자열 자르기 방식(`extractValue(json, "EVENT_NM")`)이라 JSON
     안에서 `EVENT_NM`이 **처음 등장하는 것 하나만** 가져오고 있었다 —
     하루에 일정이 여러 개면 나머지는 그냥 버려졌음. Jackson
     (`ObjectMapper`/`JsonNode`, `fetchEventsInRange`와 동일한 패턴)으로
     교체해서 그 날짜의 모든 row를 순회해 `EVENT_NM`을 중복 제거하며 모은
     뒤 `" · "`로 이어붙여 반환하도록 고쳤다(`SchoolCalendarDto.eventName`은
     여전히 단일 `String`이라 DTO/프론트 변경은 필요 없었음 — 조인된
     문자열을 그대로 표시).
  **실제 NEIS 데이터로 검증(정정 반영 후 최종 상태 기준)**: 로그인 후
  "서울고등학교" 검색·선택 → 2026년 8월 그리드에서 토요휴업일만 있는
  순수 토요일(08-08/22, 09-05)은 배지가 안 뜨고, 토요휴업일 + 실제
  행사가 겹친 토요일(08-01 "미국 방문 체험학습", 08-29 "논박토론챌린지
  예선")은 그 실제 행사만 배지로 뜨는 것을 확인. 08-21(2개)/08-26(2개)/
  08-28(3개)/08-31(2개)/09-02(2개)처럼 하루에 여러 일정이 겹치는 칸에
  배지가 전부 쌓여서 나오는 것도 확인. 08-28을 클릭해서 상세 패널의
  "학사일정:" 배지가 "학급자치 · 명사 초청 특강 · 논박토론챌린지 예선"으로
  3개 다 조인되어 나오는 것도 확인. `./gradlew compileJava`로 Java 변경
  컴파일 에러 없음 확인.
- **캘린더 페이지를 열면 자동으로 오늘 날짜 선택+상세 패널 오픈 — ✅ 완료**:
  예전엔 `selectedDateStr`가 오늘로 초기화돼서 그리드에서 오늘 칸이
  `.cal-day-selected`로 강조는 됐지만, 상세 패널(`#dayDetailPanel`)은
  사용자가 직접 날짜를 클릭해야만 열렸다. `calendar.js` 초기화 시퀀스
  마지막에 `handleDayClick(selectedDateStr)`를 한 번 호출해서, 페이지를
  열자마자 오늘 날짜를 클릭한 것과 동일하게 패널이 자동으로 열리고
  시간표/급식/학사일정(방학이면 특별 안내 카드)이 곧바로 보이도록 함.

### 2026-08-05 3차 라운드 — 추가 요청 (✅ 전부 완료, UI/UX는 위에서 보류)

사용자가 2차 라운드 도중 새 메시지로 우선순위를 다시 정리해서 요청함
("UI/UX 빼고 전부 수정해"). 완료된 작업:

- **"게시물 삭제 시 댓글 미보존" 버그 제보 확인 — ✅ 조사 완료, 재현 안 됨**.
  curl과 실제 브라우저 클릭(본인 삭제/관리자 강제삭제 양쪽 경로) 모두로
  재현을 시도했으나 재현되지 않음 — 댓글은 DB에도, 관리자 상세 화면에도
  정상 보존되고 있었다(2번 항목 "게시글/댓글 소프트 딜리트"에서 이미 해결된
  상태). 사용자가 2차 라운드 배포 전 캐시된 상태나 이전 버전을 보고 있었을
  가능성이 있음 — **다시 재현되면 정확한 재현 절차(어떤 계정으로 어떤
  버튼을 눌렀는지)를 알려달라고 요청할 것.**
- **포스트 URL 일관성 — ✅ 완료**: `/posts/{id}`(일반)와
  `/admin/posts/{id}`(관리자)가 같은 게시물을 가리키는데 서로 넘나들 방법이
  없던 문제 — `post/detail.html`에 관리자 전용 "관리자 페이지에서 보기"
  링크, `admin/post-detail.html`에 "게시글 페이지에서 보기" 링크를 추가해서
  두 화면을 오갈 수 있게 함(URL 구조 자체는 원래도 일관돼 있었어서, 실질적
  개선은 "두 화면을 오갈 방법이 없었다"는 부분이었음 — 사용자에게 확인 후
  이 방향으로 진행).
- **계정 소프트 삭제(6-5/6-7) — ✅ 완료**: `User.deleted`/`deletedAt` 추가,
  탈퇴 계정은 로그인 차단(`CustomUserDetailsService`), 마이페이지에 탈퇴
  버튼(비밀번호 재확인) 추가. 탈퇴 계정 조회는 새로 만든 `/admin/users`
  전체 계정 관리 페이지에 통합(아래 항목 참고). 탈퇴 사용자의 작성 글/댓글은
  삭제하지 않고 그대로 두되 닉네임을 "탈퇴한 사용자"로 표시하기로 확정
  (사용자에게 직접 확인함 — 실제 닉네임 유지 옵션도 제시했으나 이쪽을 선택).
- **신고 "문제없음" 판결 기능 — ✅ 완료**: 관리자가 신고된 게시물/댓글을
  검토해서 "문제없음"으로 판결하면, 그 이후 그 콘텐츠가 수정되기 전까지는
  신고를 시도해도 신고자 수가 올라가거나 블라인드되지 않고 "이미 검토되어
  문제없다고 판정된 게시물/댓글입니다" 안내만 나온다. 게시물/댓글이 수정되면
  이 판정은 자동으로 초기화(내용이 바뀌었으니 다시 검토가 필요). 5번 항목
  "신고 '문제없음' 관리자 판결" 참고.
- **관리자 페이지 확장 — ✅ 완료**: `/admin/users`(전체 계정 목록 + 권한
  승격/해제 + 탈퇴 처리/복구, 관리자 자기 자신은 변경 못 하게 방어),
  `/admin/posts?status=all`(전체 게시글 탭 — 기존엔 신고/블라인드된 글만
  보였는데, 평소 문제 없는 글도 미리 보고 관리할 수 있어야 한다는 지적을
  반영). 관리자 페이지 상단에 "게시물 관리"/"계정 관리" 대분류 탭 추가.

**검증 방법 메모**: 이번 라운드는 회원가입 API로 테스트 계정을 여러 개
만들어서(cmtrep1~3 등) 서로 다른 사용자로 신고/탈퇴 시나리오를 재현했다.
회원가입 폼 필드(schoolKind 등)에 실제 검색된 학교가 아니어도 "학교 이름/
코드/학년/반이 비어있지만 않으면" 통과한다(`UserService.register()`가
값 존재 여부만 검사하고 NEIS 실제 학교인지는 검증 안 함) — 테스트 계정
만들 때 이 점을 활용하면 편하다. 단, 학교종류(schoolKind) 값에 한글을 넣을
땐 위 curl 함정(아래 7번 항목)에 걸리니 영문으로 넣을 것.

### 2026-08-05 4차 라운드 — 오늘의 한마디 신고/블라인드 + 관리자 관리 (✅ 완료)

사용자 요청: "오늘의 한마디 기능은 댓글과 같이 신고가 가능하도록 만들고
댓글과 오늘의 한마디 공통으로 숨겨질 수 있게 만들고 일반 게시글처럼
관리자 페이지에서 수정(관리)할 수 있게 만들어줘". 5번 항목 "오늘의 한마디
신고 → 자동 블라인드"에 구현 상세 전부 기록. 요약:

- `ScheduleComment`에 `PostComment`와 동일한 신고/블라인드/소프트삭제
  필드(`reportCount`/`blind`/`reportCleared`/`deleted`/`deletedAt`) 추가,
  `ScheduleCommentReport` 신규(`CommentReport`와 동일 구조).
- `/school/api/comments/{id}/report` API + 캘린더 위젯에 신고 버튼/블라인드
  배지 렌더링(`calendar.js`/`calendar.css`).
- `/admin/schedule-comments`(전체/삭제됨 탭 + 검색 + 인라인 액션) +
  `/admin/reports?type=schedule`(신고 관리 3번째 탭) 신설, 관리자 대시보드
  상단 탭에 "3. 한마디 관리" 추가(순서: 신고관리→게시글관리→**한마디관리**→계정관리).
- **검증**: curl로 신고 3회 자동블라인드 → 신고자별 콘텐츠 노출 차이 →
  문제없음 처리 → 수정 시 리셋 → 관리자 블라인드 토글/강제삭제/복구까지
  전체 흐름을 실제 DB에 대해 확인. 브라우저(Claude_Browser)로 캘린더
  위젯의 수정/삭제/신고 버튼이 작성자 여부에 따라 올바르게 렌더링되는
  것도 확인(스크린샷은 여전히 타임아웃 나서 `read_page`/`get_page_text`로
  대체 — 7번 항목 메모와 동일).
- 이 작업 도중 별개로 발견한 컴파일 에러(`AdminReportController`가
  `AdminPostService.getReportedPosts/getReportedComments`의 keyword 파라미터
  추가 이후 옛 시그니처로 호출하고 있던 문제)도 이번 세션 초반에 먼저 수정함.

**작업 중 발견했지만 이번 라운드 범위 밖이라 미루고 기록만 해둔 이슈들**은
바로 아래 "새로 발견된 기능적 갭" 목록과 그다음 "UI/UX 자잘한 개선 후보"에
정리해뒀다. 사용자가 직접 "너도 보이는 문제 찾아서 CLAUDE.md에 적어달라"고
요청해서, 코드를 훑어보고(Explore 서브에이전트로 `Post`/`PostComment`/
`AdminPostController`/`AdminReportController`/`report-list.html`/
`post-detail.js` 등을 근거 기반으로 재확인) 아래 항목들을 새로 추가함 —
전부 구현은 안 했고 **문서화만** 한 상태이니 다음 세션에서 우선순위를
사용자와 다시 정하고 시작할 것.

#### 새로 발견된 기능적 갭 (다음 작업 후보, 우선순위 미정)

- ~~**[사용자 지적] 댓글 전용 관리 페이지 부재**~~ → **✅ 2026-08-10(4차)에
  해결됨** — `/admin/comments`(신규, `AdminCommentController`) 추가.
  8번 항목 "2026-08-10(4차) 라운드" 참고.
- ~~**[사용자 지적] 신고 관리(`/admin/reports`) 검색 기능 없음**~~ →
  **✅ 2026-08-10(3차)에 해결됨** — `AdminReportController.list()`에
  `keyword`/`page`/`size` 파라미터를 추가하고 세 탭(게시물/댓글/한마디)
  각각에 검색 폼 + 페이지네이션을 붙였다. 8번 항목 "2026-08-10(3차) 라운드"
  참고.
- ~~**[사용자 지적] "내가 신고한 글/댓글" 확인 기능 없음**~~ → **✅
  2026-08-10(4차)에 해결됨** — `PostDetailDto`/`PostCommentDto`/
  `ScheduleCommentDto`에 `reportedByMe` 필드 추가, 신고 버튼이 처음부터
  "신고완료"로 비활성화됨. **마이페이지 "내가 신고한 목록" 페이지는
  여전히 없음**(신고 이력 조회 자체는 범위 밖) — 다음 후보로 남겨둠.
  8번 항목 "2026-08-10(4차) 라운드" 참고.
- ~~**관리자 목록 전체가 페이지네이션 없음**~~ → **✅ 2026-08-10(3차)에
  해결됨(단, DB 쿼리 페이지네이션이 아니라 메모리 슬라이싱)** —
  `AdminPostService`/`AdminScheduleCommentService`/`AdminUserService`의
  `getXxx(keyword)` 메서드들은 여전히 `List<...>` 전체 조회 + 메모리 필터링
  그대로 두고, 그 필터링된 List를 컨트롤러 단에서 `PageUtils.paginate()`로
  잘라 `Page`로 감싸는 방식으로 커뮤니티와 동일한 페이지네이션 UI를
  붙였다(4번 항목 `global.util.PageUtils` 참고). **원래 지적한 "게시물/댓글
  수가 늘어나면 로딩이 느려진다"는 스케일 문제 자체는 근본적으로 해결된
  게 아니다** — 여전히 매 요청마다 전체 목록을 DB에서 읽고 메모리에서
  자르기 때문에, 데이터가 정말 많아지면 리포지토리 단 `Pageable` 쿼리로
  바꿔야 한다. 8번 항목 "2026-08-10(3차) 라운드" 참고.
- ~~**게시글 목록에 "신고된 댓글 있음" 신호가 없음**~~ → **✅
  2026-08-10(4차)에 해결됨** — `AdminPostSummaryDto.reportedCommentCount`
  추가, "전체 게시글"/"신고 관리" 게시물 탭 제목 옆에 "댓글 신고 N" 배지.
  8번 항목 "2026-08-10(4차) 라운드" 참고.
- ~~**댓글 신고 목록에서 해당 댓글로 바로 이동/포커스가 안 됨**~~ → **✅
  2026-08-10(4차)에 해결됨** — 댓글 링크가 `#comment-{id}` 앵커를 들고
  가고, `admin/post-detail.html`이 그 앵커로 들어오면 해당 댓글을 잠깐
  강조 표시(`.admin-report-item-highlight`)한다. 8번 항목
  "2026-08-10(4차) 라운드" 참고.
- **`ScheduleComment`가 `PostComment`와 완전히 동일하지 않은 부분**: 이번
  라운드는 신고/블라인드/삭제/관리자 관리만 맞췄고, ① 탈퇴 사용자 닉네임
  "탈퇴한 사용자" 치환(`PostService.displayNickname()`/`PostCommentService.
  toDto()`엔 있는데 `ScheduleCommentService.toDto()`엔 없음), ②
  `BannedWordFilter` 금지어 검사(`PostService`/`PostCommentService`는
  호출하는데 `ScheduleCommentService.validateContent()`는 길이만 검사)
  두 가지는 아직 안 맞춰져 있다. 일부러 뺀 게 아니라 이번 요청 범위(신고/
  블라인드/관리자 관리) 밖이라 손대지 않은 것 — 나중에 통일할지 사용자에게
  확인할 것.

#### 이외 UI/UX 자잘한 개선 후보 (기능 문제는 아님)

기존 7·8·9번 항목(캘린더 시각효과/버튼 애니메이션/index 개편, 2026-08-05(3차)
사용자가 스킵 지시)과 신고 alert() 토스트 전환 항목에 이어서, 이번 라운드
코드를 훑으며 추가로 눈에 띈 것들:

- **관리자 대시보드 상단 대분류 탭이 4개로 늘어남**: "1.신고관리 2.게시글관리
  3.한마디관리 4.계정관리"(`admin/fragments/nav.html`) — 좁은 화면에서
  줄바꿈되거나 잘리는지 실제로 확인 안 해봤다. `post-category-tabs` 클래스를
  그대로 재사용해서 큰 틀은 반응형일 가능성이 높지만, 탭 개수가 늘어난 뒤
  모바일 뷰포트로 한 번 확인해볼 것.
- **`admin/schedule-comment-list.html`의 "학교/학년/반" 컬럼이 김**: `학교명 +
  학년 + 반` 문자열을 그대로 이어붙여서(`schedule-comment-list.html`) 학교명이
  길면 테이블이 옆으로 많이 늘어날 수 있음 — `admin-table-wrap`이
  `overflow-x: auto`라 깨지지는 않지만 가로 스크롤이 자주 생길 수 있다.
- **신고/삭제/복구 등 액션 확인이 전부 네이티브 `confirm()`**: 게시글/댓글
  뿐 아니라 이번에 추가한 한마디 관리 액션들(`admin/schedule-comment-list.html`)
  도 전부 `onsubmit="return confirm(...)"` 패턴 — 기존에 보류된 "alert() →
  커스텀 토스트" 항목과 같은 맥락으로, 나중에 통일된 확인 모달을 만들 때
  같이 처리하면 됨.
- **신고 관리 3탭(게시물/댓글/한마디)이 전부 다른 필드 이름의 비슷한 테이블**:
  `post`/`comment`/`scheduleComment` 각각 모델 속성 이름이 달라서
  (`report-list.html`) 마크업이 3벌 거의 그대로 반복돼 있다 — 지금 당장
  문제는 아니지만, 나중에 4번째 신고 대상이 생기면 Thymeleaf fragment로
  묶는 리팩터링을 고려할 것.

- **투표 기능**: 기획서에 언급만 있고 설계는 없음 — 요구사항부터 다시
  정리 필요.
- **포인트/티어 시스템**: `User`에 `point`/`tier` 컬럼 추가, 질의응답
  카테고리 답변 채택 시 포인트 지급 로직, 일일 획득 한도(어뷰징 방지).
  카테고리가 이미 QNA로 나뉘어 있으니 "채택된 답변" 개념(PostComment에
  isAccepted 플래그 등)부터 설계하면 됨.
- **게시글 조회수 어뷰징 방지 강화**: 지금은 `HttpSession` 기반이라
  세션 만료/새 브라우저로 우회 가능 — 더 강하게 막으려면 IP+쿠키
  조합이나 DB에 조회 이력을 남기는 방식으로 전환 검토.
- Timetable/Meal에 `updatedAt` 컬럼 추가 + 24시간 TTL 만료 재조회 로직.
- 방학 기간 D-Day 표시, 방학 중 시간표 요청 차단.
- `CalendarEvent`/`Event`/`CalendarEventRepository` 미사용 정리(삭제 또는
  학사일정 캐싱 용도로 실제 사용하도록 전환).
- 흩어진 프로젝트 사본(0번 항목) 정리 여부 확인, 이 폴더의 git 커밋 진행.

### 2026-08-10 라운드 — 테스트 계정/게시글 시더 추가 + 레거시 `users.name` 컬럼 버그 수정 (✅ 완료)

사용자 요청: "테스트 자바 파일에 실행시 아디비 비번이 같은 user1 부터
user5계정까지 만들게 해주고 게시물도 카테고리마다 3개씩 만들어줘". 새 기능
개발이 아니라 로컬 개발/테스트용 데이터 시더를 만드는 작업이었다.

- **신규 파일**: `src/test/java/com/webschool/webschool/TestDataSeeder.java`
  (`@SpringBootTest` + `@Test void seedTestData()`). `UserService.register()`/
  `PostService.createPost()`를 그대로 호출해서 실제 서비스 검증 로직(아이디
  중복 체크, 비밀번호 확인, 학교/학년/반 필수값, 제목/내용 길이·금지어
  검사 등)을 그대로 통과하며 데이터를 만든다 — SQL을 직접 INSERT하지 않고
  프로덕션 코드 경로를 그대로 재사용하는 방식을 택함.
  - `user1`~`user5`(아이디=비밀번호, 닉네임도 동일)를 `RegisterDto`로
    가입시킨다. 학교 관련 필드는 회원가입 검증(존재 여부만 체크, 실제 NEIS
    학교인지는 검증 안 함 — 2026-08-05(3차) 라운드 메모 참고)을 통과시키기
    위한 더미 값("테스트중학교" 등)을 채운다.
  - `Post.Category`의 3개 값(FREE/ANONYMOUS/QNA) 전체를 순회하며 각각
    `user1` 작성으로 테스트 게시글 3개씩(총 9개, 제목 "{카테고리 라벨}
    테스트 게시글 N")을 만든다.
  - **멱등성**: 계정은 `userRepository.existsByUsername()`으로, 게시글은
    `postRepository.findAllByDeletedFalseOrderByCreatedAtDesc()`를 스트림
    검색해 같은 제목이 이미 있는지로 각각 존재 여부를 먼저 체크하고
    건너뛴다 — 실행할 때마다 `UserService.register()`가 던지는 "이미
    존재하는 아이디입니다" 예외로 테스트가 실패하지 않도록, 여러 번
    실행해도 안전하게 설계함(회원가입 웹 폼과 달리 이 시더는 재실행이
    당연히 예상되는 용도라서).
  - 실행 방법: `./gradlew test --tests "com.webschool.webschool.
    TestDataSeeder"` (일반 `./gradlew test`를 실행해도 다른 테스트와 함께
    같이 돈다 — `WebschoolApplicationTests`처럼 항상 실행되는 스위트의
    일부이니, CI/일반 빌드에서 매번 이 계정들이 만들어진다는 점을 인지할
    것. 운영 배포 파이프라인이 생기면 이 클래스를 별도 소스셋이나
    `@Disabled`로 분리하는 걸 고려해야 함).
- **버그 발견/수정**: 위 시더를 처음 실행했을 때 `user1` 가입 단계에서
  `DataIntegrityViolationException`(`Field 'name' doesn't have a default
  value`)이 터졌다 — 6번 항목 버그#8에 상세 기록. 원인은 `users` 테이블에
  `User` 엔티티엔 없는 레거시 `name` 컬럼이 `NOT NULL`로 남아있던 것이었고,
  **이 시더뿐 아니라 실제 웹 회원가입도 똑같이 막혀 있었을 것으로 보이는
  더 근본적인 문제**였다. `ALTER TABLE users MODIFY COLUMN name VARCHAR(50)
  NULL;`로 데이터 손실 없이 해결한 뒤 시더를 재실행해서 정상 동작을 확인함.
- **검증**: `./gradlew compileTestJava`로 컴파일 확인 → 첫 실행에서 위 버그로
  실패 → DB 스키마 수정 후 재실행해서 `BUILD SUCCESSFUL` 확인 → mysql
  클라이언트로 `SELECT`해서 `users`에 `user1`~`user5` 5개 행, `posts`에
  카테고리별 3개씩(FREE/ANONYMOUS/QNA) 총 9개 행이 실제로 생성된 것을
  직접 확인했다(콘솔에 한글이 mojibake로 보였지만 이건 Windows mysql.exe
  콘솔 코드페이지 문제일 뿐 — JDBC URL에 `characterEncoding=UTF-8`이
  이미 있어 실제 저장된 데이터 자체는 정상이라고 판단함, 3번 항목의
  MySQL 연결 설정 참고).

### 2026-08-10(2차) 라운드 — 관리자 권한 체계(총관리자/부관리자) + 계정 비활성화 + 프로필 확인 (✅ 완료)

사용자 요청 원문 요약: "관리자 페이지에 들어가면 [신고 관리 탭이 기본으로
뜨는 화면]처럼 실행되게 해줘. admin 빼고 다른 관리자는 전부 부관리자로
만들어서 서로의 관리자 권한을 추가/제거 못 하게 하고, 총관리자가 부관리자
계정별로 신고관리/게시글관리/한마디관리 3개 권한만 켜고 끌 수 있게 해줘
(계정 관리는 여기 포함 안 됨) — 이를 위한 별도 관리자 대시보드 페이지 하나를
추천함. 계정 비활성화 기능도 추가하고, 상대방(다른 계정)의 프로필을 확인하는
기능도 추가해줘." 스크린샷으로 첨부된 화면은 "1.신고관리 2.게시글관리
3.한마디관리 4.계정관리" 대분류 탭 + "게시물/댓글/오늘의 한마디" 하위 탭이
있는 기존 `/admin/reports` 화면이었다.

**설계 결정 (진행 전 스스로 정리한 판단, 애매한 지점마다 기존 코드/문서
컨벤션을 근거로 삼음)**:
- "admin 빼고 부관리자"를 username 하드코딩이 아니라 **새 역할
  `User.Role.ROLE_SUPER_ADMIN`**으로 모델링했다 — 하드코딩보다 확장성 있고,
  `Post.Category`/`ScheduleComment` 등 기존 코드가 전부 enum 기반 상태
  모델을 쓰는 것과 일관됨. 앱 안에는 총관리자를 새로 만들거나 바꾸는 UI/API를
  전혀 만들지 않았다(의도적 — "admin 하나로 고정"이라는 요청 그대로).
- "1개의 관리자 대시보드 페이지" 추천을 **기존 "계정 관리"(`/admin/users`,
  전체 유저 목록) 페이지에 권한 토글을 끼워넣는 대신, 별도의 새 페이지**
  (`/admin/users/admins`, "관리자 권한 관리")로 만들었다 — 계정 관리는
  학생 포함 전체 계정이 몇십~몇백 명일 수 있어 그 안에 권한 토글까지 넣으면
  화면이 복잡해지고, "그를 위한" 이라는 표현이 권한 토글 전용 목적의 페이지를
  가리킨다고 판단함. 관리자 계정만 추려서 보여주고(총관리자 행은 읽기 전용
  "모든 권한 보유"), 부관리자 행마다 체크박스 3개가 `onchange="this.submit()"`
  로 즉시 저장된다.
- **HTML `<table>` 안에 행 전체를 감싸는 `<form>`을 넣지 않았다** — `<tr>`는
  `<td>`/`<th>`만 자식으로 허용하는 HTML 콘텐츠 모델이라 `<form>`이 `<tr>`를
  감싸면 브라우저가 파싱 단계에서 위치를 옮겨버릴 위험이 있다(foster
  parenting). 그래서 "관리자 권한 관리" 페이지는 `<table>` 대신 `<div>` 기반
  행 레이아웃(`.admin-permission-row`)으로 만들어 이 문제 자체를 피했다 —
  앞으로 "테이블 한 행 전체를 하나의 폼으로 묶고 싶다"는 요구가 다시 생기면
  테이블 대신 div 레이아웃을 쓰거나, `<form id="...">`를 테이블 밖에 두고
  각 입력에 `form="..."` 속성으로 연결하는 방식을 검토할 것.
- **권한 검사를 2단으로 나눴다**: (1) `SecurityConfig`의
  `hasAnyRole("ADMIN","SUPER_ADMIN")` — "관리자이긴 한지"의 굵은 체
  (2) 신규 `AdminAccessInterceptor`(`global.security` 패키지, `HandlerInterceptor`) —
  `/admin/reports`·`/admin/posts`·`/admin/schedule-comments`·`/admin/users`
  경로별로 부관리자가 실제로 그 메뉴에 대한 권한(`canManageReports` 등)이
  있는지 세밀하게 체크. 기존 4개 관리자 컨트롤러(AdminReportController/
  AdminPostController/AdminScheduleCommentController/AdminUserController)의
  메서드 시그니처를 하나도 안 건드리고 권한 체계를 끼워넣을 수 있어서
  이 방식을 택함(컨트롤러마다 `Authentication` 파라미터를 추가하고 매
  메서드 시작에 체크 코드를 반복하는 대안보다 훨씬 적은 변경으로 끝남).
  권한 없이 접근하면 `AccessDeniedException`을 던지고, `SecurityConfig`에
  새로 추가한 `accessDeniedHandler`가 `/admin/**` 요청이면
  `/admin/access-denied`(새 안내 페이지)로, 그 외엔 `/`로 리다이렉트한다.
- **"계정 비활성화"를 기존 "탈퇴 처리"(`User.deleted`)와 별개의 새 필드
  `User.active`로 만들었다** — 탈퇴는 본인/관리자가 계정을 사실상
  정리하는 것(닉네임이 어디서나 "탈퇴한 사용자"로 치환됨)이고, 비활성화는
  총관리자가 계정과 작성 글은 그대로 둔 채 로그인만 즉시 막았다가 다시
  풀 수 있는 훨씬 가벼운 정지 조치라서 의미가 다르다고 판단했다(둘 다
  로그인 차단 효과는 있어 `CustomUserDetailsService`에서
  `disabled(user.isDeleted() || !user.isActive())`로 OR 결합). "이미
  있는 탈퇴 기능과 뭐가 다르냐"는 질문이 나올 수 있는 지점이니, 다음에
  이 기능을 만질 때는 이 구분을 참고할 것.
- **"상대방의 프로필을 확인하는 기능"은 계정 관리(총관리자) 맥락으로
  한정했다** — 게시글/댓글 상세 화면에서 작성자 닉네임을 클릭해 프로필을
  보는 것까지는 만들지 않았다(요청 문장이 "총관리자가 ~계정별로"라는
  문단 안에 함께 있어서 계정 관리 기능의 연장으로 해석함). 필요하면
  다음 후보로 남겨둘 것.

**구현 상세**:
- `User` 엔티티: `Role.ROLE_SUPER_ADMIN` 추가, `active`(기본 true)/
  `canManageReports`/`canManagePosts`/`canManageScheduleComments`(전부
  기본 false) 컬럼 추가, `isSuperAdmin()`/`isAdmin()` 헬퍼 메서드.
- `AdminAccessInterceptor`(신규) + `WebConfig`에 `/admin/**` 경로로 등록.
- `SecurityConfig`: `hasRole("ADMIN")` → `hasAnyRole("ADMIN","SUPER_ADMIN")`,
  `accessDeniedHandler` 추가.
- `AdminHomeController`(신규, `main.controller` 패키지): `/admin` 진입점
  (권한별 첫 메뉴로 리다이렉트) + `/admin/access-denied`.
- `AdminUserService`/`AdminUserController`: `getAllAdmins()`(관리자만 필터),
  `updatePermissions()`, `deactivateUser()`/`activateUser()`,
  `getUserProfile()`(게시글/댓글 수 + 최근 게시글 5개 — `PostRepository`/
  `PostCommentRepository`에 `countByAuthor_IdAndDeletedFalse()` 등 신규
  쿼리 메서드 추가) 추가. `setRole()`은 총관리자 대상 변경을 막고, 부관리자→
  학생 강등 시 권한 플래그 3개를 자동으로 꺼서(예전 권한이 남아있다가
  나중에 재승격됐을 때 아무 확인 없이 부활하지 않도록) 정리.
- 신규 템플릿: `admin/admin-permissions.html`(권한 토글 대시보드),
  `admin/user-profile.html`(프로필), `admin/access-denied.html`(안내).
- `admin/user-list.html`: 프로필 링크, 비활성화/활성화 버튼, 총관리자·
  부관리자·학생 3단계 역할 배지, "관리자 권한 관리로 →" 링크 추가.
- `fragments/navbar.html`(관리자 링크 → `/admin`, 역할 체크 갱신) /
  `admin/fragments/nav.html`(부관리자는 권한 있는 탭만 노출, 계정 관리
  탭은 총관리자 전용) 갱신.
- `admin.css`: `.admin-permission-*`(권한 토글 행), `.admin-profile-*`
  (프로필 페이지 정보 그리드/통계 카드) 클래스 추가.

**진행 중 발견한 버그 2건(6번 항목 버그#9/#10에 상세 기록, 둘 다 이번
라운드 안에서 직접 수정)**:
1. `ddl-auto: update`가 이번엔 `users.role` enum에 `ROLE_SUPER_ADMIN`
   추가를 자동으로 반영하지 못해서 mysql로 직접 `ALTER TABLE ... MODIFY
   COLUMN role ENUM(...)`를 실행해야 했다.
2. `AdminUserService`의 "마지막 남은 관리자는 강등/탈퇴 불가" 가드가
   `ROLE_SUPER_ADMIN` 도입 이후엔 항상 거짓이어야 하는데 그대로 남아있어서,
   총관리자가 유일한 부관리자의 권한을 해제하려 해도 조용히 막히는 버그가
   됐다 — curl로 승격→권한부여→권한해제 시나리오를 재현하다가 발견,
   `AdminUserService.setRole()`/`deleteUser()`와
   `UserService.deleteAccount()` 세 곳 모두에서 이 가드를 제거했다(대신
   `deleteAccount()`엔 총관리자 본인 자진 탈퇴를 막는 새 가드 추가).

**검증**: `./gradlew compileJava`로 컴파일 확인 후, 실행 중인 devtools
프로세스에 `compileJava`+`processResources`로 반영. curl로 쿠키를 유지하며
전체 시나리오를 순서대로 실제 확인함 — admin 로그인 → `/admin` 접속 시
`/admin/reports`로 자동 이동 → `user2`를 부관리자로 승격(권한 전부 꺼진
상태로 시작하는지 DB로 확인) → 그 상태로 `user2` 로그인 시 `/admin`이
`/admin/access-denied`로 감을 확인 → `admin`이 `canManagePosts=true`만
부여 → `user2`로 재로그인 시 `/admin`은 `/admin/posts`로만 이동하고
`/admin/reports`·`/admin/users`·다른 계정 승격 시도는 전부
`/admin/access-denied`로 막히는지 확인 → 프로필 페이지(`/admin/users/
{id}/profile`)와 관리자 권한 대시보드(`/admin/users/admins`) 응답 확인 →
`user3` 비활성화 후 로그인 실패(`/login?error=true`) → 재활성화 후 로그인
성공 → 부관리자 권한 해제(demote) 시도 → 첫 시도에서 버그#10을 발견하고
수정한 뒤 재확인해서 정상 동작 확인. Claude_Browser로도 admin 계정으로
로그인해 "관리자 권한 관리" 페이지에서 실제로 체크박스를 클릭해 즉시
저장되는지(DB 값 변화로 확인), "계정 관리" 목록과 프로필 페이지가
의도대로 렌더링되는지, `user2`로 로그인했을 때 관리자 하위 탭이 보유
권한만큼만(이 경우 "1. 신고 관리" 하나만) 보이는지까지 눈으로 확인했다.
테스트에 썼던 `user2`는 다시 `ROLE_USER`로 되돌려 TestDataSeeder가
만든 원래 상태로 정리해뒀다.

**다음에 이어서 할 때 참고할 것**: 게시글/댓글 상세 화면에서 작성자
프로필로 바로 이동하는 링크는 아직 없다(위 설계 결정 참고, 필요하면
`/admin/users/{id}/profile`을 그대로 재사용하면 됨 — 이미 어떤 대상이든
동작하는 범용 조회 라우트라 새로 만들 것 없음). 관리자 권한 관리
페이지에 검색/페이지네이션은 없다(관리자 수가 적을 걸 가정 — 8번 항목
"관리자 목록 전체가 페이지네이션 없음" 항목과 같은 맥락, 관리자 수가
늘어나면 같이 고려할 것). **2026-08-10(3차)에서 발견됨**: 이 페이지
자체는 `/admin/users` 안의 링크를 눌러야만 갈 수 있고 관리자 상단 탭
바(`admin/fragments/nav.html`)엔 없어서, 총관리자(admin) 본인도 이
기능이 "안 보인다"고 느꼈다 — 아래 3차 라운드에서 5번째 탭으로 승격함.

### 2026-08-10(3차) 라운드 — 캘린더 오늘+선택 겹침 색상, 커뮤니티·관리자
검색/페이지네이션/페이지당 N개 보기, 관리자 권한 관리 탭 노출 (✅ 완료)

사용자 요청 원문 요약: "(캘린더 스크린샷 첨부, 10일=오늘=노란색 옆에
11일=선택됨=진한 파란색) 10일에 노란색과 함께있는 파란색은 내가 선택한
진한파란색을 따라 같은 칸에 나타나게 해줘. 커뮤니티와 관리자 대시보드에서
검색기능/검색창/페이지당 n개 보기 등 구현하고, 부관리자의 권한설정
부분은 admin 계정에서도 안 보이는 것 같아 수정해줘." 세 가지 별개
요청을 한 번에 처리함.

**1) 캘린더 "오늘 = 선택된 날짜" 겹침 색상 (`calendar.css`)**
- 원인: `.fc .fc-day-today.fc-day-selected .fc-daygrid-day-number`(오늘이면서
  동시에 선택된 칸)가 노란 배경 위에 `box-shadow: 0 0 0 3px rgba(14, 165,
  233, 0.55)`(반투명 스카이블루 링, 알파 0.55)만 얹었는데, 이게 노란
  배경과 대비가 약해서 거의 안 보였다 — 사용자가 스크린샷으로 지적한
  "10일엔 파란색이 안 보인다"는 정확히 이 문제.
- 수정: 링 색을 `var(--brand-2-dark)`(`#0284c7`, 불투명 진한 파란색 —
  `.fc-day-selected` 단독 상태가 쓰는 `--brand-gradient`와 같은 계열의
  "진한" 톤)로 바꿔서 노란 배경 위에서도 확실히 보이게 함. 애니메이션
  (`day-highlight-pop`/`day-selected-ring-pulse`)은 그대로 재사용.
- 검증: 브라우저(Claude_Browser)로 `/school/calendar` 접속 후(오늘 날짜가
  기본 선택 상태 - `calendar.js`의 `selectedDateStr = formatLocalDate(new
  Date())`) `document.styleSheets`를 직접 순회해서 해당 CSS 규칙의
  `cssText`가 `box-shadow: 0 0 0 3px var(--brand-2-dark)`로 반영된 것을
  확인. **주의**: `getComputedStyle()`로 애니메이션 진행 중 값을 읽으면
  브라우저 팬이 비표시 상태일 때 프레임 컴포지팅이 멈춰서 애니메이션이
  0% 키프레임 값에 멈춘 것처럼 보일 수 있다(스크린샷 타임아웃과 동일한
  원인) — 실제 정적 규칙(`styleSheets`)을 직접 확인하는 쪽이 더 믿을 만함.

**2) 커뮤니티/관리자 대시보드 검색 + 페이지네이션 + "페이지당 N개 보기"**
- 신규 `global.util.PageUtils`(4번 항목 참고): `normalizeSize(Integer)`
  (요청값을 5~100 사이로 정규화, 기본 10) + `paginate(List, page, size)`
  (이미 메모리에서 필터링된 List를 Spring Data `PageImpl`로 잘라 감싸기).
  관리자 목록들은 원래부터 DB 쿼리가 아니라 `matches()` 헬퍼로 메모리
  필터링을 했기 때문에(6번/8번 항목 여러 곳 참고), 진짜 DB 페이지 쿼리로
  바꾸는 대신 이미 필터링된 List를 자르는 방식을 택함 — 관리자 수/게시물
  수가 정말 많아지면 나중에 리포지토리 단 `Pageable` 쿼리로 바꿔야 한다는
  전제는 여전히 유효함(8번 항목 "관리자 목록 전체가 페이지네이션 없음"
  갱신 참고).
- **커뮤니티(`/posts`)**: 원래도 검색(`keyword`)과 페이지네이션은 있었지만
  페이지 크기가 `PostService.PAGE_SIZE = 10`으로 고정돼 있었다.
  `PostController`/`PostService.getList()`에 `size` 파라미터를 추가하고
  `post/list.html`에 "페이지당 10/20/30/50개" 셀렉트를 추가(카테고리 탭 +
  검색 폼 + 페이지네이션 링크 전부 `size`를 들고 다니게 수정).
- **관리자 게시글(`/admin/posts`)**: 검색은 있었지만 페이지네이션이
  전혀 없었다(목록이 무한정 길어짐) — `AdminPostController`에 `page`/
  `size` 추가, `PageUtils.paginate()`로 감싸고 `post-pagination`(커뮤니티와
  동일 CSS) + 페이지 크기 셀렉트 추가.
- **관리자 신고 관리(`/admin/reports`)**: **버그 수정** — 서비스 레이어
  (`AdminPostService.getReportedPosts/getReportedComments`,
  `AdminScheduleCommentService.getReportedComments`)는 이미 keyword
  필터링을 지원했는데 `AdminReportController.list()`가 `keyword` 파라미터
  자체를 안 받고 항상 `null`을 넘겨서 검색창 자체가 없었다(2026-08-05
  4차 라운드에서 컴파일 에러 수습하며 남겨둔 상태 그대로, 8번 항목에
  "다음 후보"로만 기록돼 있던 것 — 자세한 배경은 위 문단 참고). 이번에
  `keyword`/`page`/`size`를 추가하고, 게시물/댓글/한마디 3개 탭 각각에
  검색 폼 + 페이지네이션을 붙였다. 탭마다 서로 다른 모델 속성(`posts`/
  `comments`/`scheduleComments`)만 채워지므로, 템플릿에서 `th:with`로
  "현재 탭의 페이지 크기"(`currentSize`)를 한 번만 계산해서 재사용 —
  SpEL 삼항 연산자는 단축 평가라 존재하지 않는 모델 속성(예: 게시물
  탭인데 `comments.size`)을 건드리지 않는다는 점을 확인하고 이 패턴을
  씀(스프링 EL/Thymeleaf에서 검증됨).
- **관리자 한마디 관리(`/admin/schedule-comments`)**: 검색은 있었지만
  페이지네이션이 없었다 — 게시글 관리와 동일 패턴으로 추가.
- **관리자 계정 관리(`/admin/users`)**: **더 심각한 버그 발견** —
  `user-list.html`엔 검색 폼(`keyword` GET 파라미터)이 이미 있었는데
  `AdminUserController.list()`가 `Model model` 하나만 받고 `keyword`
  파라미터 자체를 선언하지 않아서, 검색창에 뭘 입력해 검색해도 **항상
  전체 목록이 그대로 나왔다**(요청은 갔지만 서버가 완전히 무시). 브라우저로
  `/admin/users?keyword=user2` 접속해 재현 확인 후, `AdminUserService`에
  `getAllUsers(String keyword)`(아이디/닉네임/학교명 대상 `matches()`
  필터, 다른 관리자 서비스와 동일 패턴)를 추가하고 컨트롤러가 이걸 쓰도록
  수정. 페이지네이션도 함께 추가. **검증**: 수정 전엔 검색이 씹혔던 걸
  재현 → 수정 후 `keyword=user2`로 정확히 1건만 나오는 것 확인.
- CSS: `post.css`에 `.list-toolbar`(검색 폼 + 페이지 크기 셀렉트를 한 줄에
  배치하는 공용 래퍼) / `.page-size-select` 신규 추가 — `post.css`는
  커뮤니티/관리자 페이지 전부에 이미 로드돼 있어서 공용으로 씀.

**3) "관리자 권한 관리"(`/admin/users/admins`) 노출 문제**
- 원인: 2026-08-10(2차)에서 이 페이지를 만들 때 상단 4단계 탭 바
  (`admin/fragments/nav.html`)에 넣지 않고, `/admin/users` 페이지 안의
  "관리자 권한 관리로 →" 링크로만 연결해뒀다. 그래서 총관리자(admin)도
  관리자 페이지들을 둘러보다가 이 기능 자체를 못 찾을 수 있었다 — 사용자가
  지적한 "부관리자 권한설정 부분이 admin 계정에서도 안 보인다"는 바로 이
  discoverability 문제였다(권한/인터셉터 로직 자체는 2차 라운드 때 이미
  브라우저로 검증까지 끝난 상태 - 위 2차 라운드 단락 참고).
- 수정: `admin/fragments/nav.html`의 탭 바를 4개→5개로 확장해서
  "5. 관리자 권한"(`/admin/users/admins`, 총관리자 전용)을 항상 노출.
  `admin-permissions.html`도 원래 있던 "계정 관리로 →" 단순 뒤로가기
  링크 대신 이 nav 탭 프래그먼트(`active='admins'`)를 포함하도록 바꿔서
  다른 관리자 화면들과 톤을 맞춤. `user-list.html`의 기존 "관리자 권한
  관리로 →" 링크는 nav 탭과 중복이라 제거(검색/페이지 크기 툴바로
  자리를 대체).
- 검증: 브라우저로 admin 로그인 → `/admin/users`에서 상단 탭에
  "5. 관리자 권한"이 뜨는 것 확인 → 클릭해서 `/admin/users/admins`로
  이동, 부관리자 권한 체크박스 3개가 정상 렌더링되는 것 확인.

**검증 전체**: `./gradlew compileJava` 통과 확인 후, 실행 중이던 devtools
프로세스에 `compileJava`+`processResources`로 반영(2차 라운드와 동일한
방식). 브라우저로 admin 로그인 후 `/admin/users`(검색 버그 재현+수정
확인, 5번째 탭 확인) → `/admin/users/admins`(탭 클릭으로 도달, 체크박스
렌더링 확인) → `/posts`(카테고리/검색/페이지 크기 셀렉트 동작, `size=5`로
강제 페이지네이션 발생시켜 1/2페이지 링크 확인, 셀렉트를 20으로 바꿔
자동 제출되는 것 확인) → `/admin/posts`(검색+페이지네이션, `size=5`로
페이지네이션 링크 확인) → `/admin/reports`(검색어 입력 후 게시물/댓글/
한마디 3탭 전환 시 에러 없이 렌더링되는 것 확인, `type=comment`·
`type=schedule` 둘 다 확인) → `/admin/schedule-comments`(에러 없이
렌더링 확인)까지 순서대로 실제 확인함. `/school/calendar`는 CSS 규칙
자체를 `styleSheets`로 직접 확인(위 1번 단락 참고).

**다음에 이어서 할 때 참고할 것**: `PageUtils.paginate()`는 여전히 "전체
List를 메모리에서 자르는" 방식이라, 관리자 쪽 데이터가 아주 많아지면
리포지토리 단 `Pageable`/`@Query` 기반으로 바꿔야 한다(8번 항목 갱신된
단락 참고). `/admin/reports`의 POST 액션(블라인드/삭제 등, 실제로는
`AdminPostController`/`AdminScheduleCommentController`가 처리)들은 이번
라운드 이전부터 액션 후 `keyword`/`page`/`status`를 유지하지 않고 항상
목록 맨 앞으로 리다이렉트하는 기존 동작 그대로 뒀다(예: `/admin/
schedule-comments/{id}/delete` → 무조건 `redirect:/admin/schedule-comments`) -
이번 라운드 범위 밖이라 손 안 댔지만, 검색/페이지네이션이 생긴 지금은
"검색해서 찾은 글을 조치하면 검색 조건이 날아간다"는 게 더 눈에 띌 수
있으니 다음 후보로 고려할 것.

### 2026-08-10(4차) 라운드 — "내가 신고한" 표시, 관리자 액션 검색조건 유지,
커뮤니티 검색 심화(범위/정렬), 댓글 전용 관리 화면, 댓글→원문 이동,
게시글 목록 댓글신고 배지 (✅ 완료)

사용자 요청 원문 요약: 직전 라운드(3차)가 끝난 뒤 "추가로 더 구현할
검색기능이나 관리자 기능 있을까?"라고 물어봐서, 코드/CLAUDE.md를 근거로
후보 5개를 제시함(검색 2개: "내가 신고한 글/댓글" 표시, 관리자 액션 후
검색조건 유지 / 관리자 3개: 댓글 전용 관리 화면, 댓글 신고→원문 이동,
게시글 목록 댓글신고 배지). 사용자 응답: "검색기능은 지금 말한 후보들
추가하고 추가로 더 풍부하게 만들어 / 관리자기능은 니가 말한거 전부
진행해" — 검색 2개 + 관리자 3개 전부, 그리고 검색은 "더 풍부하게"라는
요청에 맞춰 검색 범위(제목/내용)와 정렬 옵션을 추가로 얹었다(내 판단으로
정한 것 - 사용자가 구체적으로 지정하진 않음).

**1) "내가 신고한 글/댓글" 표시 (`reportedByMe`)**
- `PostDetailDto`/`PostCommentDto`/`ScheduleCommentDto`에 `reportedByMe`
  필드 추가. `PostService.toDetailDto()`/`PostCommentService.toDto()`/
  `ScheduleCommentService.toDto()`가 각각 기존에 이미 있던
  `PostReportRepository.existsByPost_IdAndReporter_Username()` /
  `CommentReportRepository.existsByComment_IdAndReporter_Username()` /
  `ScheduleCommentReportRepository.existsByComment_IdAndReporter_Username()`
  (원래는 신고 중복 방지용)를 재사용해서 계산 — 새 쿼리를 만들 필요가
  없었음.
- 프론트: `post/detail.html`의 신고 버튼은 `th:disabled="${post.reportedByMe}"`
  + 텍스트를 "신고완료"로 서버 렌더링. 댓글/한마디는 AJAX로 렌더링되는
  구조라 `post-detail.js`/`calendar.js`의 `renderComments()`에서
  `c.reportedByMe`가 true면 처음부터 `disabled` 속성이 붙은 신고 버튼을
  만들도록 수정(기존에 신고 성공 직후 버튼을 비활성화하던 코드는 그대로
  둠 - 새로고침 전/후 양쪽 다 커버됨). CSS는 이미 `:disabled` 스타일이
  세 군데(`post-report-btn`/`post-comment-report-btn`/`comment-report-btn`)
  전부 있어서 추가할 것이 없었음(신고 성공 후 비활성화 처리가 이미 있었기
  때문으로 보임).
- **검증**: user2로 로그인해 실제 댓글(id=1)의 `/posts/{id}/comments` 응답에서
  `reportedByMe:false` 확인 → `/report` API 호출로 신고 → 같은 API를 다시
  호출해 `reportedByMe:true`로 바뀐 것 확인 → 페이지 새로고침 후 버튼이
  `disabled=true`, `title="이미 신고했어요"`로 렌더링되는 것까지 확인.
  테스트로 늘어난 신고 카운트/신고 로우는 mysql로 직접
  `DELETE FROM comment_reports WHERE id=2; UPDATE post_comments SET
  report_count = report_count - 1 WHERE id=1;`로 원상복구함(2026-08-05
  세션들이 테스트 계정 정리했던 것과 같은 이유 - 검증용 흔적을 실제
  데이터에 남기지 않기 위함).

**2) 관리자 액션 후 검색조건 유지**
- 신규 `PageUtils.buildListRedirect(basePath, extraParams, page, size)` +
  `PageUtils.params(...)`(가변 인자로 Map 만드는 헬퍼) — extraParams의
  값이 null/빈 문자열이면 자동으로 생략되고, `UriComponentsBuilder`로
  안전하게 인코딩해서 `"redirect:" + url`을 반환한다.
- 신규 `admin/fragments/list-state.html` — 행별 액션 폼에 공통으로 넣는
  hidden input 4개(status 또는 type / keyword / page / size) 프래그먼트.
  각 관리자 목록 화면(`AdminScheduleCommentController`/
  `AdminCommentController`(4번 항목)/`AdminUserController`)의 액션
  메서드가 `@ModelAttribute` 커맨드 객체로 이 값들을 그대로 받아서
  `PageUtils.buildListRedirect()`로 리다이렉트를 만든다(컨트롤러마다
  내부 `static class ListState`로 이 커맨드 객체를 정의 - Spring MVC의
  `@ModelAttribute`가 폼 파라미터를 자동 바인딩해줘서 각 액션 메서드에
  `@RequestParam`을 4개씩 나열할 필요가 없어짐).
- `AdminPostController`는 한 단계 더 복잡했다 — 게시글 관리는 행별
  인라인 액션이 없고 액션이 전부 상세 화면(`/admin/posts/{id}`)에
  있어서, "목록에서 상세로 넘어갈 때 들고 온 필터 상태"까지 같이
  들고 다녀야 한다. `admin/post-list.html`의 제목 링크가
  `status/keyword/page/size`를 쿼리 파라미터로 붙여서 상세로 넘기고,
  `AdminPostController.detail()`이 그걸 받아 `listStatus`/`listKeyword`/
  `listPage`/`listSize` 모델 속성으로 노출하면, `post-detail.html`의
  "목록으로" 링크와 액션 폼들이 그 값을 그대로 다시 넘긴다. 액션 중
  `blind`/`unblind`/`restore`/`clear-report`/`clearCommentReport`는
  상세 화면으로(`ListState.redirectToDetail(id)`), `delete`만 목록으로
  (`ListState.redirectToList()`) 돌아간다(삭제된 글은 더 이상 검토
  대상이 아니므로 목록으로 보내는 게 자연스럽다고 판단).
- **검증**: 브라우저 fetch로 `/admin/comments/1/blind`에
  `keyword=dsaf&page=0&size=20`을 보내고 응답의 최종 URL이
  `/admin/comments?keyword=dsaf&page=0&size=20`인 것 확인(→ 바로
  `/unblind`로 원복). `/admin/users?keyword=user3&size=20` 응답 HTML에서
  승격 폼의 hidden input이 `keyword=user3/page=0/size=20`으로 정확히
  채워지는 것 확인(실제 승격은 안 함 - 렌더링만 확인). `/admin/posts?
  keyword=질의&size=20` → 제목 링크가 `/admin/posts/9?status=&keyword=
  질의&page=0&size=20`으로 나가는 것 → 그 URL로 상세 진입 시 "목록으로"
  링크와 블라인드 폼 hidden input이 전부 같은 값을 들고 있는 것까지
  확인(리스트→상세→액션 전체 체인 검증 완료).

**3) 커뮤니티 검색 심화 — 검색 범위 + 정렬**
- `PostRepository.search()`의 하드코딩된 `ORDER BY p.createdAt DESC`를
  제거하고 `Pageable`의 `Sort`를 그대로 따르게 바꿈(Spring Data JPA가
  `@Query`에 ORDER BY가 없으면 Pageable의 Sort를 자동으로 붙여준다) —
  이래야 `PostService.getList()`가 "최신순/오래된순/조회수순" 여러
  정렬을 이 쿼리 하나로 지원할 수 있음. 검색 범위는 새 `:scope` 파라미터로
  처리(`scope <> 'content'`면 제목 매칭, `scope <> 'title'`이면 내용 매칭 —
  기본값(빈 문자열)은 둘 다 걸림).
- **의도적으로 안 한 것**: 관리자 검색(`admin/post-list.html` 등)은 이미
  작성자 실제 닉네임으로 검색이 되는데, 이건 관리자 화면이 원래도
  블라인드/익명 여부와 무관하게 실명을 보여주기 때문에(`PostService.
  displayNickname()`은 일반 사용자 화면 전용, 관리자 DTO는 처음부터
  `post.getAuthor().getNickname()`을 그대로 씀) 문제가 없다. 반면
  **커뮤니티(일반 사용자) 검색에는 작성자 닉네임 검색을 추가하지
  않았다** — 익명 카테고리 게시물도 작성자 엔티티 자체는 실명을 갖고
  있어서, 실명으로 검색했을 때 "이 사람이 익명 글을 썼다"는 게 검색
  결과로 드러나면 익명 기능의 취지가 깨진다고 판단했기 때문(다음에
  이 기능이 다시 요청되면 이 근거를 참고할 것 - 최소한 ANONYMOUS
  카테고리는 닉네임 검색 대상에서 제외해야 함).
- `post/list.html`: 검색 폼을 세로 2줄로 늘려서(`.post-search-form-rich`)
  첫 줄은 검색어 입력, 둘째 줄은 검색 범위 라디오(제목+내용/제목만/
  내용만) + 정렬 셀렉트(최신순/오래된순/조회수 많은순, 값 바뀌면
  자동 제출). `scope`/`sort`를 카테고리 탭·페이지크기 셀렉트·페이지네이션
  링크 전부에 실어서 어떤 조작을 해도 나머지 조건이 안 날아가게 함.
- **검증**: `/posts?sort=views` 응답에서 조회수 4/1/0/0/... 순으로
  정렬된 것 확인. `/posts?keyword=가나다&scope=title`(존재하지 않는
  검색어)로 "검색 결과가 없어요" 정상 렌더링(에러 없음) 확인.

**4) 댓글 전용 관리 화면(`/admin/comments`)**
- 신규 `AdminCommentController` — `AdminScheduleCommentController`와
  거의 동일한 구조(전체/삭제됨 탭 + 검색 + 페이지네이션 + 블라인드/
  삭제/복구/문제없음 처리). `AdminPostService`에 `getAllComments(keyword)`/
  `getDeletedComments(keyword)`(신규 `PostCommentRepository.
  findAllByDeletedFalseOrderByCreatedAtDesc()`/`findAllByDeletedTrueOrder
  ByDeletedAtDesc()` 기반) + `setCommentBlind()`/`deleteComment()`/
  `restoreComment()`(게시글 상세 화면을 거치지 않고 댓글 하나를 바로
  조치하기 위한 메서드, 기존 `clearCommentReport()`는 재사용) 추가.
  반환 DTO는 새로 만들지 않고 기존 `AdminCommentReportSummaryDto`를
  재사용(이미 `postId`/`postTitle`이 있어서 "소속 게시글" 링크에
  그대로 씀) — 여기에 `deleted`/`deletedAt` 필드만 추가함.
- 권한: 댓글은 게시글에 종속된 하위 리소스라 새 권한 플래그를 만들지
  않고 `canManagePosts`에 얹었다 — `AdminAccessInterceptor`가
  `/admin/comments`도 `/admin/posts`와 같은 조건으로 검사하도록 한 줄
  추가.
- `admin/fragments/nav.html`: 대분류 탭이 5개→6개로 늘어남
  ("1.신고관리 2.게시글관리 **3.댓글관리(신규)** 4.한마디관리 5.계정관리
  6.관리자권한" — 게시글 관리 바로 뒤에 배치).
- **검증**: 브라우저로 `/admin/comments` 접속해 실제 댓글(user1이 쓴 것
  아니라 admin이 쓴 "dsaf", 신고 1건) 목록/탭/검색폼/페이지크기 셀렉트가
  전부 렌더링되는 것 확인.

**5) 댓글 신고 → 원문 위치로 바로 이동**
- `admin/comment-list.html`/`admin/report-list.html`(댓글 탭)의 "소속
  게시글" 링크가 이제 `/admin/posts/{postId}#comment-{commentId}` 형태로
  나감(Thymeleaf `@{...}` 링크 빌더 대신 `${'...' + var + '...'}` 문자열
  연결로 프래그먼트를 붙임 - `@{}` 문법 안에서는 임의 문자열 연결이
  안 돼서 이 방식을 씀).
- `admin/post-detail.html`의 댓글 목록 각 항목에 `th:id="'comment-' +
  ${comment.id}"` 추가 + 페이지 하단에 작은 인라인 스크립트: URL 해시가
  `#comment-`로 시작하면 해당 요소에 `.admin-report-item-highlight`
  클래스를 잠깐(2.6초) 붙이고 `scrollIntoView({block:'center'})`로
  스크롤. 브라우저가 앵커로 자동 스크롤은 해주지만 어떤 댓글인지 눈에
  안 띄어서(사용자 지적) 배경을 노란색으로 잠깐 강조하는 CSS
  (`admin.css`)를 추가함.
- **검증**: `/admin/comments`에서 "소속 게시글" 링크의 실제 href가
  `/admin/posts/9#comment-1`인 것 확인 → 그 URL로 이동해 `#comment-1`
  요소가 존재하는 것과(`getElementById`) 하이라이트 스크립트가 실행됐다가
  타임아웃으로 클래스가 다시 빠진 것(정상 동작 - 지속적으로 남아있으면
  오히려 버그)까지 확인.

**6) 게시글 목록에 "신고된 댓글 있음" 배지**
- `AdminPostSummaryDto`에 `reportedCommentCount` 필드 추가. 신규
  `PostCommentRepository.countReportedOrBlindByPostId(postId)`
  (`c.deleted=false AND (c.blind=true OR c.reportCount>0)`인 댓글 수) —
  `AdminPostService.toSummaryDto()`가 게시물 하나마다 이 쿼리를 한 번씩
  더 날린다(기존에도 `latestReportAt` 계산 때문에 게시물별로 신고 목록을
  또 조회하고 있어서 - N+1 자체는 새로운 패턴이 아니라 기존 컨벤션을
  따른 것. 8번 항목 "관리자 목록 전체가 페이지네이션 없음" 갱신 단락과
  같은 맥락으로, 데이터가 아주 많아지면 이 부분도 같이 최적화 대상임).
- `admin/post-list.html`(전체 게시글 탭)과 `admin/report-list.html`
  (게시물 탭) 둘 다 제목 옆에 `reportedCommentCount > 0`이면 "댓글 신고
  N" 배지(노란/빨강 톤 `.admin-status-blind` 재사용)를 붙임.
- **검증**: `/admin/posts` 목록에서 "질의응답 테스트 게시글 3" 제목 옆에
  "댓글 신고 1" 배지가 실제로 뜨는 것 확인(이 글의 댓글 id=1이 신고
  1건 있는 상태였음).

**검증 전체**: `./gradlew compileJava` 통과 확인 후 실행 중이던 devtools에
`compileJava`+`processResources`로 반영. 위 1~6번 각 단락에 적어둔
개별 검증에 더해, user2 계정으로 실제 로그인해서(fetch 기반 로그인 -
CSRF가 개발 편의상 꺼져 있어서 가능함, `SecurityConfig.java:24`)
`reportedByMe` 플로우를 종단간 확인한 것과, admin 계정으로 돌아와
관리자 화면들을 순서대로 확인한 것이 이번 라운드의 핵심 검증이었다.
테스트로 만든 신고 데이터는 전부 mysql로 직접 원상복구함(위 1번 단락).

**다음에 이어서 할 때 참고할 것**:
- `ScheduleComment`(오늘의 한마디)에는 `reportedByMe`를 추가했지만
  댓글 관리 화면 같은 "전용 관리 화면"은 이미 `/admin/schedule-comments`가
  있어서 새로 만들 것이 없었다(4번 항목은 `PostComment` 전용 갭이었음).
- 마이페이지 "내가 신고한 글/댓글 목록" 페이지는 여전히 없음(1번 단락
  "의도적으로 안 한 것" 참고) — `reportedByMe` 필드 자체는 있으니
  `PostReportRepository`/`CommentReportRepository`/
  `ScheduleCommentReportRepository`에 `findByReporter_Username()`류
  쿼리만 추가하면 비교적 빠르게 만들 수 있음.
- 커뮤니티 검색에 작성자 닉네임 검색은 익명 계정 정보 유출 우려로
  일부러 안 넣었다(3번 단락 참고) — 나중에 요청이 오면 최소한
  `category != 'ANONYMOUS'` 조건을 걸고 넣을 것.
- `/admin/reports`의 POST 액션(블라인드/삭제 등)이 검색조건을 안
  들고 다니는 건 3차 라운드에서 "범위 밖"으로 남겨뒀던 이슈인데,
  이번에 `/admin/comments`/`/admin/schedule-comments`/`/admin/users`는
  전부 해결했다. `/admin/reports`는 애초에 조회 전용 화면(실제 액션은
  `AdminPostController`/`AdminScheduleCommentController`가 처리)이라
  범위에서 계속 빠져 있음 - 필요하면 `/admin/reports`에서 넘어갈 때도
  `AdminPostController`/`AdminScheduleCommentController`의 `ListState`가
  `/admin/reports`로 돌아가는 옵션을 추가하는 방향으로 확장 가능.

### 2026-08-10(5차) 라운드 — 작성자 이름 클릭 → 프로필 확인 (커뮤니티 +
관리자, 게시물/댓글/한마디 전부) (✅ 완료)

사용자 요청 원문: "커뮤니티와 관리자 페이지에서 작성자(댓글, 게시물,
한마디 모두)이름을 눌러서 그 사람 프로필을 확인할 수 있는 기능을
구현하자." 이 학교 커뮤니티 서비스는 미성년자로 추정되는 학생들이 실명
닉네임으로 활동하는 곳이라, 구현 전에 **두 가지를 사용자에게 직접
확인**했다(코드나 기존 컨벤션만으로는 답이 안 나오는 정책 결정이라
AskUserQuestion으로 물어봄):
1. 커뮤니티(일반 사용자) 화면에서 프로필에 뭘 보여줄지 → **"최소 정보만
   (닉네임 + 작성 글 목록)"** 선택. 학교/학년/반은 노출 안 함.
2. 관리자 화면에서는 어떤 관리자까지 프로필을 볼 수 있게 할지 →
   **"해당 화면 권한이 있는 부관리자까지"** 선택.

**1) 커뮤니티(일반 사용자) 공개 프로필 — `/users/{id}` (신규)**
- 신규 `user.dto.PublicUserProfileDto`(닉네임 + 게시글 `Page`)/
  `PublicUserProfilePostDto`(uuid/title/categoryLabel/createdAt/viewCount),
  `user.service.UserProfileService`, `user.controller.UserProfileController`.
  `UserService`(회원가입/마이페이지, 자기 자신 관리)나 `AdminUserService`
  (총관리자 전용 계정 관리)와는 책임이 달라서("다른 사람을 조회만 하는"
  새 책임) 별도 서비스로 분리 — 이 프로젝트의 기존 컨벤션(AdminPostService
  등을 기존 서비스와 분리해온 것)과 같은 이유.
- **핵심 설계 결정 — 익명 게시물은 본인 프로필에서도 제외**: 신규
  `PostRepository.findByAuthor_IdAndCategoryNotAndDeletedFalseAndBlindFalse
  OrderByCreatedAtDesc(authorId, ANONYMOUS, pageable)`로 ANONYMOUS 카테고리
  글을 아예 쿼리에서 뺐다. 안 빼면 "이 사람 프로필에 있는 글 목록에 이
  글이 있다 = 이 사람이 이 익명 글을 썼다"는 게 드러나서 익명 기능
  자체가 무너진다 — 3번 항목(2026-08-10(4차) 라운드, 커뮤니티 검색에
  작성자 닉네임 검색을 일부러 안 넣은 것)과 완전히 같은 이유의 판단.
  탈퇴한 계정(`user.isDeleted()`)도 프로필 자체를 404 취급(`redirect:/posts`).
- `SecurityConfig`: `GET /users/*`를 게시글 조회와 동일하게 `permitAll`로
  등록(로그인 없이도 커뮤니티를 둘러볼 수 있는 것과 일관성).

**2) 관리자 공개 프로필 — `/admin/profiles/{id}` (신규, 기존
`/admin/users/{id}/profile`과는 별개 경로)**
- 기존 `/admin/users/{id}/profile`은 `AdminAccessInterceptor`가
  `/admin/users/**` 전체를 총관리자 전용으로 막아둔다(계정 관리 자체가
  총관리자 전용이라서). 그런데 게시글/댓글/한마디 "관리" 화면은 권한만
  있으면 부관리자도 들어오는 화면이라, 거기서 작성자 이름을 누르는 것까지
  총관리자 전용으로 막으면 그 화면을 보고 있는 부관리자 본인이 프로필을
  못 보는 모순이 생긴다. 그래서 **완전히 새 경로**(`/admin/profiles/{id}`,
  신규 `AdminProfileController`)로 분리하고, `AdminAccessInterceptor`에
  "신고/게시글/한마디 관리 권한이 하나라도 있으면 통과"하는 조건을 추가
  했다 — 이 화면들에서 이미 실명 닉네임 등 같은 정보를 보고 있으므로
  새로 노출되는 정보가 없다고 판단.
- 화면/데이터는 100% 재사용: `AdminProfileController`가
  `AdminUserService.getUserProfile()`을 그대로 호출하고
  `admin/user-profile.html` 템플릿도 그대로 쓴다(학교/학년/반/관리자
  권한/작성 게시글·댓글 수까지 - 이건 애초에 조작 버튼이 없는 읽기 전용
  화면이라 재사용해도 안전함). 다만 템플릿 상단 "뒤로가기" 링크가
  "계정 관리로"(`/admin/users`)로 고정돼 있었는데, 총관리자가 아니면
  못 가는 화면이라 `loginUser.role.name() == 'ROLE_SUPER_ADMIN'` 조건으로
  분기해서 부관리자에게는 "관리자 홈으로"(`/admin`)를 대신 보여준다.
- **검증**: 이미 DB에 부관리자 권한이 전부 꺼진 상태로 남아있던 `user1`
  (ROLE_ADMIN, 세 권한 모두 false - 이전 세션에서 만들어진 상태로 추정,
  건드리지 않고 그대로 테스트에 활용)로 로그인해 `/admin/profiles/6`
  요청 → `/admin/access-denied`로 막히는 것 확인 → mysql로
  `can_manage_posts=1`만 부여 → 같은 요청이 200으로 통과하고 뒤로가기
  링크가 "관리자 홈으로"인 것까지 확인 → 테스트 끝나고 권한을 다시 0으로
  원복(검증용 흔적을 실제 데이터에 남기지 않기 위함, 이전 라운드들과
  동일한 습관).

**3) 콘텐츠 화면들에 링크 연결 — `authorId`/`authorLinkable` 필드**
- 커뮤니티: `PostListItemDto`/`PostDetailDto`/`PostCommentDto`/
  `ScheduleCommentDto`에 `authorId`+`authorLinkable`(boolean) 추가.
  `authorLinkable`은 "익명 게시물이 아니고(게시글에 한함) 작성자가
  탈퇴하지 않았을 때만" true — 이 판단을 서비스 단(`PostService.
  isAuthorLinkable()` 등)에서 미리 계산해서 넘기고, 템플릿/JS는
  `authorLinkable`이 true일 때만 `<a href="/users/{authorId}">`로, false면
  기존처럼 그냥 `<span>`으로 렌더링한다(문자열 비교로 판단하지 않고
  boolean 하나로 판단하게 해서 템플릿이 익명/탈퇴 로직을 다시 알 필요가
  없게 함). 댓글/한마디는 익명 개념이 없어서(카테고리 무관 항상 실명)
  탈퇴 여부만 본다.
  - 관리자 DTO(`AdminPostSummaryDto`/`AdminPostDetailDto`/
    `AdminCommentItemDto`/`AdminCommentReportSummaryDto`/
    `AdminScheduleCommentSummaryDto`)에는 `authorId`만 추가(관리자
    화면은 애초에 익명이든 아니든 항상 실명을 보여주므로 linkable
    판단이 필요 없음 - `/admin/profiles/{id}`로 무조건 링크).
- **커뮤니티 게시글 목록의 HTML 구조를 바꿈**: `post/list.html`은 원래
  `<li>` 전체가 `<a class="post-list-link">` 하나였고 그 안에 닉네임도
  들어있었는데, `<a>` 안에 `<a>`를 중첩하면 HTML 파서가 깨뜨려서
  (adoption agency algorithm - 브라우저가 앵커를 예측 불가능하게
  쪼개버림) 닉네임을 별도 링크로 뺄 수가 없었다. 그래서 `<li>` 자체가
  flex 행을 맡고 `.post-list-link`(제목만)와 `.post-list-meta`(닉네임
  링크 + 날짜 + 조회수)가 형제 요소가 되도록 구조를 바꿨다
  (`post.css`의 `.post-list-item`/`.post-list-link` 관련 규칙 전부
  갱신, 모바일 반응형 규칙도 함께 이동). 시각적으로는 기존과 동일하게
  한 줄에 나란히 보인다 - 실제로 중첩 `<a>` 문제였는지 브라우저
  `outerHTML`을 직접 찍어서 형제 구조로 파싱된 것 확인.
- 나머지(게시글 상세, 댓글, 한마디, 관리자 테이블들)는 애초에 닉네임이
  다른 링크 안에 중첩돼 있지 않아서 구조 변경 없이 `<span>`→`<a>`
  전환만 하면 됐다.
- 링크가 걸린 곳: `post/list.html`, `post/detail.html`,
  `post-detail.js`(댓글, AJAX 렌더링), `calendar.js`(한마디, AJAX
  렌더링), `admin/post-list.html`, `admin/post-detail.html`(게시물
  작성자 + 댓글 작성자 둘 다), `admin/comment-list.html`,
  `admin/schedule-comment-list.html`, `admin/report-list.html`(게시물/
  댓글/한마디 3탭 전부).

**검증 전체**: `./gradlew compileJava` 통과 후 devtools에
`compileJava`+`processResources`로 반영. 브라우저로 `/posts` 목록에서
질의응답/자유 카테고리는 `<a href="/users/1">user1</a>`로, 익명
카테고리는 `<span>익명</span>`(링크 없음)으로 렌더링되는 것을
`outerHTML`까지 찍어서 확인 → `/users/1`(user1의 프로필) 접속 시 자유
3개+질의응답 3개(총 6개)만 보이고 **익명으로 쓴 3개는 목록에서 빠진
것** 확인(익명 정체 비노출 검증의 핵심) → 게시글 상세/댓글 작성자
링크(`/users/1`, `/users/6`) 확인 → `/school/api/comments` fetch
응답에 `authorId`/`authorLinkable` 필드가 정상적으로 들어있는 것 확인
(캘린더 위젯 자체는 학교 선택이 필요해 브라우저로 직접 클릭 검증은
생략하고 API 응답 + 코드 리뷰로 대체 - `post-detail.js`와 완전히
동일한 패턴이라 신뢰도 높음) → 관리자 화면들에서 `/admin/profiles/{id}`
링크가 렌더링되는 것 확인(신고 없는 상태라 `/admin/reports`의 게시물/
한마디 탭은 목록 자체가 비어서 링크가 없는 게 정상 - "신고 누적 게시물이
없어요" 문구로 빈 상태임을 재확인) → 2번 단락에 적은 권한 검증까지.

**다음에 이어서 할 때 참고할 것**: `ScheduleCommentDto`는 여전히 탈퇴
사용자 닉네임을 "탈퇴한 사용자"로 안 바꾼다(4차 라운드에도 언급된
기존 갭, `ScheduleCommentService.toDto()`) — `authorLinkable`은 탈퇴
여부로 올바르게 false가 되지만, 화면엔 탈퇴 계정의 옛날 닉네임이 그대로
찍힌다는 점은 유의. 커뮤니티 프로필(`/users/{id}`)에 페이지네이션은
넣었지만 "페이지당 N개 보기" 셀렉트는 안 넣었다("최소 정보만"이라는
설계 결정에 맞춰 의도적으로 단순하게 유지 - 필요하면 3차 라운드의
`.page-size-select` 패턴을 그대로 붙이면 됨).

---

## 2026-08-10 (6차 라운드) — 마이페이지 자기 콘텐츠 관리 + 소개글 +
캘린더 바로가기 제거 + 신고 "문제없음 철회" + 향후 로드맵 문서화

사용자 요청 원문(요약): "본인 프로필에서 본인이 작성한 글(게시글/한마디/
댓글)을 관리할 수 있게 해줘 / 남이 보는 내 프로필에 소개글을 짧게 적을
수 있는 기능 추가 / 프로필에 보이는 캘린더 바로가기는 삭제 / 관리자
기능 중 신고 시 문제없음 처리할 때 댓글 문제없음 철회도 만들어줘" +
16개 항목짜리 장기 기능/인프라 목록을 CLAUDE.md에 전부 할 예정이라고
적어달라는 요청.

**1) 마이페이지 "내가 쓴 글 관리" (`/mypage/activity`)**
- 기존에 `/mypage`(마이페이지)와 `/users/{id}`(남이 보는 공개 프로필,
  5차 라운드에서 만듦)는 서로 다른 화면이었는데, 사용자가 말한
  "본인프로필"은 전자(`/mypage`)를 가리킨다 — 자기 자신이 쓴 글을
  "관리"(수정 아님, 삭제 위주)할 수 있는 화면이 없었던 것이 갭이었다.
- 새 화면: 게시글/댓글/오늘의 한마디 3개 탭, 각 탭은 본인이 작성한
  항목을 최신순으로 페이지네이션(10개씩)해서 보여주고 각 항목에
  "삭제" 버튼이 붙는다. 삭제는 기존에 이미 있던 자기소유 검증 로직
  (`PostService.deletePost(id, username)`, `PostCommentService.
  deleteComment(commentId, username)`, `ScheduleCommentService.
  deleteComment(id, username)` - 전부 서비스 내부에서 username으로
  소유자 검증함, `/posts/{uuid}/delete`나 `/school/api/comments/{id}`
  DELETE API가 쓰던 것과 완전히 동일한 메서드)를 그대로 재사용하고,
  새로 만든 `MyActivityController`는 삭제 후 마이페이지 활동 탭/페이지
  상태를 유지한 채로 돌아오는 리다이렉트만 추가로 책임진다(기존
  엔드포인트를 건드리지 않고 새 엔드포인트를 얹은 이유 - 기존
  `/posts/{uuid}/delete`는 항상 `/posts`로, 댓글/한마디 삭제는 REST
  DELETE라서 마이페이지 컨텍스트로 자연스럽게 못 돌아옴).
- "수정"은 이 화면에 넣지 않았다 — 게시글은 이미 게시글 상세 페이지에
  본인 소유일 때만 보이는 수정 버튼이 있고, 댓글/한마디는 원래 인라인
  수정(그 페이지 안에서 바로 편집)만 지원해서 마이페이지에 별도 수정
  UI를 새로 만드는 대신 각 항목에서 원본 위치로 이동하는 링크(게시글
  제목 클릭 → 상세, 댓글의 소속 게시글 제목 클릭 → 그 게시글)만 붙였다.
  한마디는 날짜별로 캘린더에서만 보여서(URL로 특정 날짜에 바로 못 감 -
  `calendar.js`가 날짜 상태를 서버 쿼리 파라미터가 아니라 클라이언트
  상태로 관리함) 원본으로 가는 링크는 생략하고 텍스트 정보만 표시.
- 익명(ANONYMOUS) 게시물도 이 화면에는 그대로 나온다 — 5차 라운드에서
  만든 공개 프로필(`/users/{id}`)은 "이 사람이 이 익명 글을 썼다"가
  드러나면 안 돼서 익명 글을 제외했지만, 마이페이지는 본인만 보는
  화면이라 그 제약이 적용되지 않는다. 그래서 `PostRepository`에
  `findByAuthor_IdAndDeletedFalseOrderByCreatedAtDesc`(카테고리 필터
  없음)를 새로 추가했다 — 공개 프로필용 메서드(카테고리 제외 있음)와는
  분리된 별도 쿼리.
- 새 파일: `user/dto/MyPostSummaryDto.java`, `MyCommentSummaryDto.java`,
  `MyScheduleCommentSummaryDto.java`, `user/service/
  MyActivityService.java`, `user/controller/MyActivityController.java`,
  `templates/user/my-activity.html`.
- 리포지토리 추가: `PostRepository.
  findByAuthor_IdAndDeletedFalseOrderByCreatedAtDesc`,
  `PostCommentRepository.findByAuthor_IdAndDeletedFalseOrderByCreatedAtDesc`,
  `ScheduleCommentRepository.findByUser_IdAndDeletedFalseOrderByCreatedAtDesc`.
- CSS: `mypage.css`에 `.mypage-container-wide`(목록형 화면이라 기본
  마이페이지 카드보다 넓게), `.my-activity-delete-form`,
  `.my-activity-comment-item`/`.my-activity-comment-body`(댓글/한마디는
  게시글과 달리 내용이 위, 메타+삭제버튼이 아래 - 세로 배치) 추가.
  템플릿은 `post.css`(post-list/post-pagination/post-category-tabs)와
  `admin.css`(admin-empty/admin-status-badge)를 재사용해서 새 CSS
  클래스를 최소화했다.

**2) 남이 보는 프로필에 소개글(bio) 추가**
- `User` 엔티티에 `bio`(최대 150자, nullable) 필드 추가. `ddl-auto:
  update`라 서버 재시작 시 `users` 테이블에 컬럼이 자동 생성됨(직접
  ALTER TABLE 안 해도 됨 - 실제로 재시작 후 `DESCRIBE users`로 컬럼
  생겨있는 것 확인).
- `mypage-edit.html`에 소개글 textarea(maxlength=150) 추가, `MyPageUpdateDto`
  → `UserService.updateProfile()`이 저장(150자 초과 시 에러 메시지로
  막음). `AuthController`의 GET/POST 두 군데(정상 흐름 + 계정 삭제
  실패 시 폼 재구성 흐름) 모두 `dto.setBio(user.getBio())` 채워 넣는
  것 빠뜨리지 않게 함(둘 중 하나만 하면 특정 흐름에서 소개글이 화면에서
  사라지는 버그가 났을 것).
- `PublicUserProfileDto`/`UserProfileService.getProfile()`에 `bio` 필드
  추가, `templates/user/profile.html`에 소개글이 있을 때만(`#strings.
  isEmpty` 체크) 닉네임 아래 박스로 표시. `post.css`에 `.profile-bio`
  스타일 추가.

**3) 마이페이지 캘린더 바로가기 제거**
- `mypage.html`의 "캘린더 바로가기"(`/school/calendar`) 버튼을
  "내가 쓴 글 관리"(`/mypage/activity`) 링크로 교체(캘린더는 이미
  상단 네비게이션 바에 있어서 마이페이지에 중복으로 있을 필요가
  없다고 판단한 것으로 보임 - 사용자가 명시적으로 삭제 요청). 기존
  `.mypage-cal-btn` CSS 클래스는 그대로 재사용(버튼 스타일은 똑같이
  유지하고 텍스트/아이콘/href만 교체).

**4) 신고 "문제없음" 판결 철회 기능**
- 기존엔 게시글/댓글/한마디 전부 "문제없음 처리"(`reportCleared=true`)
  버튼은 있는데 되돌리는 버튼이 어디에도 없었다 — 관리자가 실수로
  누르거나 재검토가 필요해져도 UI에서 되돌릴 방법이 없던 갭. 사용자가
  "댓글 문제없음 철회"를 콕 집어 말했지만, 게시글/한마디도 완전히
  동일한 `reportCleared` 필드 패턴을 쓰고 있어서 셋 다 일관되게
  추가했다(하나만 되고 나머지는 안 되면 오히려 헷갈림).
- 서비스: `AdminPostService.unclearReport(id)`(게시글) /
  `unclearCommentReport(commentId)`(댓글) / `AdminScheduleCommentService.
  unclearReport(id)`(한마디) — 전부 `reportCleared`만 `false`로 되돌리고
  `reportCount`/`blind`는 건드리지 않는다(철회하면 원래 신고 카운트
  기준으로 "신고누적" 상태가 다시 보이는 게 자연스러움 - `clearReport`의
  정반대 동작).
- 컨트롤러: `AdminPostController`에 `/{id}/unclear-report`와
  `/{postId}/comments/{commentId}/unclear-report` 추가,
  `AdminCommentController`에 `/{id}/unclear-report` 추가,
  `AdminScheduleCommentController`에 `/{id}/unclear-report` 추가 —
  전부 기존 `clear-report` 엔드포인트 바로 옆에 같은 `ListState` 패턴
  (`@ModelAttribute`로 검색/필터/페이지 상태 hidden input 받아서 그
  상태로 리다이렉트)으로 만들어서 액션 후 목록이 1페이지로 리셋되는 일이
  없게 했다.
- 템플릿: `admin/post-detail.html`(게시물 자체 + 댓글 개별 항목 둘 다),
  `admin/comment-list.html`, `admin/schedule-comment-list.html`에
  `th:if="${x.reportCleared}"`일 때만 보이는 "문제없음 철회" 버튼 추가
  (기존 "문제없음 처리" 버튼은 반대로 `th:unless`라서 둘이 동시에 보이는
  일은 없음). `admin/report-list.html`(신고 관리 대시보드)과
  `admin/post-list.html`(게시글 목록)은 원래부터 읽기 전용(액션 버튼
  없이 상세로 링크만 나가는 구조, 5차 라운드에 문서화된 기존 설계
  결정)이라 여기엔 철회 버튼을 추가하지 않았다 — 일관성 유지.

**검증**: `./gradlew compileJava` 통과 → devtools 반영 → 브라우저로
`/mypage`에서 캘린더 버튼이 사라지고 "내가 쓴 글 관리"가 보이는 것 확인
→ `/mypage/activity` 3개 탭(게시글/댓글/한마디) 전부 실제 데이터로
렌더링되는 것 확인 → mysql로 admin 계정에 임시 소개글을 넣고
`/users/6`에서 표시되는 것 확인 후 다시 NULL로 원복 → `/mypage/edit`
폼에 소개글 textarea가 정상 렌더링되는 것 확인(총관리자 실제 비밀번호를
몰라서 실제 폼 제출까지는 안 하고 DB 직접 조작 + 읽기 경로로 검증) →
"문제없음 철회" 버튼: 댓글 하나(`post_comments.id=1`)를 mysql로
`report_cleared=1`로 만든 뒤 `/admin/comments`에서 버튼이 뜨는 것
확인 → 브라우저 클릭은 `confirm()` 다이얼로그가 자동화 환경에서
막혀서(이전 라운드들에도 기록된 브라우저 도구 한계) `fetch()`로 직접
POST 호출 → DB에서 `report_cleared`가 0으로 바뀐 것 확인(원래 값이라
그대로가 최종 상태) → 게시글(`posts.id=1`)과 한마디
(`schedule_comments.id=5`, 원래부터 `report_cleared=1`이던 기존
데이터)에도 같은 방식으로 왕복 테스트 후 각각 원래 값(0, 1)으로
정확히 복원됨을 확인.

**다음에 이어서 할 때 참고할 것**: 마이페이지 활동 관리 화면에서
댓글/한마디는 "삭제"만 가능하고 "수정"은 못 한다(위 설계 이유 참고) —
필요해지면 게시글 상세의 인라인 댓글 수정 UI를 마이페이지에서도 열 수
있게 postUuid를 이미 DTO에 담아뒀으니 "수정" 버튼을 `/posts/{uuid}#comment-{id}`
로 보내는 정도로 확장 가능. 한마디는 날짜별 URL이 없어서 원본으로
바로 가는 링크가 아예 없는 상태 — `calendar.js`가 날짜를 서버 쿼리
파라미터로 받게 바뀌면 그때 링크를 추가하면 됨.

### 향후 기능/인프라 로드맵 (사용자가 전부 진행하기로 확정한 백로그)

아래 16개 항목은 사용자가 "이것들 전부 할거야"라고 명시적으로 확정한
장기 작업 목록이다(2026-08-10). 우선순위나 착수 순서는 아직 정해지지
않았고, 각 항목을 시작할 때 이 프로젝트의 기존 패턴(소프트 삭제, 신고
3회 자동 블라인드, 익명성 보호, 관리자 권한 세분화 등)과 일관되게
설계할 것. 이미 부분적으로 구현된 항목은 괄호에 현재 상태를 적어둔다.

1. **게시글 검색 기능 구현** (부분 완료) — 커뮤니티 목록 검색/정렬/
   페이지당 개수는 3~4차 라운드에서 이미 구현됨(`PostRepository.search()`).
   추가로 풍부하게 만들 수 있는 부분: 태그, 날짜 범위 필터 등.
2. **관리자 기능 구현** (대부분 완료) — 신고/게시글/댓글/한마디/계정/
   권한 관리 화면 전부 존재. 신규 요구가 생기면 이 CLAUDE.md의 기존
   라운드 기록을 먼저 참고.
3. **게시글 및 댓글 신고 기능 구현** (완료) — 3명 서로 다른 사용자
   신고 시 자동 블라인드, 관리자 "문제없음"/철회 처리(6차 라운드)까지
   구현됨.
4. **좋아요 및 북마크 기능 구현** (미착수) — Post/PostComment에 좋아요
   테이블 신설 필요, 북마크는 User-Post 다대다 관계로 설계 예상.
5. **알림 기능 구현 (공지사항 포함)** (완료, 2026-08-12) — 댓글/좋아요/
   관리자 조치(블라인드/문제없음/계정 정지/권한 변경) 알림 + 공지사항
   게시판(`Post.Category.NOTICE`, 목록 상단 고정) 구현. 실시간 대신
   폴링 방식(20초 간격) 선택. 자세한 내용은 맨 아래 "2026-08-12 라운드"
   섹션 참고.
6. **계정 프로필 확인 기능 구현 (일반계정)** (완료) — 5차 라운드
   (`/users/{id}`, 최소 정보만) + 6차 라운드(소개글 추가)로 완성.
7. **계정정지 신고 기능 구현** (부분 완료) — `User.active` 필드로 총
   관리자의 수동 계정 정지는 이미 있음. "신고를 통한 계정정지"(콘텐츠
   신고가 아니라 사용자 자체를 신고해서 정지시키는 흐름)는 미구현 —
   새로운 UserReport 엔티티 + 누적 기준 설계 필요.
8. **소셜 로그인 기능 구현** (미착수) — Spring Security OAuth2 Client
   추가 필요(Google/카카오 등), 기존 로컬 계정과의 연동(같은 이메일
   병합 여부 등) 정책 결정 필요.
9. **이메일 인증 및 비밀번호 찾기 기능 구현** (미착수) — 현재 `User`
   엔티티에 이메일 필드 자체가 없음. 회원가입 흐름에 이메일 필드 추가
   + 인증 메일 발송(SMTP 연동) + 비밀번호 재설정 토큰 흐름 설계 필요.
10. **데이터베이스 운영 및 백업 환경 구축** (미착수) — 정기 백업
    스케줄(mysqldump 등), 복구 절차 문서화.
11. **로그 수집 및 모니터링 환경 구축** (미착수) — 현재 별도 로깅
    프레임워크/모니터링 스택 연동 없음(Spring Boot 기본 로그만).
12. **파일 저장소 분리 및 관리 환경 구축** (미착수) — 현재 게시글
    이미지가 로컬 `uploads/` 디렉토리에 저장됨(`PostImageService`
    참고) — S3 등 외부 스토리지로 분리 필요.
13. **클라우드 서버 환경 구축** (미착수) — 현재 로컬(Windows) 개발
    환경에서만 구동. 배포 대상 클라우드(AWS/GCP/네이버클라우드 등)
    선정 필요.
14. **HTTPS 적용 및 보안 설정 강화** (미착수) — 현재 CSRF도 개발
    편의상 꺼져 있음(`SecurityConfig`). 배포 전 CSRF 재활성화 + HTTPS
    인증서 적용 + 보안 헤더 점검 필요.
15. **CI/CD 자동 배포 환경 구축** (미착수) — 현재 Git 저장소 자체가
    없음(로컬 폴더로만 작업 중, 세션 요약 참고) — CI/CD를 붙이려면
    먼저 원격 저장소(GitHub 등)부터 필요.
16. **파일 저장소/클라우드/CI-CD 등 인프라 항목들은 순서상 서로 의존적**
    — 예: CI/CD(15번)는 원격 저장소가 먼저 있어야 하고, 클라우드
    서버(13번)가 있어야 HTTPS(14번) 인증서 적용이 의미가 있음. 착수
    순서를 정할 때 이 의존관계를 고려할 것.

---

## 2026-08-10 (7차 라운드) — 내 프로필 설정(소개글) 분리, 한마디 신고
표시 버그 조사, 좋아요/북마크 기능 시작

사용자 요청 원문(요약): "내 정보 수정 이외에 내 프로필 설정을 추가 —
상대방이 보이는 프로필을 사진처럼 변경"(스크린샷: 마이페이지 카드
아래에 소개글+게시글 보러가기 박스가 손그림으로 추가됨) / "오늘의
한마디는 신고를 당해도 관리자 대시보드에 표시되지 않는 버그 수정" /
"오늘의 한마디·게시글·댓글에 대해 좋아요 및 북마크 기능 구현을 시작".

**1) 내 프로필 설정(소개글) — "내 정보 수정"에서 분리**
- 6차 라운드에서는 소개글(bio)을 "내 정보 수정"(`/mypage/edit`, 아이디/
  비밀번호/학교 정보 폼)에 끼워 넣었는데, 사용자가 스크린샷으로
  "내 정보 수정과는 별개로" 소개글 전용 영역을 추가해달라고 명확히
  요청 — 그래서 소개글 편집을 완전히 분리했다.
  - `mypage-edit.html`에서 소개글 textarea 제거, `MyPageUpdateDto`에서
    `bio` 필드 제거, `UserService.updateProfile()`에서 bio 처리 로직
    제거(제거 안 하면 "내 정보 수정" 폼을 저장할 때마다 bio가 null로
    덮어써지는 버그가 났을 것 — 새 폼에 bio 필드가 없으니까).
  - `UserService.updateBio(username, bio)` 신설 — 현재 비밀번호
    재확인을 요구하지 않는다(계정 보안과 무관한 낮은 위험도 데이터라고
    판단, `updateProfile()`과의 차이점).
  - `AuthController`에 `GET/POST /mypage/profile` 추가,
    `templates/user/profile-edit.html` 신설(소개글 textarea 하나만 있는
    최소 폼).
- 스크린샷을 그대로 재현: `mypage.html`의 정보 목록(관심 학교/학년·반)
  아래에 `.mypage-profile-box`를 추가 — "{닉네임} 소개글" 헤더(연필
  아이콘 클릭 시 `/mypage/profile`로 이동) + 현재 소개글(없으면 "아직
  작성한 소개글이 없어요" 안내) + "{닉네임}이 쓴 게시글 보러가기"
  링크(`/users/{loginUser.id}`, 본인의 공개 프로필로 바로 이동).
- **검증**: 브라우저 클릭이 실제로는 요청을 안 보내는 문제를 겪음
  (`computer.left_click`이 좌표상 정확한 버튼 위치를 클릭했는데도
  네트워크 로그에 POST가 안 잡힘 — 이전 세션들에도 기록된 "브라우저
  창이 실제로 표시/합성되지 않을 때 클릭이 씹히는" 도구 한계로 추정,
  `form_input`으로 textarea 값은 정상적으로 채워졌던 것으로 봐서 DOM
  자체는 문제 없었음). `fetch()`로 같은 폼 데이터를 직접 POST해서
  우회 검증 → DB에 소개글 저장 확인 → `/mypage`와 `/users/6`(공개
  프로필) 양쪽에 정상 표시되는 것 확인 → 소개글을 다시 빈 값으로
  저장해 원상 복구.

**2) "오늘의 한마디 신고가 관리자 대시보드에 안 보인다" — 재현 실패**
- 코드 리뷰(`ScheduleCommentService.reportComment()` - `@Transactional`
  정상 확인, `ScheduleCommentRepository.findReportedOrBlindComments()` -
  `blind=true OR reportCount>0` 조건 정상 확인, `AdminScheduleCommentService.
  toSummaryDto()` - 필드 매핑 정상 확인)로는 결함을 찾지 못함.
- mysql로 `schedule_comments.report_count`를 직접 1로 만들고
  `/admin/reports?type=schedule`을 열어서 정상 노출되는 것 확인(1차
  재현 시도) → 혹시 실제 신고 API 경로(프론트 JS → 컨트롤러 → 서비스)
  자체에 문제가 있을까 싶어 회원가입(`/register`)으로 임시 테스트
  계정을 하나 만들고 curl로 로그인 → `POST /school/api/comments/5/report`
  실제 호출 → DB에 `report_count=1`로 정상 반영 → `/admin/reports?type=
  schedule`에 정상 노출되는 것까지 실제 API 경로로 재확인(2차, 가장
  신뢰도 높은 재현 시도) → **두 시도 모두 정상 동작해서 버그를 재현하지
  못함**. 테스트 계정/신고 데이터/한마디 상태(`report_count`,
  `report_cleared`)는 전부 원래 값으로 복구.
- 결론: 이 라운드 기준으로는 해당 버그를 재현하지 못했다. 다음에
  이어서 조사할 때 참고할 것 — 사용자가 겪은 상황이 (a) 특정 권한만
  가진 부관리자 계정에서 발생했는지(단, `/admin/schedule-comments`
  "전체" 탭도 동일한 상태 배지를 보여주므로 권한이 달라도 어딘가에서는
  보여야 정상), (b) 신고 후 페이지를 새로고침하지 않고 계속 보고
  있었는지(캐시/새로고침 문제일 가능성), (c) 정확히 "관리자 대시보드"가
  `/admin/reports`가 아닌 다른 화면을 가리키는 것인지 등을 사용자에게
  구체적인 재현 경로(어느 화면에서 신고했고 어느 화면에서 안 보였는지)
  를 물어보고 진행하는 게 좋다.

**3) 좋아요(♥)/북마크 기능 — 게시글·댓글·오늘의 한마디 전체 시작**
- `PostReport`/`CommentReport`/`ScheduleCommentReport`와 동일한 패턴으로
  콘텐츠 타입별 좋아요/북마크 테이블을 분리 신설했다(6개 신규 엔티티):
  `PostLike`/`PostBookmark`(post.domain),
  `CommentLike`/`CommentBookmark`(post.domain, PostComment 대상),
  `ScheduleCommentLike`/`ScheduleCommentBookmark`(school.domain). 각각
  `(대상_id, user_id)` 유니크 제약으로 중복 좋아요/북마크를 막고, 토글
  로직(있으면 삭제=취소, 없으면 생성=추가)은 서비스 레이어에서 처리.
  - 좋아요는 카운트를 각 부모 엔티티(`Post`/`PostComment`/
    `ScheduleComment`)의 새 `likeCount` 필드에 비정규화해서 저장 —
    목록/상세 조회 때마다 COUNT 쿼리를 따로 안 날리기 위함
    (`reportCount`와 동일한 기존 패턴).
  - 북마크는 카운트를 공개하지 않는 개인용 기능이라(다른 사람이 몇 명이
    북마크했는지 보여줄 필요가 없음 - "나중에 다시 보려고 저장"하는
    용도) 비정규화 카운트 없이 북마크 테이블 자체가 마이페이지 "북마크"
    탭의 조회 근거가 된다.
  - `ddl-auto: update`라 서버 재시작 시 `post_likes`/`post_bookmarks`/
    `comment_likes`/`comment_bookmarks`/`schedule_comment_likes`/
    `schedule_comment_bookmarks` 6개 테이블이 자동 생성됨(직접 DDL 안
    함, 재시작 후 `SHOW TABLES`로 확인).
- 서비스: `PostService.toggleLike()`/`toggleBookmark()`/
  `removeBookmark()`, `PostCommentService.toggleLike()`/`toggleBookmark()`,
  `ScheduleCommentService.toggleLike()`/`toggleBookmark()` — 전부
  동일한 패턴(대상 존재 확인 → 기존 좋아요/북마크 존재 여부 확인 →
  토글). `removeBookmark()`는 토글이 아니라 "항상 제거만" 하는 별도
  메서드로 분리했다 — 마이페이지 "북마크 해제" 버튼에 토글을 그대로
  재사용하면 이미 해제된 상태에서 다시 눌렀을 때 오히려 북마크가 다시
  켜지는 사고가 날 수 있어서 의도적으로 나눔.
- 컨트롤러: `POST /posts/{uuid}/like`, `/posts/{uuid}/bookmark`,
  `POST /posts/{postUuid}/comments/{commentId}/like`, `/bookmark`,
  `POST /school/api/comments/{id}/like`, `/bookmark` — 전부 토글 결과
  (`liked`/`likeCount` 또는 `bookmarked`)를 JSON으로 반환하는 AJAX
  엔드포인트(기존 신고 엔드포인트와 동일한 스타일).
- DTO: `PostListItemDto`/`PostDetailDto`/`PostCommentDto`/
  `ScheduleCommentDto`에 `likeCount`(목록/상세 공통) +
  `likedByMe`/`bookmarkedByMe`(상세·댓글에서 현재 로그인 사용자 기준
  버튼 초기 상태 판단용, `reportedByMe`와 동일한 패턴) 추가. 이 판단을
  위해 `PostLikeRepository`/`PostBookmarkRepository`(+댓글/한마디용)에
  `existsBy..._IdAndUser_Username`을 추가해서 `PostReportRepository.
  existsByPost_IdAndReporter_Username`과 동일하게 User를 따로 조회하지
  않고 바로 판단하게 했다.
- 프론트엔드:
  - 게시글 상세(`post/detail.html`): 신고 버튼 위에 좋아요(하트+카운트)/
    북마크 버튼 행 추가(`post-detail.js`가 클릭 시 토글 API 호출 후
    버튼 상태·카운트를 그 자리에서 갱신).
  - 댓글(게시글 상세 AJAX 렌더링, `post-detail.js` `renderComments()`):
    각 댓글 헤더에 작은 좋아요(하트+카운트)/북마크 아이콘 버튼 추가 —
    본인 댓글이든 남의 댓글이든 항상 노출(신고와 달리 자기 댓글도
    좋아요/북마크할 수 있게 허용, 자기 신고 금지와는 다른 정책).
  - 오늘의 한마디(캘린더 위젯, `calendar.js` `renderComments()`): 댓글과
    동일한 패턴으로 좋아요/북마크 아이콘 추가.
  - 커뮤니티 목록(`post/list.html`): 좋아요 수가 1 이상일 때만
    조회수 옆에 하트 아이콘+숫자 표시(0이면 안 보이게 해서 목록이
    지저분해지지 않게 함) — 목록에서는 버튼(토글)은 없고 표시만 함,
    상세로 들어가야 누를 수 있음.
  - 마이페이지: `/mypage/activity`에 4번째 탭 "북마크" 추가(게시글
    북마크만 목록화 — 댓글/한마디는 토글 자체는 되지만 목록 화면은
    이번엔 만들지 않음, 아래 "다음에 참고" 항목 참고), 각 항목에
    "북마크 해제" 버튼.
- CSS: `post.css`에 `.post-like-bookmark-row`/`.post-like-btn`/
  `.post-bookmark-btn`(게시글) + `.post-comment-like-btn`/
  `.post-comment-bookmark-btn`(댓글, active 상태일 때 하트는 빨강/
  북마크는 브랜드색으로 강조), `calendar.css`에 `.comment-like-btn`/
  `.comment-bookmark-btn`(기존 `.comment-edit-btn`/`.comment-report-btn`
  공유 셀렉터 그룹에 합류).
- **검증**: `./gradlew compileJava` 통과 → devtools 반영 → 서버 재시작
  후 6개 테이블 자동 생성 확인 → 게시글 상세에서 fetch로 좋아요/북마크
  토글 → 카운트 1 증가 + "북마크됨" 텍스트로 바뀌는 것 확인 → 페이지
  새로고침해도 상태가 유지되는 것 확인(DB 기반이므로 당연하지만 실제
  재조회 경로까지 검증) → 커뮤니티 목록에 하트+숫자가 뜨는 것 확인 →
  마이페이지 "북마크" 탭에 방금 북마크한 글이 뜨는 것 확인 → 댓글
  좋아요/북마크, 한마디 좋아요/북마크까지 전부 fetch로 API 레벨 검증
  (3개 콘텐츠 타입 × 2개 기능 = 6개 토글 전부 정상 동작, DB에 각각
  1건씩 생성되는 것도 확인) → 테스트 데이터를 다시 토글해서 원복하려는
  도중, **실제 사용자가 이미 배포된 기능을 자신의 브라우저에서 직접
  테스트하고 있는 것으로 보이는 정황 포착**(제가 만지지 않은 여러 다른
  게시글에 몇 초 간격으로 새 북마크 행이 계속 쌓임 - 세션 계정과 동일한
  admin 계정) → 실제 사용자 데이터를 덮어쓰지 않기 위해 이 시점부터
  추가적인 테스트/원복 조작을 중단함(제가 직접 만든 테스트 흔적 중
  명확히 구분 가능했던 것만 정리, 이후 것은 손대지 않음).

**다음에 이어서 할 때 참고할 것**:
- 댓글/한마디 북마크는 토글 API는 있지만 마이페이지에 목록 화면이
  없다(게시글 북마크만 `/mypage/activity?tab=bookmarks`로 조회 가능) —
  필요해지면 `CommentBookmarkRepository`/`ScheduleCommentBookmarkRepository`
  에 `findByUser_IdOrderByCreatedAtDesc(Pageable)`를 추가하고
  `MyActivityService`에 동일 패턴의 메서드를 붙이면 된다(이미 게시글
  버전이 정확히 그 패턴으로 구현돼 있음).
- 북마크한 게시글이 나중에 삭제/블라인드되면 마이페이지 북마크 목록에
  깨진 링크로 남을 수 있다(필터링 안 함) — 사용량이 늘면 처리 필요.
- 한마디 신고 미표시 버그는 이 라운드에서 재현하지 못했다 - 위 2번
  항목의 "다음에 참고" 내용대로 사용자에게 구체적 재현 경로를 확인하고
  이어갈 것.

---

## 2026-08-10 (8차 라운드) — 내 활동내역 개편(이름 변경/좋아요 탭/검색),
게시글 수정 링크, 한마디 북마크 누락 버그 수정

사용자 요청 원문(요약): "내가 쓴 글 관리를 내 활동내역으로 이름 바꾸는건
어때? 좋아요한 게시글도 조회 가능하게, 활동내역 페이지에 검색기능도
추가" → 이어서(같은 턴 중간에) "게시글 관리에서 삭제뿐 아니라 수정도
가능하게, 북마크 페이지에 북마크된 한마디가 안 보이는 버그 수정".

**1) 이름 변경 + 좋아요 탭 + 검색** — `/mypage/activity` 관련 화면 전부
"내가 쓴 글 관리" → "내 활동내역"으로 변경(`mypage.html` 링크,
`my-activity.html` 제목/헤딩, DTO 주석). 탭 구성이 게시글/댓글/한마디
"쓴 것" 위주에서 좋아요/북마크까지 아우르는 활동 이력 전반으로
넓어졌다는 걸 이름에 반영.
- 좋아요 탭: `PostLikeRepository.findByUser_IdOrderByCreatedAtDesc` 신설,
  `MyActivityService.getLikedPosts()`가 북마크 탭과 동일 패턴으로 처리.
  "취소" 버튼은 `PostService.toggleLike()`를 재사용하지 않고
  `removeLike()`를 새로 만들었다(`removeBookmark()`와 같은 이유 - 토글을
  재사용하면 이미 취소된 상태에서 다시 누를 때 오히려 좋아요가 켜지는
  사고 방지).
- 검색: 게시글/댓글/한마디/좋아요/북마크 5개 탭 전부 상단에 검색창
  추가. **이 라운드에서 조회 방식을 통째로 바꿨다** - 기존엔
  `Pageable`로 DB 레벨 페이지네이션을 하고 있었는데, 검색어 필터링을
  붙이려면 리포지토리에 커스텀 `@Query`를 탭마다 새로 짜야 해서, 대신
  관리자 목록 화면들과 동일한 패턴(전체 List 조회 → 메모리에서
  `matches()`로 필터링 → `PageUtils.paginate()`)으로 전환했다 - 본인
  소유 데이터만 대상이라 규모가 작다는 전제는 관리자 화면과 동일하게
  적용됨. 그래서 `PostRepository`/`PostCommentRepository`/
  `ScheduleCommentRepository`/`PostBookmarkRepository`/
  `PostLikeRepository`의 관련 메서드들을 `Page<T> ...(id, Pageable)`에서
  `List<T> ...(id)`로 시그니처를 바꿨다(사용처가 `MyActivityService`
  하나뿐이라 안전하게 교체 가능했음, 교체 전 `grep`으로 다른 사용처
  없는 것 확인).

**2) 게시글 탭에 "수정" 링크 추가** — 별도 수정 UI를 새로 만들 필요
없이 기존 `/posts/{uuid}/edit`(소유자 확인은 그 페이지의 기존 로직이
그대로 처리)로 바로 가는 링크만 삭제 버튼 옆에 추가. `mypage.css`에
`.my-activity-edit-btn` 신설(`.btn-outline-brand`는 별도 베이스 `.btn`
클래스가 없어서 그대로 쓰면 스타일이 하나도 안 먹는 걸 확인하고 직접
크기/색을 지정한 클래스로 만듦).

**3) "북마크 페이지에 한마디가 안 보인다" 버그 — 실제 존재하던 갭**
(7차 라운드의 "오늘의 한마디 신고 미표시" 버그와는 다른 건이다 - 이건
실제로 재현되고 원인도 명확한 진짜 버그였음). 원인: 7차 라운드에서
"북마크 시작" 범위를 게시글만으로 의도적으로 좁혀놨었는데(당시
CLAUDE.md에도 "필요해지면 확장"이라고 명시), 한마디도 좋아요/북마크
토글 자체(캘린더 위젯의 하트/북마크 아이콘)는 이미 되고 있어서 사용자
입장에서는 "북마크했는데 왜 마이페이지 북마크 목록엔 없지?"로 보이는
게 당연했다.
- `ScheduleCommentBookmarkRepository.findByUser_IdOrderByCreatedAtDesc`
  신설, `ScheduleCommentService.removeBookmark()`(토글 아닌 항상 제거,
  `PostService.removeBookmark()`와 동일 이유) 신설,
  `MyActivityService.getBookmarkedScheduleComments()` 추가.
- **"북마크" 탭 자체를 게시글/한마디 두 콘텐츠 타입을 다루는 서브탭
  구조로 재설계**했다(신고 관리 화면의 `type=comment/schedule` 패턴과
  동일) - 한 페이지에 두 목록을 동시에 페이지네이션하면 `page` 파라미터가
  어느 목록 기준인지 애매해지는 문제가 있어서, `type` 쿼리 파라미터로
  한 번에 하나의 서브탭만 보여주는 구조로 만들었다. 좋아요 탭은 이번엔
  한마디 서브탭을 추가하지 않았다(사용자가 북마크만 콕 집어 말함 -
  필요해지면 정확히 같은 패턴으로 확장 가능).

**검증**: `./gradlew compileJava` 통과 → devtools 반영 → 브라우저로
`/mypage/activity` 5개 탭 전부 렌더링 확인(제목 "내 활동내역", 검색창,
"수정"/"삭제" 버튼) → fetch로 게시글 제목 키워드 검색이 실제로
필터링되는 것 확인 → `/school/api/comments/5/bookmark` 토글로 한마디
북마크를 만들고 `/mypage/activity?tab=bookmarks&type=schedule`에 정상
노출되는 것 확인(버그 재현 및 수정 검증) → `type=post` 서브탭은 별개로
정상 작동(빈 목록) 확인 → 좋아요 탭에 실제 사용자가 좋아요한 게시글
2건이 이미 떠 있는 것 확인(7차 라운드에서 발견한 실사용자 활동 데이터,
그대로 유지) → 테스트로 추가했던 한마디 북마크 1건만 다시 토글해서
원복(실사용자 데이터는 건드리지 않음) → `/posts/{uuid}/edit` 수정
링크가 정상적으로 게시물 수정 폼을 여는 것까지 확인.

**다음에 이어서 할 때 참고할 것**: 좋아요 탭은 한마디 서브탭이 없다
(북마크 탭만 게시글/한마디 서브탭 구조) - 필요해지면
`ScheduleCommentLikeRepository.findByUser_IdOrderByCreatedAtDesc` +
`MyActivityService.getLikedScheduleComments()` + 좋아요 탭에도 동일한
서브탭 UI를 붙이면 된다(이번에 만든 북마크 탭 코드를 그대로 복붙하면
될 정도로 패턴이 동일함). 댓글 좋아요/북마크는 여전히 토글만 되고
마이페이지 목록 화면은 없음(7차 라운드부터 이어지는 기존 갭).

---

## 2026-08-11 라운드 — git 히스토리 복구(merge) + 캘린더 프론트엔드 전면
재작성(커스텀 그리드 + 사이드 패널)

**1) git 히스토리 사고 복구 (커밋/push 작업 중 발견)**

사용자가 "작업내용 확인해서 깃 init push해줘"라고 요청해서 상태를 확인해보니,
이 폴더(`webproject-main\webproject-main`)에 이미 `git init`이 최근에(2026-08-10
21:54) 새로 되어 있었고 origin도 연결돼 있었는데, **로컬 커밋이 1개뿐이고 그
1개가 origin/main(커밋 2개, 관리자 권한 체계 포함)과 히스토리가 전혀 이어지지
않는 unrelated history**였다(`git merge-base`가 공통 조상을 못 찾음). 로컬
`git init`이 실수로 기존 `.git`을 덮어썼거나 새 폴더에서 다시 초기화된 것으로
추정 — 원인 조사는 범위 밖이라 하지 않았다.

그냥 push하면 거부되고 `--force`로 밀면 origin에 이미 있던 관리자 권한 체계
작업이 통째로 사라지는 상황이라, 사용자에게 먼저 상황을 설명하고 진행 방식을
확인받았다("원격 기록 유지하며 병합 시도" 선택). `git merge origin/main
--allow-unrelated-histories`를 실행하니 **52개 파일이 add/add 충돌**났다(두
히스토리가 완전히 다른 시점의 스냅샷이라 대부분의 공유 파일이 충돌). 이 규모의
충돌은 파일마다 실제로 두 버전의 코드를 읽고 기능을 합쳐야 해서, 이해를 다른
곳에 위임하지 않고 병합 작업 자체를 백그라운드 에이전트에 맡긴 뒤(52개 파일을
전부 읽어서 판단해야 하는 기계적이지만 판단이 필요한 작업이라 컨텍스트 절약
목적) 결과를 직접 검증했다.

- **검증 결과**: 대부분의 파일에서 로컬(HEAD) 쪽이 이미 origin의 기능을 포함한
  상위집합이었다(로컬 마지막 커밋이 origin보다 늦은 시각이라 자연스러운 결과) —
  `AdminAccessInterceptor.java`처럼 겉보기엔 "관리자 권한" 전용 파일도 이미
  로컬에 그 기능이 들어있었다. `git diff HEAD origin/main`으로 origin이 로컬에
  없는 걸 추가한 부분이 실제로 있는지 파일별로 직접 대조해서, "그냥 ours로
  덮어써서 origin 쪽 내용이 조용히 사라진 것"이 아니라는 걸 확인한 뒤 병합
  커밋(`329cdbe`)을 만들고 `./gradlew.bat compileJava`로 빌드까지 통과하는 걸
  확인하고 나서야 사용자에게 최종 push 확인을 받았다.
- 병합은 `git merge`(rebase나 squash가 아님)라서 원래 두 히스토리(로컬 1개
  커밋 + origin 2개 커밋)가 전부 그대로 보존되고, 병합 커밋이 그 위에 새로
  얹힌 것뿐이다 — 사용자가 "원래 있던 커밋은 남아있는거지?"라고 재확인해서
  `git log --all --graph`로 직접 보여줬다.
- **다음에 이어서 할 때 참고할 것**: 왜 로컬에 `.git`이 새로 초기화돼 있었는지
  원인은 조사하지 않았다 — 비슷한 일이 또 생기면(다른 폴더 사본에서 작업하다가
  일부만 복사해왔다거나) 이번처럼 `git merge-base`로 히스토리가 이어지는지부터
  확인하고 진행할 것. 이 프로젝트는 여러 폴더 사본이 존재해온 이력이 있어서
  (0번 항목 참고) 이런 사고가 재발할 가능성이 낮지 않다.

**2) 캘린더 프론트엔드 전면 재작성**

사용자가 별도로 준비해둔 Figma 디자인 참고 자료(`C:\Users\jjhjj\OneDrive\Desktop\
Main site index.html layout\`, React/shadcn 코드 번들)를 읽고 캘린더 페이지의
프론트엔드를 그 디자인에 맞춰 다시 작성해달라고 요청함. 이 폴더의
`src/imports/calendar.{html,css,js}`는 참고 자료 대상이 아니라 Figma 임포트
도구가 **기존 사이트 파일을 그대로 복사**해온 것이었다(diff로 줄바꿈 문자
차이(LF vs CRLF)만 빼면 100% 동일한 것 확인) — 실제 새 디자인은
`src/app/App.tsx`의 `CalendarPage`/`DayDetail` React 컴포넌트에 있었다.

- **디자인 방향 변경 사항**: FullCalendar(CDN 라이브러리) 기반 그리드 + 클릭 시
  뜨는 **팝업 모달**이었던 기존 구조를, **직접 구현한 42칸(6주) 커스텀 그리드**
  + 날짜 클릭 시 그리드 옆(데스크톱)/아래(모바일)에 나타나는 **사이드 상세
  패널**로 교체. 컨트롤 바(학교 검색+학년/반)와 월 이동 바를 카드형 반투명
  배경(`backdrop-filter: blur`)으로 재스타일링, 하단에 오늘/선택됨 범례 추가.
- **의도적으로 구현하지 않은 것**: 참고 디자인은 그리드의 각 날짜 칸에 학사일정
  배지·급식 있음 점(월 전체를 한 번에 보여주는 "한눈에 보기" 정보)까지
  그려져 있었는데, 이건 목업 데이터(`SCHOOL_EVENTS`/`MOCK_MEALS` 하드코딩)로
  만든 것이고 실제 백엔드(`SchoolController`/`SchoolService`)는 **날짜 하나를
  클릭했을 때만** NEIS 학사일정/급식/시간표를 조회하는 구조라 월 전체 데이터를
  한 번에 내려주는 API가 없다. 이걸 만들려면 `CalendarEventRepository`/
  `MealRepository`에 기간 조회 쿼리를 추가하고 새 엔드포인트를 만드는
  백엔드 작업이 필요해서, "프론트엔드만 다시 작성해달라"는 요청 범위를 벗어난다고
  판단해 뺐다 — 범례도 "오늘"/"선택됨" 2개만 남기고 실제로 구현하지 않은
  "학사일정"/"시험"/"공휴일"/"급식 있음" 항목은 넣지 않았다. **월 단위 그리드에
  일정/급식 표시를 실제로 보여주고 싶으면 이 백엔드 작업부터 다음 후보로
  고려할 것.**
- **재사용/보존한 것**: 오늘의 한마디(날짜별 댓글) 조회/작성/수정/삭제/신고/
  좋아요/북마크 로직은 API·DOM 구조(`#commentList`/`#commentForm`/
  `#commentInput`)가 그대로라 `calendar.js`의 관련 함수들을 사실상 그대로
  재사용했다 — 신규 위험 없이 기존 기능이 그대로 동작함. 학교 검색/학년-반
  선택 위젯(`school-search.js`/`class-select.js`/`grade-select.js`)도 동일한
  DOM id로 그대로 연결. 오늘/선택 강조 애니메이션 키프레임(`day-highlight-pop`/
  `day-selected-ring-pulse`)도 클래스명만 `.fc-day-*` → `.cal-day-*`로 바꿔
  그대로 재사용.
- 변경 파일: `templates/school/calendar.html`, `static/css/calendar.css`,
  `static/js/calendar.js` (자바 코드/DB 스키마 변경 없음 — 순수 프론트엔드
  작업이라는 요청 범위 그대로).

**검증**: `./gradlew.bat compileJava processResources` 통과 → `bootRun`으로
서버 기동(8888) → 브라우저로 admin 로그인 후 `/school/calendar` 접속 →
`get_page_text`로 42칸 그리드가 올바른 앞뒤 달 날짜로 채워지는 것 확인 →
날짜 클릭 시(오늘=여름방학 특수일, 5월 12일=평일) 패널이 열리고 실제 NEIS
데이터(학사일정 배지/급식/시간표 7교시)가 정상 렌더링되는 것 확인 → 테스트
댓글 작성 → 좋아요/북마크 토글(active 상태·카운트 갱신 확인) → 삭제까지
전체 왕복 확인(테스트 흔적 정리 완료) → 패널 닫기 버튼으로 그리드가 1열로
복귀하는 것(`cal-grid-main--split` 클래스 제거) 확인 → `resize_window`로
모바일(375px) 뷰포트에서 가로 스크롤 없이 패널이 전체 폭으로 쌓이는 것까지
확인. 콘솔 에러 없음.

**다음에 이어서 할 때 참고할 것**: 월 그리드에 학사일정/급식 표시를 추가하려면
위 "의도적으로 구현하지 않은 것" 항목의 백엔드 작업부터 시작할 것. 이 라운드
시작 시점에 발견한 git 히스토리 문제(1번 항목)는 병합으로 해결됐지만 원인
자체는 미상이니, 앞으로 이 프로젝트를 여러 폴더에서 동시에 열어 작업하는 걸
피하거나 세션 시작 시 `git status`/`git log`를 습관적으로 먼저 확인할 것.

**3) 캘린더 월 그리드에 "OO주간" 학사일정 배지 추가**

바로 위 2번 항목에서 "월 전체 데이터를 한 번에 내려주는 API가 없어서 뺐다"고
적어둔 지 얼마 안 돼서, 사용자가 "특정한 일정만 가져올 수 있어? 가져와서
**주간 이런거면 표시해주는 기능 만들자"라고 바로 그 백엔드 작업을 요청함 —
전체 학사일정이 아니라 **이름에 "주간"이 포함된 것만** 걸러서 가져오는 걸
명시적으로 원함(기말고사 하루 같은 단발성 일정까지 다 보여주면 그리드가
지저분해지는 걸 우려한 것으로 보임).

- `NeisApiService.fetchEventsInRange(atptCode, schoolCode, fromYmd, toYmd, keyword)`
  신규 — `SchoolSchedule` NEIS API를 `AA_FROM_YMD`/`AA_TO_YMD`(기간 조회)로 호출하고,
  `keyword`(기본 "주간")가 이름에 포함된 항목만 걸러서 `CalendarEventDto`(date/
  eventName) 리스트로 반환. 기존 `fetchEventFromNeis()`(단일 날짜, 구버전 문자열
  자르기 파싱)는 그대로 두고 별도 메서드로 추가 — 새 NEIS 연동은 Jackson 파싱을
  따르라는 3번 항목 가이드대로 `searchSchools()`/`fetchClassList()`와 같은 스타일로
  작성함.
- `SchoolService.getMonthlyEvents(atptCode, schoolCode, year, month, keyword)` 신규 —
  프론트 `buildCalendarDays()`와 정확히 동일한 계산(그 달 1일이 속한 주의 일요일부터
  42일)으로 범위를 구해서 `fetchEventsInRange()`를 호출한다. 그리드가 이전/다음 달
  날짜로 앞뒤를 채우기 때문에, 딱 그 달만 조회하면 "학부모 상담 주간"처럼 달 경계에
  걸친 일정이 그리드 끝부분에서 배지 없이 잘려 보이는 문제가 생겼을 것 — 실제로
  검증 중 8월 마지막 주~9월 초에 걸친 일정으로 이 케이스를 확인했다.
  `SchoolController`에 `GET /school/api/calendar-events?atptCode=&schoolCode=&year=&
  month=&keyword=(기본 "주간")` 추가.
- 프론트(`calendar.js`): `loadMonthEvents()`가 월 전환/학교 선택 변경 시마다 이
  API를 호출해서 `currentMonthEventMap`(날짜→일정명)을 갱신하고,
  `applyMonthEventChips()`가 그리드의 해당 `.cal-day` 칸에 작은 배지
  (`.cal-day-event-chip`)를 붙인다. `renderGrid()` 끝에서도 캐시된 맵으로 다시
  적용해서(날짜 클릭 등으로 그리드가 다시 그려질 때 배지가 사라지지 않게) 재조회
  없이 유지되도록 함. 범례에도 "주간 일정" 항목 추가.
- 변경 파일: `NeisApiService.java`, `SchoolService.java`, `SchoolController.java`,
  신규 `dto/CalendarEventDto.java`, `calendar.js`, `calendar.css`, `calendar.html`.

**검증**: `./gradlew.bat compileJava` 통과 → devtools 반영 → 브라우저(admin 로그인,
아산배방중학교)로 `/school/api/calendar-events?year=2026&month=8` 직접 호출해서
"학부모 상담 주간"(2026-08-31~09-04, 실제 NEIS 데이터)이 정확히 5건 반환되는 것
확인 → 그리드에서 8월 마지막 줄(31일)과 9월로 넘어간 뒤에도 1~4일 칸에 동일한
배지가 정상적으로 유지되는 것 확인 → 9월 1일 클릭 시 상세 패널의 학사일정
배지("학부모 상담 주간")와도 내용이 일치하는 것까지 확인, 콘솔 에러 없음.

**바로 이어서(같은 턴)**: 사용자가 "지금 **주간만 가져오는거 같은데 특수하게
일이 정해진 날 모두 저렇게 표시해줘"라고 범위를 넓혀달라고 요청 — "주간"류만이
아니라 시험/휴업일/공휴일 등 학사일정에 잡힌 날은 전부 배지로 보여달라는 것.
`SchoolController`의 `keyword` 기본값을 `"주간"` → `""`(빈 문자열, 필터 없음)로
바꾸고, `NeisApiService.fetchEventsInRange()`가 원래부터 keyword가 비어있으면
필터링하지 않도록 짜여 있어서(`if (keyword != null && !keyword.isBlank() && ...)`)
백엔드 로직 자체는 건드릴 필요 없이 컨트롤러 기본값 한 줄만 바꾸면 됐다. `keyword`
파라미터 자체는 나중에 "특정 종류만 보기" 같은 요구가 생기면 쓸 수 있도록 옵션으로
남겨둠. 범례 라벨도 "주간 일정" → "학사일정"으로 갱신. **검증**: 8월 그리드에서
여름방학(장기간)/토요휴업일(매주)/광복절/대체공휴일/학부모 상담 주간까지 실제
NEIS 데이터가 전부 배지로 뜨는 것을 `get_page_text`로 확인, 콘솔 에러 없음.

**한 번 더 이어서**: 사용자가 "토요일 휴업일 재외 이어지는 일정은 1줄로 이어서
표시 색 다르게 표시"라고 요청 — 여러 날짜에 걸친 일정(여름방학, 학부모 상담
주간 등)을 각 칸마다 따로 배지 반복이 아니라 **하나의 색깔 띠처럼 이어 붙여서**
표시하고, 일정마다 **다른 색**을 쓰되, 매주 반복되는 **토요휴업일은 이 처리에서
제외**(그대로 단순 회색 배지)해달라는 것 — 안 그러면 매주 뜨는 토요휴업일이
너무 눈에 띄어서 정작 중요한 방학/시험/행사 기간이 묻히기 때문으로 보임.

- `calendar.js`의 `applyMonthEventChips()`를 재작성: 같은 주(그리드 행) 안에서
  좌우로 인접한 칸이 **같은 일정명**이면 하나로 이어진 것으로 보고, 시작/끝 칸만
  모서리를 둥글게(`.cal-day-event-bar-start`/`-end`), 중간 칸들은 각지게 해서
  `.cal-day` 좌우 padding(6px)을 음수 마진으로 상쇄(`margin: 0 -6px`)해 칸
  경계까지 꽉 채운다. 추가로 이어지는 칸 사이의 회색 경계선도 `border-right-color:
  transparent`로 지워서 진짜 끊김 없는 띠처럼 보이게 함. 이름은 시작 칸에만
  표시(중간/끝 칸은 색깔 띠만 이어짐 - 여러 번 반복 안 함).
  일정명별 색은 7색 팔레트(`EVENT_COLOR_PALETTE`)에서 이 달에 등장하는 순서대로
  고정 배정(같은 일정은 항상 같은 색). `SATURDAY_CLOSURE_NAME = '토요휴업일'`은
  이름이 정확히 일치하면 색 배정/연결 로직을 전부 건너뛰고 기존
  `.cal-day-event-chip`(회색조 단일 배지)로만 표시.
- 매주가 그리드 행 경계이므로, 여러 주에 걸친 일정(예: 여름방학)은 주가 바뀔
  때마다 새로 시작하는 띠로 자연스럽게 끊긴다 — 이번 검증에서 실제로 7/26(일)
  시작 띠가 7/31(금)에서 끊기고(토요일은 "토요휴업일"이라 이름이 달라 자동으로
  끊김), 8/2(일)부터 새 띠가 다시 시작하는 것으로 확인됨(별도 "주 경계 처리"
  코드 없이 인접 비교 로직만으로 자연스럽게 해결됨).
- CSS: `calendar.css`에 `.cal-day-event-bar`/`-start`/`-end` 추가(색상 자체는
  팔레트에서 JS가 인라인 스타일로 지정, CSS는 형태/여백/모서리만 담당).

**검증**: 8월 그리드에서 여름방학이 주 단위로 끊기며 이어진 인디고색 띠로,
학부모 상담 주간(8/31~9/4)이 초록색 띠로, 광복절/대체공휴일이 각각 단일 칸
빨강 계열 배지로, 토요휴업일은 매주 그대로 회색 배지로 뜨는 것을 DOM
구조(`cal-day-event-bar`의 start/end 플래그, 배경색, 인접 칸 경계선 색)까지
직접 찍어서 확인. 8/15(토, 광복절)을 클릭해도 상세 패널이 정상 동작하는 것도
재확인, 콘솔 에러 없음.

**세 번째로 이어서**: 사용자가 스크린샷과 함께 "토요일 휴업일은 표시하지 않아
모든 선의 크기를 키워서 같은 크기로 만들어줘", 그리고 같은 턴 중간에 "겹치는
부분은 위아래로 잘 낑겨줘"라고 추가 요청.

- **토요휴업일 완전히 숨김**: 이전엔 회색 배지로라도 표시했는데, 이제
  `applyMonthEventChips()`에서 `name === SATURDAY_CLOSURE_NAME`이면 아무것도
  렌더링하지 않고 그냥 건너뛴다(칸이 비어 보임). 안 쓰게 된 `.cal-day-event-chip`
  CSS 클래스는 완전히 제거함(더 이상 아무 데서도 안 만드므로 죽은 코드 정리).
- **모든 띠를 같은 크기로**: 원래는 진짜 시작/끝 칸만 살짝 안쪽으로 들여서
  둥글게(패딩+마진 보정) 하루짜리 단일 이벤트가 연결된 여러 날짜 이벤트보다
  좁아 보이는 문제가 있었다. `.cal-day-event-bar-start`/`-end`에서 그 인셋(
  `margin-left/right: 0; padding-left/right: 6px;`)을 제거하고, **모든** 띠가
  항상 칸 폭 전체(`margin: 0 -6px`)를 꽉 채우도록 통일했다 — 시작/끝 칸은
  모서리만 살짝 둥글게(`border-radius`) 캡을 씌우고 폭 자체는 다른 칸과 동일함.
  실제로 브라우저에서 모든 배지의 `getBoundingClientRect().width`가 칸 너비와
  거의 동일(120px vs 121px, 1px는 테두리)한 것으로 확인. 두께도 살짝
  키움(`padding: 2px 6px` → `4px 6px`, `font-size: 0.64rem` → `0.68rem`).
- **위아래 겹침 방지**: `.cal-day`의 `gap`을 4px → 6px, `min-height`를
  92px → 98px로 늘려서 날짜 숫자 배지(특히 "오늘" 노란 배경)와 그 아래 학사일정
  띠 사이에 여유 공간을 확보 — 실제로 각 칸의 숫자 배지와 띠의
  `getBoundingClientRect()`를 비교해서 겹치지 않는 것(`overlap: false`)을
  전 칸에서 확인함.

**검증**: 8월 그리드에서 토요휴업일 5개 날짜(8/1, 8/8, 8/22, 8/29, 9/5)가 전부
아무것도 안 뜨는 빈 칸으로 바뀐 것 확인 → 남은 모든 학사일정 띠(여름방학 연속
구간, 학부모 상담 주간, 광복절, 대체공휴일 등)의 렌더링 폭을 전부 찍어봐서
칸 너비와 동일하게 통일된 것 확인 → 날짜 숫자와 띠가 겹치지 않는 것 확인,
콘솔 에러 없음.

---

## 2026-08-12 라운드 — 알림 기능 + 공지사항 게시판 구현 (✅ 완료)

사용자 요청: "알림 기능 구현 공지사항 포함 구현시작하자" — 로드맵(위 "향후
기능/인프라 로드맵") 5번 항목을 그대로 착수. 별도 설계 확인 없이 로드맵에
이미 적혀있던 방향(폴링 방식, `Post.Category`에 공지 카테고리 추가, 상단
고정 노출)대로 바로 구현.

**1) 신규 `notification` 패키지** (`domain`/`repository`/`service`/`dto`/`controller`,
`post`/`user`/`school`과 동일한 계층 구조)
- `Notification` 엔티티: `recipient`(User FK), `type`(COMMENT/LIKE/
  REPORT_ACTION/ACCOUNT/ANNOUNCEMENT), `message`, `link`(클릭 시 이동할 경로,
  없으면 알림 목록에 머무름), `read`, `createdAt`.
- **버그(빌드 후 발견)**: `read`라는 자바 필드명을 컬럼명으로 그대로 썼더니
  `read`가 MySQL 예약어라서 Hibernate가 생성한 `CREATE TABLE notifications`
  구문이 조용히 실패 — `ddl-auto=update`가 개별 DDL 실패를 무시하고 넘어가서
  앱은 정상 기동됐지만 `notifications` 테이블 자체가 안 만들어짐(로그인 후
  모든 페이지에서 알림 개수를 조회하려 시도하면 터질 수 있는 상황이었음).
  `@Column(name = "is_read")`로 컬럼명만 분리해서 해결 — 자바 필드/getter/
  setter는 `read`/`isRead()`/`setRead()` 그대로 유지. **교훈**: 이 프로젝트에서
  엔티티 필드명을 정할 때 SQL 예약어(read/order/group/key/date 등)와 겹치는지
  한 번씩 의식할 것 — `ddl-auto=update`는 이런 실패를 조용히 삼킨다(9번 버그
  항목의 enum 확장 실패와 같은 계열의 함정).
- `NotificationService`: `notify()`(단순 생성), `notifyIfNotSelf()`(행위자
  본인이 알림 대상이면 스킵 - 자기 글에 자기가 댓글/좋아요 다는 경우 방지),
  `broadcastAnnouncement()`(공지 작성 시 탈퇴하지 않은 전체 사용자에게, 작성자
  본인 제외하고 일괄 생성), `getPage()`/`getUnreadCount()`/`markRead()`/
  `markAllRead()`.
- `NotificationController`: `GET /notifications`(목록, 페이지네이션),
  `POST /notifications/{id}/read`(읽음 처리 후 알림의 `link`로 리다이렉트,
  링크 없으면 목록에 머무름), `POST /notifications/read-all`, `GET
  /notifications/unread-count`(JSON, 네비바 배지 폴링용 - 비로그인이면 0).
  `/notifications/**`는 `SecurityConfig`의 `anyRequest().authenticated()`에
  자동으로 걸려서 별도 매처 추가 안 함.

**2) 알림이 발생하는 지점 (기존 서비스에 훅 추가)**
- `PostCommentService.createComment()` — 댓글/답변 작성 시 게시글 작성자에게
  알림(본인 글에 본인 댓글이면 스킵). 익명 게시물(`ANONYMOUS`)이면 "답변",
  아니면 "댓글"로 문구만 다르게(기존 UI 명칭 규칙과 동일).
- `PostService.toggleLike()` / `PostCommentService.toggleLike()` — 좋아요
  누를 때(취소 시엔 알림 안 보냄)만 게시글/댓글 작성자에게 알림.
- `PostService.reportPost()` / `PostCommentService.reportComment()` — 신고
  누적으로 **처음** 블라인드 전환되는 순간에만 작성자에게 알림(이미 블라인드인
  글에 추가 신고가 들어와도 매번 알림 가지 않도록 `wasBlind` 플래그로 가드).
- `AdminPostService` — 관리자의 블라인드/블라인드 해제, "문제없음" 처리(게시글
  `clearReport`/댓글 `clearCommentReport`) 시 대상 작성자에게 알림. "문제없음
  철회"(`unclearReport`/`unclearCommentReport`)는 재검토 중간 상태라 판단해
  알림 없이 조용히 처리(기존 신고 카운트 로직과 동일하게 비대칭 설계).
- `AdminUserService` — `setRole()`(승격/강등), `deactivateUser()`(정지 -
  로그인 자체가 막히므로 `link`를 `null`로 둬서 다시 로그인했을 때만 확인
  가능), `activateUser()`(재활성화) 시 대상 계정에게 알림.
- **부수 버그 수정**: `PostService.isAdmin()`/`PostCommentService.isAdmin()`이
  `role == ROLE_ADMIN`만 확인해서, 총관리자(`ROLE_SUPER_ADMIN`)가 일반
  커뮤니티 화면(`/posts/{uuid}`)에서 블라인드된 글의 원본을 못 보고 관리자
  페이지를 거쳐야 하는 문제가 있었음(공지사항 작성 권한 체크를 추가하다가
  발견) — `User.isAdmin()`(두 역할 다 포함하는 헬퍼, 이미 엔티티에 있었음)에
  위임하도록 수정. 브라우저로 총관리자 계정이 `/posts/{uuid}`에서 블라인드
  게시물 원본을 직접 보는 것까지 확인.

**3) 공지사항(`Post.Category.NOTICE`) 게시판**
- `Post.Category`에 `NOTICE("공지")` 추가 - 이번엔 `ddl-auto=update`가
  `posts.category` enum에 `NOTICE`를 알아서 잘 추가함(`role` enum 확장 실패
  사례와 달리 - 컬럼이 이미 있고 값만 늘어나는 케이스라 잘 되는 듯, 그래도
  매번 `DESCRIBE`로 확인하는 습관은 유지할 것).
- 작성 권한: `PostService.createPost()`/`updatePost()`에서 `category ==
  NOTICE`면 작성자가 `isAdmin()`이 아닐 때 예외 - `post/form.html`의 카테고리
  라디오도 `sec:authorize="hasAnyRole('ADMIN','SUPER_ADMIN')"`로 일반 사용자
  에게는 아예 안 보이게 숨김(AdminUserService와 동일한 "화면에서도 막고
  서비스에서도 한 번 더 막는" 이중 방어 패턴).
- 공지 작성 성공 시 `NotificationService.broadcastAnnouncement()` 호출 →
  탈퇴하지 않은 전체 사용자(작성자 본인 제외)에게 `ANNOUNCEMENT` 알림.
- 상단 고정 노출: `PostRepository.findTop5ByCategoryAndDeletedFalseAndBlindFalse
  OrderByCreatedAtDesc()` 신설, `PostController.list()`가 "전체" 탭 + 검색어
  없음 + 첫 페이지일 때만 `pinnedNotices`를 모델에 추가 → `post/list.html`
  최상단에 압정 아이콘 배지로 별도 렌더링(일반 페이지네이션 목록과는 별개 -
  같은 공지가 카테고리 필터 없는 일반 목록에도 최신순으로 자연스럽게 다시
  나타나는 건 의도된 동작, 일반 포럼의 "고정 + 최신" 패턴과 동일).
- `post/list.html`에 "공지" 탭 추가(전체/자유/익명/질의응답/공지).

**4) 네비바 종 아이콘 + 알림 목록 페이지**
- `GlobalModelAdvice`에 `unreadNotificationCount` `@ModelAttribute` 추가 -
  `loginUser`와 동일한 패턴으로 매 요청마다 계산되지만, 비로그인이면 DB 조회
  없이 바로 0 반환.
- `fragments/navbar.html`을 `<nav>` 하나짜리 fragment에서 `th:block`으로
  감싸서 `<nav>` + `<script>`(로그인 상태일 때만)를 함께 반환하도록 변경 -
  `th:replace`는 매치된 요소 하나만 가져오므로, 폴링 스크립트를 모든 페이지에
  공통으로 실어 보내려면 이 방법이 필요했음.
  - 종 아이콘(`/notifications` 링크) + 배지(`#notificationBadge`, 초기값은
    서버 렌더링, 이후 `notification.js`가 20초 간격으로
    `/notifications/unread-count`를 폴링해서 갱신 - "실시간 또는 폴링" 중
    폴링 선택, 이 프로젝트 규모에 WebSocket까지는 과하다고 판단).
- `templates/notification/list.html` 신설 - 알림 한 줄 한 줄이 `POST
  /notifications/{id}/read`로 제출되는 `<form>`(버튼처럼 스타일링, 클릭하면
  읽음 처리 후 알림의 `link`로 자동 리다이렉트) - 이 프로젝트가 관리자
  액션들에서 이미 쓰던 "폼 전체가 버튼" 패턴을 그대로 재사용해서 별도 JS 없이
  서버 렌더링만으로 동작. "모두 읽음 처리" 버튼(`POST /notifications/read-all`)
  도 동일 패턴. 유형별 아이콘(댓글/하트/방패/톱니/압정), 안 읽은 항목은 왼쪽에
  점 표시 + 옅은 배경.
- 신규 CSS: `notification.css`(목록 페이지), `navbar.css`에 `.site-nav-bell*`
  추가. 신규 JS: `notification.js`(배지 폴링만 담당, `setInterval` 20초).

**검증**: `./gradlew compileJava`(에러 없음) → `./gradlew compileJava
processResources -q`로 devtools 핫리로드 → `posts.category` enum에 `NOTICE`가
자동으로 추가된 것까지는 확인했으나 `notifications` 테이블이 안 보여서
위 1)의 `read` 예약어 버그를 발견/수정 → 재빌드 후 `DESCRIBE notifications`로
`is_read` 컬럼과 함께 테이블 생성 확인. 브라우저(Claude_Browser)로:
- admin 로그인 → `/posts/new`에서 "공지" 라디오가 보이는 것 확인 → 공지 작성
  → 커뮤니티 목록 최상단에 압정 배지로 고정 노출되는 것 확인.
- user1 로그인 → 종 배지에 "1" 표시 확인(`#notificationBadge`의 `textContent`/
  `className` 직접 조회) → `/notifications`에서 공지 알림 확인 → 공지 글에
  댓글 작성.
- admin으로 재로그인 → 종 배지 "1" → `/notifications`에서 "user1님이 회원님의
  글에 댓글을 남겼어요" 알림 확인 → 클릭 시 읽음 처리되고 원문 게시글로
  리다이렉트되는 것 확인 → "모두 읽음 처리" 클릭 후 배지가 0/`is-hidden`으로
  바뀌는 것 확인.
- admin으로 `/admin/posts/14`(user1의 질의응답 테스트글)를 블라인드 처리
  (fetch로 직접 POST - `confirm()` 다이얼로그가 이 브라우저 자동화 환경에서
  안 먹히는 기존에 기록된 한계, 3차 라운드 참고) → **총관리자 계정으로
  `/posts/{uuid}`에 직접 접속해 블라인드된 글 원본이 보이는 것 확인**(위
  `isAdmin()` 버그 수정 검증) → user1으로 로그인해 "관리자에 의해 블라인드
  처리되었습니다" 알림 수신 확인 → 다시 admin으로 unblind 처리해 원상 복구.
- 테스트로 만든 공지 게시물은 확인 후 소프트 삭제로 정리(기존 세션들의
  "테스트 게시글은 확인 후 삭제해둔다" 관례 유지), 나머지 시드 데이터(user1~5,
  카테고리별 테스트 게시글 9개)는 그대로 보존.

**다음에 이어서 할 때 참고할 것**: 좋아요 취소/댓글 삭제/신고 철회 같은
"되돌리는" 액션에는 의도적으로 알림을 보내지 않았다(문제없음 철회도 동일) -
알림 목록이 "취소했다는 알림"으로 시끄러워지는 것보다 조용한 게 낫다고 판단한
설계 선택이라, 나중에 사용자가 다르게 요구하면 그때 바꾸면 됨. 계정
승격/강등/정지/재활성화 알림은 로드맵 원문에 명시되진 않았지만 "관리자
조치"의 자연스러운 확장으로 포함시켰다 - 범위가 과하다고 판단되면 쉽게 뺄 수
있음(`AdminUserService`의 `notificationService.notify(...)` 호출 4곳만
제거하면 됨).

---

## 2026-08-12(2차) 라운드 — 좋아요 탭에 "오늘의 한마디" 누락 버그 수정, index 페이지 장식 요소 정리 (✅ 완료)

사용자 요청: "내 활동내역에 좋아요부분 오늘의 한마디가 안보여" + index
페이지 스크린샷 5장과 함께 "index 페이지 수정하자 / 마지막 사진은 보이는
흑점을 안보이게 수정하고 나머지 사진에 나온부분은 삭제하고 다른거로
대체하거나 없에줘".

**1) 마이페이지 "내 활동내역" → 좋아요 탭에 한마디 좋아요 목록이 없던 버그**
- 코드에 이미 주석으로 남아있던 기존 한계였음(`MyActivityService.
  getLikedPosts()` 바로 위 주석 "댓글/한마디 좋아요는 북마크와 마찬가지로
  이 라운드에서는 목록 화면을 만들지 않음") - 좋아요 토글 자체는
  `ScheduleCommentService.toggleLike()`로 이미 되는데, 그걸 모아보는 화면이
  없어서 좋아요한 한마디를 다시 찾을 방법이 없었다. **북마크 탭이 이미
  게시글/한마디 서브탭 구조로 되어있었던 것과 정확히 같은 패턴으로 좋아요
  탭도 확장**해서 해결.
- `ScheduleCommentLikeRepository.findByUser_IdOrderByCreatedAtDesc()` 신설
  (`ScheduleCommentBookmarkRepository`와 동일 패턴).
- `ScheduleCommentService.removeLike()` 신설 - 토글이 아니라 "취소" 버튼
  전용의 멱등 제거 동작(`PostService.removeLike()`/기존 `removeBookmark()`와
  동일한 이유로 분리).
- `MyActivityService.getLikedScheduleComments()` 신설.
- `MyActivityController`: `likes` 탭도 `bookmarks` 탭과 동일하게
  `type=post`/`type=schedule` 서브탭 파라미터를 받도록 변경, 신규
  `POST /mypage/activity/likes/schedule/{id}/remove` 엔드포인트 추가.
- `user/my-activity.html`: 좋아요 탭에 북마크 탭과 동일한 서브탭 UI(게시글/
  오늘의 한마디) + 한마디 좋아요 목록 블록 추가.
- **검증**: 브라우저(admin 계정, 기존에 좋아요해둔 한마디 데이터 있음)로
  `/mypage/activity?tab=likes&type=schedule`에서 한마디 2건이 정상적으로
  뜨는 것 확인, `type=post`(기본값)도 여전히 정상 동작(빈 상태 문구) 확인.

**2) index 페이지 장식 요소 정리**
- 사용자가 스크린샷 5장으로 짚어준 위치를 그대로 대응:
  1. 마퀴 티커(`.marquee-band`, 기능 이름 반복 스크롤 배너) — **완전 삭제**
     (HTML 블록 + `.marquee-*`/`@keyframes marquee-scroll` CSS 전부 제거).
  2. 히어로 우측 시간표 목업 카드 + 떠다니는 칩 2개(`.hero-visual`/
     `.hero-mockup*`/`.hero-chip*`) — **완전 삭제**, 마우스 3D 틸트를 주던
     `index.js`의 관련 코드 블록도 함께 제거(죽은 코드 방치 안 함).
  3. 히어로 하단 "아래로 스크롤" 마우스 아이콘(`.hero-scroll-cue`) — **완전
     삭제**.
  4. 히어로 상단 "NEIS Open API 실시간 연동" 배지(`.hero-eyebrow`) — **완전
     삭제**.
  - 위 4개를 들어내면서 `.hero-inner`가 원래 2단 그리드(카피 + 시각 요소)
    였는데 오른쪽 칼럼이 통째로 없어지므로, 1단 중앙 정렬 레이아웃(
    `max-width: 720px; margin: 0 auto; text-align: center;`)으로 바꾸고
    `.hero-cta-group`/`.hero-badges`도 가운데 정렬로 맞춤. 반응형 미디어
    쿼리에 남아있던 `.hero-visual`/`.hero-chip`/`.hero-scroll-cue` 관련
    규칙도 정리.
  5. "마지막 사진"(오늘의 급식 벤토 카드) — 사용자가 "검은 점"이라고
     짚었지만 코드만으로는 정확한 위치 특정이 안 돼서(같은 클래스가 다른
     카드에도 다 있어서 스크린샷 하나로는 이 카드만의 문제인지 판단 불가)
     **AskUserQuestion으로 위치를 먼저 확인**("오른쪽 위") → 벤토 카드마다
     들어있는 `.bento-card-glow`(카드 우상단에 보라색 은은한 글로우를
     hover 시 보여주는 장식용 div, `top:-40%; right:-20%`로 정확히 우상단에
     위치)와 정확히 일치 → 모든 벤토 카드(`bento-card-glow` div 5개)에서
     제거하고 관련 CSS(`.bento-card-glow`, `.bento-card:hover
     .bento-card-glow`)도 삭제. 다른 장식 요소들과 마찬가지로 "완전히 안
     보이게" 만드는 가장 확실한 방법은 삭제였음(투명도만 낮추는 식으로
     남겨두면 재발 여지가 있다고 판단).
- 정리 후 살아있는 히어로 구성: 배경 블롭 애니메이션(`.hero-blob`) + 격자
  패턴 + 타이틀/서브카피/CTA 버튼 2개/체크마크 배지 3개 — 장식은 줄었지만
  핵심 카피와 CTA는 그대로 유지됨.
- **검증**: `./gradlew compileJava processResources -q`로 반영 →
  브라우저로 `/` 방문해서 `get_page_text`에 마퀴/NEIS 배지/시간표 목업/스크롤
  큐 문구가 전혀 없는 것 확인 → `document.querySelectorAll()`로
  `.bento-card-glow`/`.hero-visual`/`.marquee-band`/`.hero-scroll-cue`/
  `.hero-eyebrow`가 전부 0개인 것과 `.hero-inner`가 `text-align: center`로
  적용된 것 직접 확인, 콘솔 에러 없음.

**다음에 이어서 할 때 참고할 것**: 히어로가 지금은 카피 중심의 미니멀한
구성이라, 나중에 사용자가 "너무 밋밋하다"고 하면 그때 다른 시각 요소(정적
일러스트, 스크린샷 등)를 다시 추가하는 방향으로 논의하면 됨 - 이번엔
"삭제하거나 대체"라는 선택지 중 삭제 쪽으로 확실하게 정리했다.
