# 🏫 webproject

**학교일정을 확인하고, 같은 학교 학생들끼리 소통할 수 있는 커뮤니티 웹서비스**

[![Java](https://img.shields.io/badge/Java-21-orange?logo=openjdk&logoColor=white)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.0-6DB33F?logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![Spring Security](https://img.shields.io/badge/Spring%20Security-OAuth2-6DB33F?logo=springsecurity&logoColor=white)](https://spring.io/projects/spring-security)
[![Hibernate](https://img.shields.io/badge/Hibernate-7.4.1-59666C?logo=hibernate&logoColor=white)](https://hibernate.org/)
[![MySQL](https://img.shields.io/badge/MySQL-8-4479A1?logo=mysql&logoColor=white)](https://www.mysql.com/)
[![Thymeleaf](https://img.shields.io/badge/Thymeleaf-Server%20Rendered-005F0F?logo=thymeleaf&logoColor=white)](https://www.thymeleaf.org/)
[![CI](https://github.com/jihu1130/webproject/actions/workflows/ci.yml/badge.svg)](https://github.com/jihu1130/webproject/actions/workflows/ci.yml)

---

## 📌 소개

NEIS(교육정보 개방 포털) API로 시간표·급식·학사일정을 실시간으로 조회하고,
같은 학교 학생들끼리 커뮤니티에서 소통할 수 있는 학교 생활 플랫폼입니다.
동명 학교를 주소로 구분하는 학교 찾기부터, 자유/익명/QnA 게시판, 리치 에디터,
신고 기반 자동 블라인드, 날짜별 "오늘의 한마디", 권한이 세분화된 관리자
페이지와 감사 로그까지 직접 설계하고 구현했습니다.

## ✨ 주요 기능

- 🔍 **학교 찾기** — 동명학교를 주소로 구분해서 검색
- 📅 **학교일정 연동(NEIS API)** — 시간표 · 급식 · 학사일정 조회, DB 캐시,
  방학 D-Day
- 🔐 **로그인/회원가입** — 로컬 계정 + 구글 소셜 로그인(OAuth2), 첫 로그인 시
  학교 설정 강제 온보딩
- ✉️ **이메일 인증 / 아이디·비밀번호 찾기** — 가입 시 이메일 인증, 인증
  토큰 기반 비밀번호 재설정 (실사용자 계정 메일 발송/수신 검증 완료)
- 💬 **커뮤니티** — 자유 / 익명 / QnA(질문자 답변 채택) 게시판, 댓글,
  리치 에디터(Quill 기반 이미지·영상·게시물/한마디 임베드)
- 🔎 **통합 검색** — 게시글/작성자 검색 (익명 글은 검색 결과에서 제외)
- 🚨 **신고 → 자동 블라인드** — 게시글·댓글·한마디 공통으로, 서로 다른 사용자
  3명이 신고하면 자동 블라인드 처리, 사용자 간 차단 기능
- 📝 **오늘의 한마디** — 날짜별 한 줄 댓글(리치 에디터 지원), 좋아요/북마크
- 🛡️ **관리자 페이지** — 총관리자/부관리자 2단계, 부관리자 권한을 기능별로
  개별 On/Off (신고 관리 / 게시글 관리 / 한마디 관리 / 공지 관리), 계정 제재,
  관리자 조치 감사 로그
- 📢 **공지사항** — 활성 공지 항상 1개 유지, 과거 이력 보관 및 조회
- 🐛 **버그 리포트** — 비로그인 사용자도 제출 가능(사진/영상 첨부), 총관리자
  전용 처리 화면
- 🔔 **알림** — 댓글/좋아요/관리자 조치/공지에 대한 알림, 네비바 뱃지
- 🌗 **다크모드** — 사이트 전역 수동 토글
- 👤 **프로필** — 활동 통계, 클릭 가능한 활동 내역 링크, 탈퇴 계정 닉네임 치환

## 🛠️ 기술 스택

| 영역 | 스택 |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 4.1.0 (Web MVC, Data JPA, Security, Thymeleaf, Actuator, Mail) |
| ORM / DB | Hibernate 7.4.1, MySQL 8 |
| Auth | Spring Security, OAuth2 Client (Google Login) |
| View | Thymeleaf, Bootstrap5, FontAwesome, Pretendard, FullCalendar, Quill(리치 에디터) |
| Sanitize | Jsoup (리치 에디터 본문 XSS 방어) |
| External API | NEIS Open API (`java.net.http.HttpClient` 직접 연동) |
| CI/CD | GitHub Actions |
| Build | Gradle |

## 🗂️ 패키지 구조

기능(도메인) 단위로 패키지를 분리했습니다 — 레이어(controller/service 등)가
아니라 `user`, `school`, `post`처럼 도메인이 최상위 기준입니다.

```
com.webschool.webschool
├── main        : 홈 진입점, 검색(SearchController), 관리자 홈(권한별 첫 메뉴로 리다이렉트)
├── global      : 보안 설정, 정적 리소스 서빙, 권한 인터셉터, 공통 모델 어드바이스,
│                 메일 발송, 리치 에디터 업로드/HTML 새니타이즈, 임베드 카드 조회 API
├── admin       : 관리자 조치 감사 로그(도메인 공통, 특정 기능에 종속되지 않음)
├── user        : 회원가입/로그인, 이메일 인증/비밀번호 재설정, 마이페이지,
│                 계정 관리·제재·차단, 프로필 조회(일반/관리자용 분리)
├── school      : 캘린더, NEIS 연동, 시간표/급식 캐시, 학사일정, 방학 D-Day,
│                 오늘의 한마디(리치 에디터 본문 포함)
├── post        : 커뮤니티(자유/익명/QnA + 답변 채택) + 댓글 + 신고/블라인드 +
│                 이미지 첨부 + 리치 에디터 본문
├── notice      : 공지사항(관리자 작성, 활성 공지 1개 유지, 이력 보관)
├── notification: 댓글/좋아요/관리자 조치/공지 알림
└── bugreport   : 버그 리포트 제출(비로그인 포함) + 첨부파일 + 총관리자 전용 관리
```

## 🚀 시작하기

```bash
# 1) application.yml 준비 (git 추적 대상 아님)
cp src/main/resources/application.yml.example src/main/resources/application.yml
# → MySQL 비밀번호 / NEIS API 키 입력 (구글 로그인 블록은 선택)

# 2) MySQL에 webschool 데이터베이스 준비 후 실행
./gradlew bootRun   # http://localhost:8888
```

자세한 빌드/테스트/트러블슈팅 가이드는 [`CLAUDE.md`](CLAUDE.md), 앞으로 할
작업 목록은 [`todo.md`](todo.md)에 정리되어 있습니다.

## 🔒 권한 체계

| 역할 | 설명 |
|---|---|
| `ROLE_USER` | 일반 사용자 |
| `ROLE_ADMIN` | 부관리자 — 신고/게시글/한마디/공지 관리 권한을 개별 부여 |
| `ROLE_SUPER_ADMIN` | 총관리자 — `admin` 계정 전용, 부관리자 권한 승격/회수 |

## 🧩 설계 원칙

- 삭제는 전부 **소프트 딜리트** (물리 삭제로 인한 FK 오류 방지)
- 신고 → 자동 블라인드 패턴을 게시글/댓글/한마디에 동일하게 적용
- 익명 게시글은 서버 단에서 닉네임을 치환하고, 프로필/검색에서 제외해 익명성 보장
- 탈퇴 계정은 일반 화면에서만 닉네임을 치환(관리자 화면은 실제 신원 유지)
- 리치 에디터 본문은 저장 전 Jsoup으로 새니타이즈 — `th:utext` 렌더링의 유일한 XSS 방어선
- 관리자 조치는 기능에 상관없이 공통 감사 로그(`admin.AdminActionLog`)에 기록

---

<p align="center">Made with ☕ by <a href="https://github.com/jihu1130">jihu1130</a></p>
