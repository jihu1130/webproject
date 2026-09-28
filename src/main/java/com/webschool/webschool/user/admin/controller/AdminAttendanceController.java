package com.webschool.webschool.user.admin.controller;

import com.webschool.webschool.global.util.PageUtils;
import com.webschool.webschool.user.admin.dto.AdminAttendanceSummaryDto;
import com.webschool.webschool.user.admin.service.AdminAttendanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.List;

// 출석 관리(관리자 페이지 재구성, 2026-09-21 추가) - 사용자 쪽엔 이미 완성된 출석체크
// (AuthController "/mypage/attendance*")를 관리자가 조회할 화면이 그동안 전혀 없었다.
// AdminAccessInterceptor가 "/admin/attendance"를 canManageAttendance로 게이팅한다.
@Controller
@RequestMapping("/admin/attendance")
@RequiredArgsConstructor
public class AdminAttendanceController {

    private final AdminAttendanceService adminAttendanceService;

    @GetMapping
    public String list(@RequestParam(required = false) String keyword,
                        @RequestParam(defaultValue = "0") int page,
                        @RequestParam(required = false) Integer size, Model model) {
        List<AdminAttendanceSummaryDto> filtered = adminAttendanceService.getAllAttendanceSummaries(keyword);
        int pageSize = PageUtils.normalizeSize(size);
        Page<AdminAttendanceSummaryDto> summaries = PageUtils.paginate(filtered, page, pageSize);
        model.addAttribute("summaries", summaries);
        model.addAttribute("keyword", keyword);
        return "admin/attendance-list";
    }

    // 개인 출석 캘린더 상세 - 사용자 마이페이지의 미니 캘린더 팝업(AttendanceCalendarDto)과 같은
    // 데이터 소스를 재사용하되, 관리자 화면이라 별도 상세 페이지로 렌더링한다.
    @GetMapping("/{userId}")
    public String detail(@PathVariable Long userId,
                          @RequestParam(required = false) Integer year,
                          @RequestParam(required = false) Integer month,
                          Model model) {
        try {
            AdminAttendanceSummaryDto summary = adminAttendanceService.getSummary(userId);
            LocalDate today = LocalDate.now();
            int targetYear = year != null ? year : today.getYear();
            int targetMonth = month != null ? month : today.getMonthValue();

            model.addAttribute("summary", summary);
            model.addAttribute("year", targetYear);
            model.addAttribute("month", targetMonth);
            model.addAttribute("attendedDates",
                    adminAttendanceService.getAttendedDatesInMonth(userId, targetYear, targetMonth));
            model.addAttribute("dayGrid", adminAttendanceService.getMonthGrid(userId, targetYear, targetMonth));
            return "admin/attendance-detail";
        } catch (IllegalArgumentException e) {
            return "redirect:/admin/attendance";
        }
    }

    // 출석 인정/취소 처리 후 보던 달로 그대로 돌아가기 위해 year/month를 폼 파라미터로 같이 받는다.
    @PostMapping("/{userId}/grant")
    public String grant(@PathVariable Long userId,
                         @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                         @RequestParam int year, @RequestParam int month,
                         Authentication authentication, RedirectAttributes redirectAttributes) {
        try {
            adminAttendanceService.grantAttendance(userId, date, authentication.getName());
            redirectAttributes.addFlashAttribute("flashSuccess", date + " 출석을 인정 처리했습니다.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("flashError", e.getMessage());
        }
        return "redirect:/admin/attendance/" + userId + "?year=" + year + "&month=" + month;
    }

    @PostMapping("/{userId}/revoke")
    public String revoke(@PathVariable Long userId,
                          @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                          @RequestParam int year, @RequestParam int month,
                          Authentication authentication, RedirectAttributes redirectAttributes) {
        try {
            adminAttendanceService.revokeAttendance(userId, date, authentication.getName());
            redirectAttributes.addFlashAttribute("flashSuccess", date + " 출석을 취소 처리했습니다.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("flashError", e.getMessage());
        }
        return "redirect:/admin/attendance/" + userId + "?year=" + year + "&month=" + month;
    }
}
