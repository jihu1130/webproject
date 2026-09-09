package com.webschool.webschool.admin.controller;

import com.webschool.webschool.bugreport.repository.BugReportRepository;
import com.webschool.webschool.post.repository.PostCommentRepository;
import com.webschool.webschool.post.repository.PostRepository;
import com.webschool.webschool.school.repository.ScheduleCommentRepository;
import com.webschool.webschool.user.domain.PointTier;
import com.webschool.webschool.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// 관리자 개요 대시보드 - AdminHomeController가 예전엔 "/admin"을 특정 기능 화면으로만 리다이렉트했는데,
// 가입자/게시글/신고/문의 현황을 한눈에 보여주는 화면이 아예 없었다(디자인 개선 계획서 우선순위 "상" 1번).
// user/post/report/bugreport 여러 기능에 걸친 집계라 특정 기능 패키지가 아니라 최상위 admin 패키지에
// 둔다(CLAUDE.md의 "감사 로그처럼 여러 기능에 걸친 것만 최상위 admin" 규칙). 총관리자 전용 -
// AdminAccessInterceptor 참고(문의 관리 수치를 포함하는데 문의 관리 자체가 총관리자 전용이라 위임하지 않음).
@Controller
@RequiredArgsConstructor
public class AdminDashboardController {

    private static final int TREND_DAYS = 14;

    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final PostCommentRepository postCommentRepository;
    private final ScheduleCommentRepository scheduleCommentRepository;
    private final BugReportRepository bugReportRepository;

    @GetMapping("/admin/dashboard")
    public String dashboard(Model model) {
        model.addAttribute("activeUserCount", userRepository.countByDeletedFalse());
        model.addAttribute("totalPostCount", postRepository.countByDeletedFalse());

        long unresolvedReports = postRepository.countByDeletedFalseAndBlindTrueAndReportClearedFalse()
                + postCommentRepository.countByDeletedFalseAndBlindTrueAndReportClearedFalse()
                + scheduleCommentRepository.countByDeletedFalseAndBlindTrueAndReportClearedFalse();
        model.addAttribute("unresolvedReportCount", unresolvedReports);
        model.addAttribute("unansweredInquiryCount", bugReportRepository.countByResolvedFalse());

        Map<LocalDate, Long> dailyPostCounts = buildEmptyDailyBuckets();
        LocalDateTime since = dailyPostCounts.keySet().iterator().next().atStartOfDay();
        for (LocalDateTime createdAt : postRepository.findCreatedAtSince(since)) {
            dailyPostCounts.merge(createdAt.toLocalDate(), 1L, Long::sum);
        }
        DateTimeFormatter dayLabel = DateTimeFormatter.ofPattern("M/d");
        model.addAttribute("postTrendLabels", dailyPostCounts.keySet().stream().map(dayLabel::format).toList());
        model.addAttribute("postTrendValues", new ArrayList<>(dailyPostCounts.values()));

        Map<PointTier, Long> tierCounts = new LinkedHashMap<>();
        for (PointTier tier : PointTier.values()) {
            tierCounts.put(tier, 0L);
        }
        for (Integer points : userRepository.findAllActivePoints()) {
            tierCounts.merge(PointTier.forPoints(points == null ? 0 : points), 1L, Long::sum);
        }
        model.addAttribute("tierLabels", tierCounts.keySet().stream().map(PointTier::getLabel).toList());
        model.addAttribute("tierValues", new ArrayList<>(tierCounts.values()));

        return "admin/dashboard";
    }

    // 최근 TREND_DAYS일을 값 0으로 미리 채워둔 순서 보장 맵 - 글이 하나도 없던 날짜도 차트에서
    // 빈 구간 없이 0으로 표시되게 한다(LinkedHashMap이라 insertion 순서 = 날짜 오름차순 유지).
    private Map<LocalDate, Long> buildEmptyDailyBuckets() {
        Map<LocalDate, Long> buckets = new LinkedHashMap<>();
        LocalDate today = LocalDate.now();
        for (int i = TREND_DAYS - 1; i >= 0; i--) {
            buckets.put(today.minusDays(i), 0L);
        }
        return buckets;
    }
}
