package com.webschool.webschool.home.controller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

// 검색 로봇 안내문. static/robots.txt로 두지 않은 이유: 정적 파일에는 1년 캐시가 걸려 있고
// (WebschoolApplication) 루트 경로 파일은 내용 해시 주소도 안 붙어서, 나중에 고쳐도 옛 내용이 남는다.
// 여기 적는 건 "수집하지 말아 달라"는 부탁일 뿐 접근 차단이 아니다 - 실제 차단은 SecurityConfig가 한다.
@RestController
public class RobotsController {

    // 로그인해야만 열리는 화면(어차피 /login으로 튕김)과 JSON API만 적는다. 공개 화면(게시글·공지·
    // 캘린더·랭킹·프로필)은 그대로 수집 대상이고, 링크 공개·비공개 글은 post/detail.html의 noindex가 맡는다.
    private static final String BODY = String.join("\n",
            "User-agent: *",
            "Disallow: /admin",
            "Disallow: /mypage",
            "Disallow: /notifications",
            "Disallow: /api/",
            "Disallow: /school/api/",
            "Disallow: /polls/",
            "Disallow: /school-setup",
            "Disallow: /email-setup",
            "Disallow: /password-setup",
            "Disallow: /verify-email",
            "Disallow: /reset-password",
            "");

    @GetMapping(value = "/robots.txt", produces = MediaType.TEXT_PLAIN_VALUE)
    public String robots() {
        return BODY;
    }
}
