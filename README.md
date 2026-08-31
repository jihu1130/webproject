# 🏫 webproject

**학교일정을 확인하고, 같은 학교 학생들끼리 소통할 수 있는 커뮤니티 웹서비스**

[![Java](https://img.shields.io/badge/Java-21-orange?logo=openjdk&logoColor=white)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.0-6DB33F?logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![Spring Security](https://img.shields.io/badge/Spring%20Security-OAuth2-6DB33F?logo=springsecurity&logoColor=white)](https://spring.io/projects/spring-security)
[![Hibernate](https://img.shields.io/badge/Hibernate-7.4.1-59666C?logo=hibernate&logoColor=white)](https://hibernate.org/)
[![MySQL](https://img.shields.io/badge/MySQL-8-4479A1?logo=mysql&logoColor=white)](https://www.mysql.com/)
[![Thymeleaf](https://img.shields.io/badge/Thymeleaf-Server%20Rendered-005F0F?logo=thymeleaf&logoColor=white)](https://www.thymeleaf.org/)
[![Live Demo](https://img.shields.io/badge/Live%20Demo-webschool.kro.kr-2ea44f?logo=googlechrome&logoColor=white)](https://webschool.kro.kr/)

---
🔗 **바로가기**: [https://webschool.kro.kr/](https://webschool.kro.kr/)

## 📌 소개

NEIS(교육정보 개방 포털) API로 시간표·급식·학사일정을 실시간으로 조회하고,
같은 학교 학생들끼리 커뮤니티에서 소통할 수 있는 학교 생활 플랫폼입니다.
동명 학교를 주소로 구분하는 학교 찾기부터, 자유/익명/QnA 게시판, 신고 기반
자동 블라인드, 날짜별 "오늘의 한마디", 권한이 세분화된 관리자 페이지까지
직접 설계하고 구현했습니다.

## ✨ 주요 기능

- 🔍 **학교 찾기** — 동명학교를 주소로 구분해서 검색
- 📅 **학교일정 연동(NEIS API)** — 시간표 · 급식 · 학사일정 조회, DB 캐시
- 🔐 **로그인/회원가입** — 로컬 계정 + 구글 소셜 로그인(OAuth2), 첫 로그인 시
  학교 설정 강제 온보딩
- 💬 **커뮤니티** — 자유 / 익명 / QnA 게시판, 댓글, 이미지 첨부
- 🚨 **신고 → 자동 블라인드** — 게시글·댓글·한마디 공통으로, 서로 다른 사용자
  3명이 신고하면 자동 블라인드 처리
- 📝 **오늘의 한마디** — 날짜별 한 줄 댓글, 좋아요/북마크
- 🛡️ **관리자 페이지** — 총관리자/부관리자 2단계, 부관리자 권한을 기능별로
  개별 On/Off (신고 관리 / 게시글 관리 / 한마디 관리 / 공지 관리)
- 📢 **공지사항** — 활성 공지 항상 1개 유지, 과거 이력 보관 및 조회
- 🔔 **알림** — 댓글/좋아요/관리자 조치/공지에 대한 알림, 네비바 뱃지
- 🏖️ **방학 D-Day** — 학사일정 기반 방학까지 남은 일수 계산

## 🛠️ 기술 스택

| 영역 | 스택 |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 4.1.0 (Web MVC, Data JPA, Security, Thymeleaf) |
| ORM / DB | Hibernate 7.4.1, MySQL 8 |
| Auth | Spring Security, OAuth2 Client (Google Login) |
| View | Thymeleaf, Bootstrap5, FontAwesome, Pretendard, FullCalendar |
| External API | NEIS Open API (`java.net.http.HttpClient` 직접 연동) |
| Build | Gradle |

## 🗂️ 패키지 구조

기능(도메인) 단위로 패키지를 분리했습니다 — 레이어(controller/service 등)가
아니라 `user`, `school`, `post`처럼 도메인이 최상위 기준입니다.

```
com.webschool.webschool
├── main        : 홈 진입점, 관리자 홈(권한별 첫 메뉴로 리다이렉트)
├── global      : 보안 설정, 정적 리소스 서빙, 권한 인터셉터, 공통 모델 어드바이스
├── user        : 회원가입/로그인, 마이페이지, 계정 관리, 프로필 조회
├── school      : 캘린더, NEIS 연동, 시간표/급식 캐시, 학사일정, 오늘의 한마디
├── post        : 커뮤니티(자유/익명/QnA) + 댓글 + 신고/블라인드 + 이미지 첨부
├── notice      : 공지사항(관리자 작성, 활성 공지 1개 유지, 이력 보관)
└── notification: 댓글/좋아요/관리자 조치/공지 알림
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


---
**서비스 주소**: [https://webschool.kro.kr/](https://webschool.kro.kr/)


<p align="center">Made with ☕ by <a href="https://github.com/jihu1130">jihu1130</a></p>
