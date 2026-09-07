package com.webschool.webschool.user.controller;

import com.webschool.webschool.user.domain.User;
import com.webschool.webschool.user.dto.RankingItemDto;
import com.webschool.webschool.user.service.UserPointService;
import com.webschool.webschool.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

// 포인트/티어 랭킹 페이지(todo.md 요구사항) - 공개 프로필(/users/{uuid})이 이미 포인트·티어를
// permitAll로 보여주고 있어 같은 공개 수준으로 로그인 없이도 열람 가능하게 둔다(SecurityConfig 참고).
@Controller
@RequiredArgsConstructor
public class RankingController {

    private static final int TOP_LIMIT = 10;

    private final UserPointService userPointService;
    private final UserService userService;

    // 사용자 요청 - 페이지네이션 없이 상위 10명만 고정 노출하고, 로그인한 사용자가 그 10명 밖에
    // 있으면 자기 순위를 목록 맨 위에 별도로 보여준다(10등 이내에 이미 있으면 그 자리에서
    // 강조(ranking-row-me)되는 것만으로 충분해서 따로 안 띄움). "밖에 있는지"는 실제 top N 목록에
    // 내 uuid가 들어있는지로 직접 판단한다(동점자 때문에 목록엔 안 남았는데 카운트 기반 순위 계산만
    // 으로는 "top N 이내"로 잘못 보이는 경우가 있어서 - UserPointService.getMyRanking() 주석 참고).
    @GetMapping("/ranking")
    public String ranking(Authentication authentication, Model model) {
        List<RankingItemDto> topRanking = userPointService.getTopRanking(TOP_LIMIT);
        User current = currentUser(authentication);
        boolean alreadyShown = current != null
                && topRanking.stream().anyMatch(item -> item.getUuid().equals(current.getUuid()));

        model.addAttribute("topRanking", topRanking);
        model.addAttribute("myRanking", (current != null && !alreadyShown) ? userPointService.getMyRanking(current) : null);
        return "user/ranking";
    }

    @GetMapping("/ranking/tiers")
    public String tierGuide(Model model) {
        model.addAttribute("tierGuide", userPointService.getTierGuide());
        return "user/tier-guide";
    }

    private User currentUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            return null;
        }
        return userService.getByUsername(authentication.getName());
    }
}
