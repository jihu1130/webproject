package com.webschool.webschool.post.admin.controller;

import com.webschool.webschool.post.recommend.dto.PostDailyBestResultDto;
import com.webschool.webschool.post.recommend.dto.PostRecommendRankDto;
import com.webschool.webschool.post.recommend.service.PostRecommendService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

// 추천 게시글 관리(관리자 페이지 재구성, 2026-09-21 추가) - 사용자 쪽 PostRecommendController와
// 완전히 동일한 조회 메서드(PostRecommendService.getRanking()/getDailyBestHistory())를 그대로
// 재사용하는 조회 전용 화면(둘 다 이미 공개 열람 화면(/posts/contest)에서 노출되는 정보라 새
// DTO/서비스를 따로 만들지 않는다). "콘텐츠 관리 > 게시글" 산하 3번째 서브탭
// (admin/fragments/nav.html contentSubTabs 참고), AdminAccessInterceptor가 canManagePosts로
// 게이팅한다. 1차는 조회 전용 - 추천 취소/랭킹 제외 같은 조치 기능은 범위 밖(후속 작업).
@Controller
@RequestMapping("/admin/post-recommend")
@RequiredArgsConstructor
public class AdminPostRecommendController {

    private final PostRecommendService postRecommendService;

    @GetMapping
    public String list(@RequestParam(defaultValue = "DAILY") PostRecommendService.Period period,
                        Authentication authentication, Model model) {
        List<PostRecommendRankDto> ranking = postRecommendService.getRanking(period, authentication.getName());
        model.addAttribute("ranking", ranking);
        model.addAttribute("period", period);
        return "admin/post-recommend-list";
    }

    @GetMapping("/history")
    public String history(@RequestParam(defaultValue = "0") int page, Model model) {
        Page<PostDailyBestResultDto> results = postRecommendService.getDailyBestHistory(page, 10);
        model.addAttribute("results", results);
        return "admin/post-recommend-history";
    }
}
