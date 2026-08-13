# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

**코드가 계속 바뀌므로, 이 문서와 실제 코드가 다르면 무조건 실제 코드를 신뢰할 것.**
과거 작업 이력(무엇을 언제 왜 했는지)은 이제 이 문서 대신 `git log`로 확인할 것 —
`https://github.com/jihu1130/webproject` (main 브랜치)에 실제 커밋 히스토리가 있다.
앞으로 할 일은 이 문서가 아니라 **[todo.md](todo.md)**에 정리돼 있다.

## Commands

**빌드/컴파일**
- 전체 컴파일: `./gradlew compileJava`
- 자바 변경 없이 정적 리소스(템플릿/CSS/JS)만 반영: `./gradlew processResources -q`
- 테스트 코드만 컴파일 확인: `./gradlew compileTestJava`

**실행**
- `./gradlew bootRun` (기본 포트 8888, `application.yml`의 `server.port`).
  MySQL(`jdbc:mysql://localhost:3306/webschool`, 계정 `root`/`1234`, 평문 —
  `application.yml`)이 로컬에 떠 있어야 함. `ddl-auto: update`라 엔티티
  변경 시 테이블이 자동 반영됨(단, 컬럼 삭제나 enum 값 확장 등은 감지 못 할
  때가 있음 — 아래 "알려진 함정" 참고).
- **8888 포트에 이미 떠 있는 프로세스를 함부로 끄지 말 것.** devtools가
  붙어있어서, 이미 `bootRun`으로 떠 있는 서버가 있다면 재시작 없이
  `./gradlew compileJava processResources -q`만 실행해도 클래스/리소스가
  자동 재로드된다. 꼭 새로 띄워야 하면 `--args='--server.port=8899'`처럼
  다른 포트를 쓸 것.
- `mysql` 클라이언트가 PATH에 없어 전체 경로로 직접 실행해야 함
  (PowerShell 기준): `& "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe" -u root -p1234 -D webschool --default-character-set=utf8mb4 -e "..."`
  (한글 데이터를 확인할 땐 `--default-character-set=utf8mb4`를 꼭 붙일 것).

**테스트**
- 전체 테스트: `./gradlew test`
- 단일 테스트 클래스: `./gradlew test --tests "com.webschool.webschool.TestDataSeeder"`
- Gradle이 입력 변화 없으면 `UP-TO-DATE`로 스킵하고 재실행하지 않으므로,
  강제로 다시 돌리려면 `--rerun` 플래그를 추가할 것.
- 린트/포매터(checkstyle, spotless 등)는 설정되어 있지 않음.

**개발용 테스트 데이터 시더** (`src/test/java/com/webschool/webschool/`, 일반
테스트가 아니라 `@SpringBootTest`로 DB에 직접 데이터를 심는 실행용 클래스임)
- `TestDataSeeder`: `user1`~`user5`(아이디=비밀번호) 계정 + 계정별 한마디
  1개, `admin`/`admin`(`ROLE_ADMIN`) 계정, 카테고리별 게시글 3개씩 + 공지
  1개 생성. 이미 있는 데이터는 건너뛰므로 여러 번 실행해도 안전(멱등).
- `SuperAdminSeeder`: `username=admin` 계정을 `ROLE_SUPER_ADMIN`으로 승격
  (`TestDataSeeder`로 admin 계정이 먼저 존재해야 함). 이미 총관리자면
  아무 것도 하지 않음(멱등).

## Architecture Highlights

여러 파일을 같이 봐야만 보이는 큰 그림만 정리한다. 상세 파일 구조는 아래
"패키지 구조" 참고.

- **패키지는 레이어가 아니라 기능 단위로 분리**(`user`/`school`/`post`
  각각이 자기 controller/service/domain/dto/repository를 가짐). `report`/
  `comment`는 별도 패키지가 아니라 전부 `post` 패키지 안(`post.domain.
  PostComment`, `post.domain.PostReport` 등)에 있다 — 게시글이라는
  애그리거트에 강하게 종속된 하위 기능이라 굳이 쪼개지 않기로 판단함.
  "다른 사람 것을 조회만 하는" 책임(관리자 계정 프로필 조회, 일반 사용자
  공개 프로필 조회)은 기존 서비스(`UserService`/`AdminUserService`)와
  분리해 새 서비스로 뽑는 게 이 코드베이스의 컨벤션.
- **권한 체계 3단계**: `ROLE_USER` / `ROLE_ADMIN`(부관리자, `User`의
  `canManageReports`/`canManagePosts`/`canManageScheduleComments`/
  `canManageNotices` 플래그로 기능별 권한을 개별 On/Off) / `ROLE_SUPER_ADMIN`(총관리자, `username="admin"`
  계정 전용 — 앱 안에 이 롤을 부여하는 UI/API가 없어 DB 직접 수정 또는
  `SuperAdminSeeder`로만 승격 가능, 유일하게 이 계정만 총관리자가 될 수
  있도록 고정된 설계). `/admin/**` 하위 경로별 실제 접근 제어는
  `global.security.AdminAccessInterceptor`가 담당하고,
  `global.advice.GlobalModelAdvice`가 모든 화면에 `loginUser` 엔티티를
  주입해 템플릿에서 권한 플래그를 바로 쓸 수 있게 한다. 새로운 관리자
  세부 권한을 추가할 땐 이 세 가지(`User`의 boolean 플래그 → 계정 관리
  화면의 토글 UI → `AdminAccessInterceptor`의 경로별 분기) 세트를 그대로
  복제하는 게 기존 관례.
- **삭제는 전부 소프트 딜리트**: `Post`/`PostComment`/`ScheduleComment`/`User`
  모두 물리 삭제 없이 `deleted`/`deletedAt` 플래그만 바꾼다(하드 삭제로 인한
  FK 500 에러를 겪고 나서 통일된 패턴 — 자식 엔티티를 새로 추가할 때도 이
  패턴을 기본값으로 따를 것).
- **신고→자동 블라인드 패턴이 3곳에 동일하게 반복 구현됨**: 게시글
  (`PostReport`), 댓글(`CommentReport`), 한마디(`ScheduleCommentReport`) —
  전부 "대상 id + 신고자 id" 유니크 제약으로 중복 신고 방지, 서로 다른
  사용자 3명이 신고하면 대상의 `blind=true`, 관리자가 "문제없음" 처리하면
  `reportCleared=true`(내용이 실제로 수정되면 자동 리셋). 새로운 신고
  대상을 추가할 땐 이 세 구현을 그대로 복제하는 것이 기존 관례다.
- **익명성 보호가 여러 곳에 걸쳐 일관되게 적용됨**: `Post.Category.
  ANONYMOUS` 글은 서버 단(DTO 생성 시)에서 닉네임을 "익명"으로 치환하고,
  본인 프로필/게시글 검색(작성자 닉네임 검색)에서도 익명 글은 아예 쿼리에서
  제외한다 — "이 프로필/검색 결과에 있다 = 이 사람이 썼다"는 추론이 가능해
  지면 익명 기능 자체가 무너지기 때문. 새 화면에 게시글 목록을 노출할 땐
  이 원칙을 지키고 있는지 확인할 것.
- **탈퇴 계정 닉네임 치환은 일반 사용자 화면에만 적용, 관리자 화면은 예외**:
  탈퇴한 작성자는 일반 화면에서 "탈퇴한 사용자"로 표시되지만, 관리자는
  실제 신원을 알아야 하므로 관리자 화면(`Admin*Service`)은 이 치환을 하지
  않고 항상 실제 닉네임을 보여준다.
- **"수정됨" 배지는 내용이 실제로 바뀔 때만**: 게시글/댓글/한마디 수정
  로직 전부 새 값과 기존 값을 비교해서 실제로 다를 때만 `updatedAt`을
  찍는다. 그대로 재저장하면 배지가 뜨지 않아야 정상.
- **Jackson 3 네임스페이스 주의**: Spring Boot 4.1부터 패키지가
  `com.fasterxml.jackson.*`이 아니라 `tools.jackson.*`로 바뀌었다
  (`school.service.NeisApiService`에서 확인 가능). NEIS 연동은 신/구 두
  방식이 공존한다 — 시간표/급식/학사일정(구버전, 문자열 자르기 파싱)과
  학교검색·반목록·기간조회(신버전, Jackson JsonNode 파싱, 더 안전함). 새
  NEIS 연동을 추가한다면 Jackson 방식을 따를 것.
- **NEIS Open API는 별도 클라이언트 라이브러리 없이 JDK 내장
  `java.net.http.HttpClient`로 직접 호출**(`NeisApiService`). 시간표/급식은
  DB 캐시(`Timetable`/`Meal`)를 먼저 조회하는 패턴이지만 TTL 만료 로직은
  아직 없음(한번 캐시되면 영구 반환 — [todo.md](todo.md) 참고).

## 프로젝트 개요

"학교일정을 확인하고 같은 학교 학생들끼리 소통할 수 있는 웹사이트". 핵심
기능: 학교 찾기(동명학교 주소 구분) · 학교일정 API(시간표/급식/학사일정) ·
로그인/회원가입 · 커뮤니티(자유/익명/QnA + 댓글/신고/블라인드) · 날짜별
한줄 댓글("오늘의 한마디") · 관리자 페이지(권한 세분화) · 알림/공지사항 ·
방학 D-Day. 투표 기능·포인트/티어 시스템·TTL 캐시 만료 등 남은 항목은
[todo.md](todo.md) 참고.

## 기술 스택

- Java 21 / Spring Boot 4.1.0 (data-jpa, security, thymeleaf, webmvc),
  Hibernate 7.4.1 + MySQL 8
- Spring Data JPA `Page`/`Pageable`로 커뮤니티 목록 페이지네이션
- 프론트: Thymeleaf + Bootstrap5(CDN, 일부만) + FontAwesome + Pretendard +
  FullCalendar, 순수 Vanilla JS(프레임워크 없음)
- 게시글 이미지: `app.upload.dir`(프로젝트 폴더 밖, `../uploads`)에 실제
  파일 저장, DB엔 경로만. `WebConfig`가 `/uploads/**`를 정적 서빙
- **총관리자 테스트 계정**: `username=admin / password=admin`(`TestDataSeeder`로
  생성 후 `SuperAdminSeeder`로 `ROLE_SUPER_ADMIN` 승격). **일반 사용자
  테스트 계정**: `user1`~`user5`(아이디=비밀번호), `TestDataSeeder`가 생성.
  둘 다 개발용 평문 계정이니 배포 전 반드시 바꿀 것 — DB 비밀번호/NEIS API
  키(`application.yml`)도 마찬가지로 평문.

## 패키지 구조

```
com.webschool.webschool
├── WebschoolApplication.java
├── main.controller  : HomeController("/"), AdminHomeController("/admin" 진입점 -
│                       권한별 첫 메뉴로 자동 리다이렉트)
├── global
│   ├── config       : SecurityConfig(경로별 인증/권한), WebConfig(/uploads 정적 서빙,
│   │                   AdminAccessInterceptor 등록)
│   ├── security.AdminAccessInterceptor : /admin/** 경로별 부관리자 세부 권한 검사
│   ├── advice.GlobalModelAdvice        : 모든 화면에 loginUser 엔티티 자동 주입
│   └── util.PageUtils                  : 관리자 목록(메모리 필터링)을 Page로 변환하는 유틸
├── user             : 회원가입/로그인/마이페이지, 계정 소프트 삭제/비활성화,
│                       관리자 계정 관리(권한 승격, 부관리자 권한 토글), 프로필 조회
│                       (일반/관리자용 분리)
├── school           : 캘린더 페이지, NEIS 연동(NeisApiService), 시간표/급식 DB
│                       캐시(SchoolService), 학사일정 조회/검색/방학 D-Day, 한줄 댓글
│                       (ScheduleComment, CRUD+신고+좋아요+북마크), 관리자 한마디 관리
├── post             : 커뮤니티 — 자유/익명/QnA 카테고리 + 댓글 + 신고/블라인드 +
│                       이미지 첨부 + 관리자 화면(전부 이 패키지 안)
├── notice           : 공지사항 — Post와 완전히 분리된 별도 모델. "활성 공지 항상
│                       1개"(새 공지 작성 시 이전 공지 자동 보관), 관리자 화면
│                       (/admin/notices, canManageNotices 권한 필요) + 사용자용 화면
│                       (/notices, 로그인 없이도 조회 가능) 둘 다 이 패키지 안
└── notification     : 댓글/좋아요/관리자 조치/공지사항에 대한 알림 - 네비바 종
                        배지 폴링(/notifications/unread-count) 방식, 실시간 아님
```

리소스는 `templates/{fragments,school,post,notice,notification,admin,user}` +
`static/{css,js}`로 기능별 대응. 공용 위젯(`school-search.js`/`class-select.js`/
`grade-select.js`)은 캘린더·회원가입·마이페이지수정 3곳에서 재사용되니 수정 시 세
화면 모두 확인할 것.

## 핵심 동작 원리

- **로그인 게이트**: `/school/**`는 인증 필요. `/posts`, `/posts/*`,
  `/posts/*/comments`(GET)와 `/users/*`(GET)는 비로그인도 열람 가능하지만
  작성/수정/삭제/신고는 인증 필요.
- **게시글/댓글/한마디 신고 → 자동 블라인드**: 서로 다른 사용자 3명이
  신고하면 블라인드. 게시글은 블라인드되면 목록에서 빠지고 작성자/관리자만
  URL로 직접 열람 가능(배너 표시). 댓글/한마디는 스레드에 남되 content가
  서버 단에서 안내 문구로 치환된다.
- **관리자 "문제없음" 판결**: `reportCleared=true`가 되면 재신고해도 카운트
  안 오름. 내용이 실제로 수정되거나 관리자가 다시 블라인드 처리하면 자동
  리셋.
- **금지어 필터**: `post.util.BannedWordFilter` — 부분 문자열 포함 검사만
  하는 단순 구현(우회 쉬움), 단어 목록도 예시 수준 — 운영 전 보강 필요.
- **조회수 중복 방지**: `HttpSession` 기반 세션당 1회만 카운트 — 세션
  만료/새 브라우저로 우회 가능한 경량 방지책.
- **캘린더 학사일정 검색**: `SchoolService.findNearestEvent(keyword)` —
  같은 이름이 매년 반복되므로(예: "기말고사") 오늘과 가장 가까운 것 하나만
  찾아 반환. 방학 D-Day(`getVacationDday()`)도 같은 방식으로 "방학"
  키워드를 검색해 계산(학교마다 "여름방학"처럼 기간 전체를 등록하기도
  하고 "방학식"/"개학" 하루짜리만 등록하기도 함 — 실제 NEIS 데이터로 검증됨).
- **캘린더 월간 일정 배지 색상**: 이름을 해시해서 색을 정하므로 같은
  이름이면 달이 바뀌어도 같은 색 유지. 같은 달 안에서 다른 이름끼리 색이
  겹치면 재배정하되, 앞뒤 달로 이어지는 일정은 원래 색을 그대로 유지(안
  그러면 달 경계에서 색이 바뀌어 보임). 여러 일정이 겹치는 주의 세로 배치는
  기간이 긴 일정을 먼저 배정해 위쪽 행을 우선 차지하게 한다.

## 알려진 함정 (다시 겪지 않기 위한 메모)

- **`ddl-auto: update`가 못 잡아내는 스키마 드리프트가 있다**: enum 값
  추가(예: `User.Role`에 새 값 추가)가 자동 반영 안 될 때가 있었고,
  엔티티 필드명이 SQL 예약어(`read`/`order`/`group`/`key` 등)와 겹치면
  `CREATE TABLE`이 조용히 실패한다(에러 로그 없이 테이블 자체가 안 만들어짐).
  원인 불명의 `DataIntegrityViolationException`이나 테이블 관련 500 에러가
  나면 엔티티 코드가 아니라 실제 DB `DESCRIBE 테이블명;`부터 확인할 것.
- **하드 삭제는 FK 있는 자식 테이블에서 500 에러를 유발한다** — 그래서
  소프트 딜리트가 기본 패턴이 됨(위 Architecture Highlights 참고).
- **커스텀 CSS 클래스명이 Bootstrap 예약 클래스(`btn-*`, `form-*` 등)와
  겹치면 예상 못한 스타일이 적용될 수 있다** — 새 클래스명 지을 때 확인할 것.
- **"항상 존재하는 상위 역할"을 권한 체계에 추가할 때, "이 역할이 마지막
  하나 남았을 때"를 가정한 기존 방어 로직이 전부 그 가정 위에 있었다는 걸
  의심할 것** — `ROLE_SUPER_ADMIN` 도입 후 "마지막 관리자는 강등 불가" 가드가
  총관리자 존재를 무시한 채 부관리자 권한 해제를 막아버린 사례가 있었다.
  컴파일 에러 없이 조용히 잘못 동작하는 타입이라 테스트 없이는 알아채기 어렵다.
- **엔티티를 DTO 없이 템플릿에서 직접 쓸 때 enum은 `.name()`을 붙여
  비교할 것** — `loginUser.role == 'ROLE_ADMIN'`처럼 실제 엔티티의 enum
  필드를 문자열과 직접 비교하면 항상 false로 평가된다(DTO의 `String` 필드와
  헷갈리기 쉬움).
- **PowerShell에서 bcrypt 해시(`$2a$10$...`)를 SQL에 넣을 때 큰따옴표
  문자열은 쓰지 말 것** — `$` 변수 보간이 시도되어 해시가 깨진다. 작은따옴표
  리터럴 + SQL 문자열은 `''`로 이스케이프하는 방식만 안전하다.
- **`@Enumerated(EnumType.STRING)` 컬럼에서 enum 값을 제거하기 전에 그
  값을 쓰는 기존 행이 있는지 먼저 확인할 것** — 남아있으면(소프트 삭제된
  행 포함) Hibernate가 그 행을 조회할 때 "알 수 없는 enum 값"으로 역직렬화
  실패한다. `Post.Category.NOTICE`를 걷어낼 때 해당 값을 쓰던 행(자식
  댓글 포함)을 먼저 하드 삭제하고서 enum 상수를 지웠다.
