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
  **이 폴더(`webproject-main\webproject-main`)는 git이 파일을 스테이징만
  해뒀고 아직 커밋이 하나도 없는 상태다** (`git log` → "no commits yet").
  작업 마무리 시 커밋/푸시가 필요한지 사용자에게 확인할 것.
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
- **관리자(ROLE_ADMIN) 테스트 계정**: `username=admin / password=admin`
  (id=1). 회원가입 화면에서는 role을 선택할 방법이 없고
  `UserService.register()`가 항상 `ROLE_USER`로 고정하기 때문에, 관리자
  계정이 더 필요하면 지금은 DB에서 직접 role 컬럼을 바꾸는 것 외에 방법이
  없다(SQL: `UPDATE users SET role='ROLE_ADMIN' WHERE username='...';`).
  이 계정 정보도 DB 비밀번호와 마찬가지로 개발용 평문이니 배포 전에는
  반드시 바꿀 것.
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
├── global
│   ├── config.SecurityConfig             : /admin/**는 hasRole("ADMIN"), /uploads/**는 permitAll
│   ├── config.WebConfig                  : /uploads/** -> app.upload.dir 정적 리소스 매핑 (2026-08-05 추가)
│   └── advice.GlobalModelAdvice          : 모든 화면에 loginUser 자동 주입
├── user
│   ├── controller.AuthController         : 로그인/회원가입/마이페이지/중복확인/계정삭제
│   │                                        (POST /mypage/delete, 2026-08-05(3차) 추가)
│   ├── controller.AdminUserController    : /admin/users (ROLE_ADMIN 전용) — 전체 계정 목록/권한
│   │                                        승격·해제/탈퇴 처리/복구 (2026-08-05(3차) 추가)
│   ├── service.UserService                : 회원가입, 프로필 수정, 아이디 중복체크, deleteAccount()
│   │                                        (본인 확인 비밀번호 재입력 → 소프트 삭제, 2026-08-05(3차) 추가)
│   ├── service.AdminUserService           : 관리자 전용 계정 관리 — 기존 UserService는 건드리지
│   │                                        않고 완전히 분리(AdminPostService와 동일 패턴). 본인
│   │                                        계정은 권한 변경/삭제 못 하게 방어 로직 있음
│   │                                        (2026-08-05(3차) 추가)
│   ├── service.CustomUserDetailsService   : UserDetails.disabled(user.isDeleted())로 탈퇴 계정
│   │                                        로그인 차단(2026-08-05(3차) 추가)
│   ├── entity.User                        : username/password/nickname/
│   │                                        schoolName/schoolCode/atptCode/
│   │                                        schoolKind/grade/classNum/role/
│   │                                        **deleted/deletedAt(2026-08-05(3차) 추가, 소프트 딜리트)**
│   │                                        (point/tier 컬럼 아직 없음)
│   ├── dto.RegisterDto / MyPageUpdateDto
│   ├── dto.AdminUserSummaryDto            : 관리자 계정 목록 조회 전용 DTO (2026-08-05(3차) 추가)
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
    │                                        전체 게시글 탭·문제없음 처리는 2026-08-05(3차) 추가)
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
│                                             "커뮤니티" 메뉴(/posts) + ROLE_ADMIN에게만 보이는
│                                             "관리자" 메뉴(/admin/posts, sec:authorize="hasRole('ADMIN')")
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
├── admin/user-list.html                   : 관리자 전체 계정 목록 — 상태(활동중/탈퇴함)/아이디/닉네임/
│                                             학교/권한 배지 + 관리자 승격·권한해제/탈퇴 처리·복구 버튼.
│                                             로그인한 관리자 본인 행은 "(본인 계정)"만 표시하고 액션
│                                             버튼은 숨김(서버 단에서도 자기 자신 변경은 막혀 있음)
│                                             (2026-08-05(3차) 추가)
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
                        관리 화면 전용 스타일이 필요하면 여기부터 확인할 것

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


## 7. 실행/운영 메모

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

- **[사용자 지적] 댓글 전용 관리 페이지 부재**: 게시글은 `/admin/posts`에서
  신고 여부와 무관하게 "전체 게시글" 탭으로 전부 훑어볼 수 있는데
  (`AdminPostService.getAllPosts()` → `PostRepository.
  findAllByDeletedFalseOrderByCreatedAtDesc()`), `PostComment`는 이런
  전체 목록 화면이 아예 없다 — 지금은 `AdminPostService.getReportedComments()`
  (`PostCommentRepository.findReportedOrBlindComments()`, `blind=true` 또는
  `reportCount>0`인 것만)로 신고된 것만 보이거나, 게시글 상세를 하나씩 열어야
  그 글의 댓글을 볼 수 있다. **신고가 아예 없는 "평범한" 댓글은 관리자가 볼
  방법이 사실상 없음.** `AdminScheduleCommentService.getAllComments()`(이번
  라운드에 한마디용으로 만든 것, 4번 항목 참고)와 동일한 패턴으로
  `AdminPostService`에도 `getAllComments(keyword)` +
  `PostCommentRepository.findAllByDeletedFalseOrderByCreatedAtDesc()`류
  쿼리를 추가하고, 댓글 전용 목록 화면(부모 게시글 링크 포함)을 새로 만들
  필요가 있어 보임. 만들 때 `/admin/schedule-comments`를 템플릿으로 삼으면
  빠를 것(구조가 사실상 동일 - 짧은 콘텐츠, 상세 페이지 없이 인라인 액션).
- **[사용자 지적] 신고 관리(`/admin/reports`) 검색 기능 없음**: `admin/
  report-list.html`은 `admin/post-list.html`(`admin-search-form`, 제목/작성자
  검색)이나 `admin/schedule-comment-list.html`(내용/작성자 검색)과 달리
  검색창 자체가 없다. 더 황당한 건 **서비스 레이어는 이미 keyword 필터링을
  지원**한다는 것 — `AdminPostService.getReportedPosts(keyword)`/
  `getReportedComments(keyword)`, `AdminScheduleCommentService.
  getReportedComments(keyword)` 전부 `matches()` 헬퍼로 keyword 필터가
  구현돼 있는데, `AdminReportController.list()`가 `keyword` 파라미터 자체를
  안 받고 항상 `null`을 넘긴다(오늘 세션 초반에 컴파일 에러를 고치면서
  `null`로 메꿨던 부분 — 4번 항목 참고). `@RequestParam(required=false)
  String keyword`를 추가하고 세 탭(게시물/댓글/한마디) 각각에 검색 폼만
  붙이면 되는, 비교적 작은 작업.
- **[사용자 지적] "내가 신고한 글/댓글" 확인 기능 없음**: `PostDetailDto`/
  `PostCommentDto`/`ScheduleCommentDto` 어디에도 "현재 로그인한 사용자가
  이미 신고했는지" 알려주는 필드가 없다. `PostReportRepository.
  existsByPost_IdAndReporter_Username()` / `CommentReportRepository.
  existsByComment_IdAndReporter_Username()` / `ScheduleCommentReportRepository.
  existsByComment_IdAndReporter_Username()`는 이미 있으니(원래는 신고
  중복 방지용) 이걸 재사용해서 `reportedByMe` 같은 필드를 DTO에 추가하고,
  프론트에서 신고 버튼을 처음부터 "신고완료"로 비활성화하면 됨(지금은
  `post-detail.js`가 신고 버튼을 무조건 활성 상태로 렌더링했다가, 클릭
  후 서버가 "이미 신고한 게시물/댓글입니다" 에러를 던져야만 알 수 있음 —
  `PostService.java:178`/`PostCommentService.java:126` 근처). 더 나아가면
  마이페이지에 "내가 신고한 목록" 페이지(신고 이력 조회, `findByReporter_
  Username` 류 메서드 추가 필요)까지 만들 수 있는데, 이건 별도 기능이니
  범위를 나눠서 사용자에게 먼저 확인할 것.
- **관리자 목록 전체가 페이지네이션 없음**: `AdminPostService`의
  `getReportedPosts`/`getDeletedPosts`/`getAllPosts`/`getReportedComments`,
  `AdminScheduleCommentService`의 대응 메서드들 전부 `List<...>`를 그대로
  반환하는 전체 조회 쿼리다(공개 커뮤니티 목록은 `Page`/`Pageable`을 쓰는
  것과 대조적 — `PostService.getList()`, `PostRepository.search()` 참고).
  게시물/댓글/한마디 수가 늘어나면 관리자 페이지 로딩이 느려지고 테이블이
  한없이 길어질 것 — 스케일 이슈이니 지금 당장은 아니어도 인지는 해둘 것.
- **게시글 목록에 "신고된 댓글 있음" 신호가 없음**: `admin/post-list.html`
  행에는 게시물 자체의 신고 상태 배지만 있고, 그 글의 댓글 중 신고/블라인드된
  게 있는지는 표시가 안 됨 — 관리자가 신고 관리(`/admin/reports?type=comment`)
  탭에서 따로 확인하지 않으면 "전체 게시글" 탭만 보고는 알 길이 없다.
- **댓글 신고 목록에서 해당 댓글로 바로 이동/포커스가 안 됨**:
  `admin/report-list.html`의 댓글 탭(`report-list.html:99` 근처)은 부모
  게시글 상세 페이지로만 링크되고, 그 안에서 신고된 댓글이 어디 있는지는
  관리자가 직접 스크롤해서 찾아야 한다(앵커 링크나 하이라이트 없음).
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
