package com.webschool.webschool.user.mypage.controller;

import com.webschool.webschool.global.util.PageUtils;
import com.webschool.webschool.user.domain.User;
import com.webschool.webschool.user.point.dto.AttendanceCalendarDto;
import com.webschool.webschool.user.point.dto.AttendanceCheckInResult;
import com.webschool.webschool.user.mypage.dto.MyPageUpdateDto;
import com.webschool.webschool.user.point.service.AttendanceService;
import com.webschool.webschool.user.mypage.service.MyActivityService;
import com.webschool.webschool.user.mypage.service.MyPageService;
import com.webschool.webschool.user.shop.service.ShopService;
import com.webschool.webschool.user.point.service.UserPointService;
import com.webschool.webschool.user.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;

// 로그인 후 마이페이지(/mypage/**) - 대시보드, 출석체크, 포인트 내역, 내 정보 수정/탈퇴,
// 프로필 설정, 알림 설정. 2026-09-28 AuthController에서 분리. 단, 이메일 인증 재발송
// (/mypage/resend-verification)은 온보딩 흐름이라 OnboardingController에 있다.
@Controller
@RequiredArgsConstructor
public class MyPageController {

    private final UserService userService;
    private final MyPageService myPageService;
    private final MyActivityService myActivityService;
    private final AttendanceService attendanceService;
    private final UserPointService userPointService;
    private final ShopService shopService;

    @GetMapping("/mypage")
    public String myPage(Authentication authentication, Model model) {
        // 프로필 카드 통계 바(게시글/댓글/받은 좋아요) - 프로필_디자인.md 설계 반영.
        model.addAttribute("stats", myActivityService.getStats(authentication.getName()));
        User user = userService.getByUsername(authentication.getName());
        boolean checkedInToday = attendanceService.hasCheckedInToday(user.getId());
        model.addAttribute("attendanceCheckedInToday", checkedInToday);
        // 출석체크 버튼에 "N일차 +M P" 미리보기를 보여주기 위한 값 - 스트릭 보너스 도입(2026-09-09)으로
        // 더 이상 고정 포인트가 아니라서 하드코딩된 문구 대신 매번 계산한 값을 그대로 노출한다.
        int streakDay = attendanceService.getCurrentStreakDay(user.getId());
        model.addAttribute("attendanceStreakDay", streakDay);
        model.addAttribute("attendanceNextPoints", attendanceService.pointsForStreakDay(streakDay));
        return "user/mypage";
    }

    // 출석체크(todo.md 요구사항) - 매일 방문 시 연속 출석일수에 따라 포인트 지급(AttendanceService
    // 상단 주석 참고). 하루 한 번만 지급되며, 이미 체크인했으면 checkIn()이 조용히 아무 것도 하지
    // 않는다. 결과(며칠째/몇 포인트인지)를 플래시 메시지에 그대로 보여주기 위해 쿼리 파라미터로 넘긴다.
    @PostMapping("/mypage/attendance")
    public String checkInAttendance(Authentication authentication) {
        User user = userService.getByUsername(authentication.getName());
        AttendanceCheckInResult result = attendanceService.checkIn(user);
        String status = result.isCheckedIn() ? "success" : "already";
        return "redirect:/mypage?attendance=" + status
                + "&streakDay=" + result.getStreakDay()
                + "&points=" + result.getPointsAwarded();
    }

    // 마이페이지 출석 미니 캘린더 팝업(디자인 개선 계획 - 출석체크 캘린더, 2026-09-09 추가) - 알림
    // 읽지않음 카운트(/notifications/unread-count), 설문 위젯(/polls/**)과 동일한 "위젯이 별도 API로
    // 자기 상태를 조회하는" 패턴. year/month를 생략하면 이번 달을 기본값으로 쓴다.
    @GetMapping("/mypage/attendance/calendar")
    @ResponseBody
    public AttendanceCalendarDto attendanceCalendar(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            Authentication authentication) {
        User user = userService.getByUsername(authentication.getName());
        LocalDate today = LocalDate.now();
        int targetYear = year != null ? year : today.getYear();
        int targetMonth = month != null ? month : today.getMonthValue();

        return AttendanceCalendarDto.builder()
                .attendedDates(attendanceService.getAttendedDatesInMonth(user.getId(), targetYear, targetMonth)
                        .stream().map(LocalDate::toString).toList())
                .currentStreakDay(attendanceService.getCurrentStreakDay(user.getId()))
                .checkedInToday(attendanceService.hasCheckedInToday(user.getId()))
                .build();
    }

    // 포인트 내역 화면(todo.md 요구사항) - 적립/소비 이력을 최신순으로 보여준다.
    @GetMapping("/mypage/points")
    public String pointHistory(@RequestParam(defaultValue = "0") int page,
                                @RequestParam(required = false) Integer size,
                                Authentication authentication, Model model) {
        User user = userService.getByUsername(authentication.getName());
        model.addAttribute("logs", userPointService.getHistory(user.getId(), page, PageUtils.normalizeSize(size)));
        return "user/point-history";
    }

    @GetMapping("/mypage/edit")
    public String myPageEditForm(Authentication authentication, Model model) {
        User user = userService.getByUsername(authentication.getName());
        model.addAttribute("updateDto", toUpdateDto(user));
        return "user/mypage-edit";
    }

    // mypage-edit.html 재렌더링이 필요한 여러 실패 케이스(저장 실패/탈퇴 실패/구글 연동 해제
    // 실패)가 전부 같은 DTO 채우기 코드를 반복하던 것을 모아둔 헬퍼.
    private MyPageUpdateDto toUpdateDto(User user) {
        MyPageUpdateDto dto = new MyPageUpdateDto();
        dto.setUsername(user.getUsername());
        dto.setNickname(user.getNickname());
        dto.setEmail(user.getEmail());
        dto.setSchoolName(user.getSchoolName());
        dto.setSchoolCode(user.getSchoolCode());
        dto.setAtptCode(user.getAtptCode());
        dto.setSchoolKind(user.getSchoolKind());
        dto.setGrade(user.getGrade());
        dto.setClassNum(user.getClassNum());
        return dto;
    }

    @PostMapping("/mypage/edit")
    public String myPageEditSubmit(@ModelAttribute("updateDto") MyPageUpdateDto dto,
                                    Authentication authentication,
                                    HttpServletRequest request, HttpServletResponse response,
                                    Model model) {
        try {
            boolean usernameChanged = myPageService.updateProfile(authentication.getName(), dto);
            if (usernameChanged) {
                new SecurityContextLogoutHandler().logout(request, response, authentication);
                return "redirect:/login?updated=true";
            }
            return "redirect:/mypage?updated=true";
        } catch (IllegalArgumentException e) {
            model.addAttribute("errorMessage", e.getMessage());
            return "user/mypage-edit";
        }
    }

    @PostMapping("/mypage/delete")
    public String deleteAccount(@RequestParam(required = false) String password,
                                 Authentication authentication,
                                 HttpServletRequest request, HttpServletResponse response,
                                 Model model) {
        try {
            myPageService.deleteAccount(authentication.getName(), password);
            new SecurityContextLogoutHandler().logout(request, response, authentication);
            return "redirect:/login?accountDeleted=true";
        } catch (IllegalArgumentException e) {
            model.addAttribute("errorMessage", e.getMessage());
            model.addAttribute("updateDto", toUpdateDto(userService.getByUsername(authentication.getName())));
            return "user/mypage-edit";
        }
    }

    // "내 프로필 설정" - 남이 보는 프로필(/users/{id})에 노출되는 소개글 전용 수정 화면.
    // 아이디/비밀번호/학교 정보를 다루는 "내 정보 수정"(/mypage/edit)과는 목적이 달라서 분리했다.
    @GetMapping("/mypage/profile")
    public String myProfileSettingsForm(Authentication authentication, Model model) {
        User user = userService.getByUsername(authentication.getName());
        model.addAttribute("bio", user.getBio());
        // 보유한 칭호/장식 중 무엇을 장착할지 이 화면에서 바로 고를 수 있게 함(사용자 요청) -
        // /shop과 같은 카탈로그를 그대로 재사용(owned/equipped 플래그 포함), 템플릿에서
        // owned == true인 항목만 걸러서 보여준다.
        model.addAttribute("catalog", shopService.getCatalog(user));
        return "user/profile-edit";
    }

    @PostMapping("/mypage/profile")
    public String myProfileSettingsSubmit(@RequestParam(required = false) String bio,
                                           Authentication authentication, Model model) {
        try {
            myPageService.updateBio(authentication.getName(), bio);
            return "redirect:/mypage?updated=true";
        } catch (IllegalArgumentException e) {
            model.addAttribute("errorMessage", e.getMessage());
            model.addAttribute("bio", bio);
            model.addAttribute("catalog", shopService.getCatalog(userService.getByUsername(authentication.getName())));
            return "user/profile-edit";
        }
    }

    // 프로필 사진 업로드/되돌리기 - 소개글(/mypage/profile)과 같은 "남이 보는 내 프로필" 설정
    // 화면 안에 있지만, 파일 업로드라 별도 엔드포인트로 분리했다(폼이 섞이면 사진만 실패했을 때
    // 방금 입력한 소개글까지 같이 날아가 보이는 게 어색해서).
    @PostMapping("/mypage/profile/image")
    public String myProfileImageSubmit(@RequestParam("profileImage") MultipartFile profileImage,
                                        Authentication authentication, Model model) {
        try {
            myPageService.updateProfileImage(authentication.getName(), profileImage);
            return "redirect:/mypage/profile?updated=true";
        } catch (IllegalArgumentException e) {
            User user = userService.getByUsername(authentication.getName());
            model.addAttribute("errorMessage", e.getMessage());
            model.addAttribute("bio", user.getBio());
            model.addAttribute("catalog", shopService.getCatalog(user));
            return "user/profile-edit";
        }
    }

    @PostMapping("/mypage/profile/image/reset")
    public String myProfileImageReset(Authentication authentication) {
        myPageService.resetProfileImage(authentication.getName());
        return "redirect:/mypage/profile?updated=true";
    }

    // 알림 설정 - "남에게 보이는 내 정보"(프로필/계정 정보)와는 성격이 다른 "내가 받을 알림"
    // 설정이라 별도 화면으로 분리했다(위 /mypage/profile, /mypage/edit과 동일한 분리 관례).
    @GetMapping("/mypage/notifications")
    public String notificationSettingsForm(Authentication authentication, Model model) {
        User user = userService.getByUsername(authentication.getName());
        model.addAttribute("commentAlertEnabled", user.isCommentAlertEnabled());
        model.addAttribute("likeAlertEnabled", user.isLikeAlertEnabled());
        model.addAttribute("replyAlertEnabled", user.isReplyAlertEnabled());
        return "user/notification-settings";
    }

    @PostMapping("/mypage/notifications")
    public String notificationSettingsSubmit(@RequestParam(defaultValue = "false") boolean commentAlertEnabled,
                                              @RequestParam(defaultValue = "false") boolean likeAlertEnabled,
                                              @RequestParam(defaultValue = "false") boolean replyAlertEnabled,
                                              Authentication authentication) {
        myPageService.updateNotificationPreferences(authentication.getName(), commentAlertEnabled,
                likeAlertEnabled, replyAlertEnabled);
        return "redirect:/mypage/notifications?updated=true";
    }
}
