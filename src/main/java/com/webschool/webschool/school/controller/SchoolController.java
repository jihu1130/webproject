package com.webschool.webschool.school.controller;

import com.webschool.webschool.poll.dto.PollCreateRequest;
import com.webschool.webschool.poll.service.PollService;
import com.webschool.webschool.school.domain.ScheduleComment;
import com.webschool.webschool.school.dto.CalendarEventDto;
import com.webschool.webschool.school.dto.ScheduleCommentDto;
import com.webschool.webschool.school.dto.ScheduleCommentReportResultDto;
import com.webschool.webschool.school.dto.SchoolCalendarDto;
import com.webschool.webschool.school.dto.SchoolSearchResultDto;
import com.webschool.webschool.school.dto.TimetableDto;
import com.webschool.webschool.school.dto.VacationDdayDto;
import com.webschool.webschool.school.service.NeisApiService;
import com.webschool.webschool.school.service.ScheduleCommentService;
import com.webschool.webschool.school.service.SchoolService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Controller
@RequestMapping("/school") // 👈 기본 경로가 /school 로 잡혀있는지 확인!
@RequiredArgsConstructor
public class SchoolController {

    private final NeisApiService neisApiService;
    private final SchoolService schoolService;
    private final ScheduleCommentService scheduleCommentService;
    private final PollService pollService;

    // 1. 캘린더 페이지 요청 (/school/calendar)
    // http://localhost:8888/school/calendar
    @GetMapping("/calendar")
    public String calendarPage() {
        return "school/calendar"; // templates/school/calendar.html 렌더링
    }

    // 2. 시간표 JSON API 요청 (/school/api/timetable)
    @GetMapping("/api/timetable")
    @ResponseBody
    public List<TimetableDto> getTimetableApi(
            @RequestParam(defaultValue = "N10") String atptCode,
            @RequestParam(defaultValue = "8181104") String schoolCode,
            @RequestParam String date,
            @RequestParam(defaultValue = "1") Integer grade,
            @RequestParam(defaultValue = "1") String classNm,
            @RequestParam(required = false) String schoolKind) {

        try {
            return neisApiService.fetchTimetableFromNeis(atptCode, schoolCode, date, grade, classNm, schoolKind);
        } catch (Exception e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    // 3. 학교명(키워드) 검색 API (동명학교 구분을 위해 주소를 함께 반환)
    @GetMapping("/api/search")
    @ResponseBody
    public List<SchoolSearchResultDto> searchSchools(@RequestParam String keyword) {
        return neisApiService.searchSchools(keyword);
    }

    // 3-1. 선택한 학교(+학년)의 실제 반 목록 조회
    @GetMapping("/api/classes")
    @ResponseBody
    public List<String> getClasses(@RequestParam String atptCode,
                                    @RequestParam String schoolCode,
                                    @RequestParam(required = false) String grade) {
        return neisApiService.fetchClassList(atptCode, schoolCode, grade);
    }

    @GetMapping("/api/calendar-details")
    @ResponseBody
    public SchoolCalendarDto getCalendarDetailsApi(
            @RequestParam(defaultValue = "N10") String atptCode,
            @RequestParam(defaultValue = "8181104") String schoolCode,
            @RequestParam String date,
            @RequestParam(defaultValue = "1") Integer grade,
            @RequestParam(defaultValue = "1") String classNm,
            @RequestParam(required = false) String schoolKind) {

        return schoolService.getCalendarDetails(atptCode, schoolCode, date, grade, classNm, schoolKind);
    }

    // 4-1. 캘린더 월 그리드용 학사일정 - 기본은 해당 월(+그리드에 보이는 앞뒤 달
    // 날짜까지) 전체 학사일정을 다 보여준다. keyword를 넘기면 이름에 그 키워드가
    // 포함된 것만 걸러서 보고 싶을 때 쓸 수 있도록 남겨둠(예: "주간"만 보기).
    @GetMapping("/api/calendar-events")
    @ResponseBody
    public List<CalendarEventDto> getCalendarEventsApi(
            @RequestParam(defaultValue = "N10") String atptCode,
            @RequestParam(defaultValue = "8181104") String schoolCode,
            @RequestParam int year,
            @RequestParam int month,
            @RequestParam(defaultValue = "") String keyword) {

        return schoolService.getMonthlyEvents(atptCode, schoolCode, year, month, keyword);
    }

    // 4-2. 일정 이름으로 검색 - 학사일정은 매년 반복되는 이름이 많아서(예:
    // "기말고사") 전체 검색 결과를 다 보여주면 어느 해 것인지 헷갈리므로, 오늘
    // 날짜와 가장 가까운 단 하나만 찾아 반환한다. 프론트는 이 날짜로 캘린더
    // 화면을 이동시키는 용도로 쓴다. 못 찾으면 404.
    @GetMapping("/api/calendar-events/search")
    @ResponseBody
    public ResponseEntity<CalendarEventDto> searchNearestEventApi(
            @RequestParam(defaultValue = "N10") String atptCode,
            @RequestParam(defaultValue = "8181104") String schoolCode,
            @RequestParam String keyword) {

        CalendarEventDto nearest = schoolService.findNearestEvent(atptCode, schoolCode, keyword);
        return nearest != null ? ResponseEntity.ok(nearest) : ResponseEntity.notFound().build();
    }

    // 4-3. 방학 D-Day - 오늘이 방학 중이면 며칠째인지(D+N), 아니면 다가올 방학(식)까지
    // 며칠 남았는지(D-N). 캘린더 페이지에서 학교 선택 시 배지로 보여준다. 못 찾으면 404.
    @GetMapping("/api/vacation-dday")
    @ResponseBody
    public ResponseEntity<VacationDdayDto> getVacationDdayApi(
            @RequestParam(defaultValue = "N10") String atptCode,
            @RequestParam(defaultValue = "8181104") String schoolCode) {

        VacationDdayDto dday = schoolService.getVacationDday(atptCode, schoolCode);
        return dday != null ? ResponseEntity.ok(dday) : ResponseEntity.notFound().build();
    }

    // 4-4. 게시글 본문에 삽입된 "한마디로 바로가기" 임베드 카드의 링크 대상. 한마디는 자체 상세
    // 페이지가 없이 캘린더 날짜별 패널 안에서만 존재하므로, 그 한마디가 속한 학교/날짜/학년/반으로
    // 캘린더를 열어주는 리다이렉트만 제공한다(calendar.js가 highlightComment 파라미터를 읽어 해당
    // 한마디로 스크롤+하이라이트한다). 찾을 수 없으면 그냥 빈 캘린더로 보낸다.
    @GetMapping("/comments/{uuid}")
    public String openComment(@PathVariable String uuid) {
        try {
            Long id = scheduleCommentService.resolveIdByUuid(uuid);
            var comment = scheduleCommentService.findForPermalink(id);
            var school = comment.getSchool();
            // calendar.js의 handleDayClick()이 기대하는 'YYYY-MM-DD' 형식 그대로 넘긴다(내부에서
            // yyyyMMdd로 변환해 API를 호출하므로 여기서 미리 변환할 필요가 없다). highlightComment는
            // 캘린더가 /api/comments로 이미 받아온 목록의 Long id와 매칭하는 값이라 그대로 유지한다.
            String date = comment.getTargetDate().toString();
            return "redirect:" + buildCalendarUrl(school.getAtptOfcdcScCode(), school.getSdSchulCode(),
                    school.getSchoolName(), date, comment.getGrade(), comment.getClassNm(), id);
        } catch (IllegalArgumentException e) {
            return "redirect:/school/calendar";
        }
    }

    // 4-5. 오늘의 한마디 작성 페이지 - 예전엔 캘린더 날짜 패널 안에 작은 리치 에디터를 끼워넣는
    // 인라인 폼이었지만, 사진/동영상/파일/바로가기 카드까지 삽입 가능한 에디터를 좁은 패널 안에
    // 두기엔 답답하다는 요청(2026-08-19)으로 게시글 작성 화면(post/form.html)과 같은 방식의 전용
    // 페이지로 분리했다. 어느 학교/날짜/학년/반에 쓰는 한마디인지는 캘린더에서 이미 선택돼 있으므로
    // 여기선 그 컨텍스트를 읽기 전용으로 보여주고 숨긴 필드로만 들고 다닌다(사용자가 바꿀 수 없음).
    @GetMapping("/comments/new")
    public String newCommentForm(@RequestParam(defaultValue = "N10") String atptCode,
                                  @RequestParam(defaultValue = "8181104") String schoolCode,
                                  @RequestParam(defaultValue = "") String schoolName,
                                  @RequestParam String date,
                                  @RequestParam(defaultValue = "1") String grade,
                                  @RequestParam(defaultValue = "1") String classNm,
                                  Model model) {
        model.addAttribute("mode", "create");
        model.addAttribute("atptCode", atptCode);
        model.addAttribute("schoolCode", schoolCode);
        model.addAttribute("schoolName", schoolName);
        model.addAttribute("date", date);
        model.addAttribute("grade", grade);
        model.addAttribute("classNm", classNm);
        model.addAttribute("dateLabel", formatDateLabel(date));
        model.addAttribute("contentValue", "");
        model.addAttribute("cancelUrl", buildCalendarUrl(atptCode, schoolCode, schoolName, date, grade, classNm, null));
        return "school/comment-form";
    }

    // 페이지 폼 제출 - JSON을 돌려주는 6번 API(createComment)와 달리 성공 시 새로 만든 한마디의
    // 퍼머링크(/school/comments/{id})로 리다이렉트해서(위 4-4) 캘린더로 되돌아가며 방금 쓴 한마디가
    // 자동으로 하이라이트되게 한다. 검증 실패는 클래스 하단의 JSON용 @ExceptionHandler로 보내면 안
    // 되므로(페이지 요청인데 JSON이 내려가 버림) 여기서 직접 잡아서 같은 폼을 에러와 함께 다시 그린다.
    @PostMapping("/comments")
    public String createCommentPage(@RequestParam(defaultValue = "N10") String atptCode,
                                     @RequestParam(defaultValue = "8181104") String schoolCode,
                                     @RequestParam(defaultValue = "") String schoolName,
                                     @RequestParam String date,
                                     @RequestParam(defaultValue = "1") String grade,
                                     @RequestParam(defaultValue = "1") String classNm,
                                     @RequestParam String content,
                                     @RequestParam(value = "pollQuestion", required = false) String pollQuestion,
                                     @RequestParam(value = "pollOptions", required = false) List<String> pollOptions,
                                     @RequestParam(value = "pollAllowMultiple", required = false, defaultValue = "false") boolean pollAllowMultiple,
                                     @RequestParam(value = "pollAllowCustomOption", required = false, defaultValue = "false") boolean pollAllowCustomOption,
                                     @RequestParam(value = "pollAnonymous", required = false, defaultValue = "false") boolean pollAnonymous,
                                     @RequestParam(value = "pollVisibilityScope", required = false) String pollVisibilityScope,
                                     @RequestParam(value = "pollSameSchoolOnly", required = false, defaultValue = "true") boolean pollSameSchoolOnly,
                                     @RequestParam(value = "pollExpiresAt", required = false) String pollExpiresAt,
                                     Authentication authentication, Model model) {
        try {
            PollCreateRequest pollForm = buildPollRequest(pollQuestion, pollOptions, pollAllowMultiple,
                    pollAllowCustomOption, pollAnonymous, pollVisibilityScope, pollSameSchoolOnly, pollExpiresAt);
            // 설문 데이터가 잘못됐으면 한마디부터 저장하기 전에 여기서 먼저 걸러낸다(PostController.
            // create()와 동일한 이유 - 안 그러면 한마디는 이미 만들어진 채로 에러 화면이 뜨고, 다시
            // 제출하면 한마디가 중복 생성될 수 있다).
            pollService.validate(pollForm);
            boolean hasPoll = pollQuestion != null && !pollQuestion.isBlank();
            ScheduleCommentDto dto = scheduleCommentService.createComment(
                    atptCode, schoolCode, LocalDate.parse(date), grade, classNm, authentication.getName(), content, hasPoll);
            pollService.createPollForComment(dto.getId(), authentication.getName(), pollForm);
            return "redirect:/school/comments/" + dto.getUuid();
        } catch (IllegalArgumentException e) {
            model.addAttribute("mode", "create");
            model.addAttribute("atptCode", atptCode);
            model.addAttribute("schoolCode", schoolCode);
            model.addAttribute("schoolName", schoolName);
            model.addAttribute("date", date);
            model.addAttribute("grade", grade);
            model.addAttribute("classNm", classNm);
            model.addAttribute("dateLabel", formatDateLabel(date));
            model.addAttribute("contentValue", content);
            model.addAttribute("cancelUrl", buildCalendarUrl(atptCode, schoolCode, schoolName, date, grade, classNm, null));
            model.addAttribute("errorMessage", e.getMessage());
            return "school/comment-form";
        }
    }

    // 오늘의 한마디 수정 페이지 - PostController.editForm()/update()와 동일한 패턴(본인 작성 글만
    // 진입 가능, 검증 실패 시 같은 폼을 에러와 함께 다시 그림).
    @GetMapping("/comments/{uuid}/edit")
    public String editCommentForm(@PathVariable String uuid, Authentication authentication, Model model) {
        try {
            Long id = scheduleCommentService.resolveIdByUuid(uuid);
            ScheduleComment comment = scheduleCommentService.getForEdit(id, authentication.getName());
            populateEditModel(model, uuid, comment);
            return "school/comment-form";
        } catch (IllegalArgumentException e) {
            return "redirect:/school/comments/" + uuid;
        }
    }

    @PostMapping("/comments/{uuid}/edit")
    public String updateCommentPage(@PathVariable String uuid, @RequestParam String content,
                                     @RequestParam(value = "removePoll", required = false, defaultValue = "false") boolean removePoll,
                                     Authentication authentication, Model model) {
        try {
            Long id = scheduleCommentService.resolveIdByUuid(uuid);
            scheduleCommentService.updateComment(id, authentication.getName(), content);
            if (removePoll) {
                pollService.deletePollForComment(id, authentication.getName());
            }
            return "redirect:/school/comments/" + uuid;
        } catch (IllegalArgumentException e) {
            try {
                Long id = scheduleCommentService.resolveIdByUuid(uuid);
                ScheduleComment comment = scheduleCommentService.getForEdit(id, authentication.getName());
                populateEditModel(model, uuid, comment);
            } catch (IllegalArgumentException lookupFailed) {
                model.addAttribute("mode", "edit");
                model.addAttribute("commentId", uuid);
                model.addAttribute("cancelUrl", "/school/comments/" + uuid);
            }
            model.addAttribute("contentValue", content);
            model.addAttribute("errorMessage", e.getMessage());
            return "school/comment-form";
        }
    }

    private void populateEditModel(Model model, String uuid, ScheduleComment comment) {
        model.addAttribute("mode", "edit");
        model.addAttribute("commentId", uuid);
        model.addAttribute("schoolName", comment.getSchool().getSchoolName());
        model.addAttribute("dateLabel", comment.getTargetDate().format(DateTimeFormatter.ofPattern("yyyy년 M월 d일 (E)", Locale.KOREAN)));
        model.addAttribute("grade", comment.getGrade());
        model.addAttribute("classNm", comment.getClassNm());
        model.addAttribute("contentValue", comment.getContent());
        model.addAttribute("cancelUrl", "/school/comments/" + uuid);
        model.addAttribute("existingPollQuestion", pollService.findQuestionForComment(comment.getId()).orElse(null));
    }

    private String formatDateLabel(String isoDate) {
        try {
            return LocalDate.parse(isoDate).format(DateTimeFormatter.ofPattern("yyyy년 M월 d일 (E)", Locale.KOREAN));
        } catch (Exception e) {
            return isoDate;
        }
    }

    private String buildCalendarUrl(String atptCode, String schoolCode, String schoolName, String date,
                                     String grade, String classNm, Long highlightCommentId) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath("/school/calendar")
                .queryParam("atptCode", atptCode)
                .queryParam("schoolCode", schoolCode)
                .queryParam("schoolName", schoolName)
                .queryParam("date", date)
                .queryParam("grade", grade)
                .queryParam("classNm", classNm);
        if (highlightCommentId != null) {
            builder.queryParam("highlightComment", highlightCommentId);
        }
        return builder.build().encode().toUriString();
    }

    // 5. 날짜별 한마디 댓글 조회 (같은 학년·같은 반끼리만 공유)
    @GetMapping("/api/comments")
    @ResponseBody
    public List<ScheduleCommentDto> getComments(
            @RequestParam(defaultValue = "N10") String atptCode,
            @RequestParam(defaultValue = "8181104") String schoolCode,
            @RequestParam String date,
            @RequestParam(defaultValue = "1") String grade,
            @RequestParam(defaultValue = "1") String classNm,
            Authentication authentication) {

        return scheduleCommentService.getComments(atptCode, schoolCode, parseDate(date), grade, classNm, authentication.getName());
    }

    // 6. 날짜별 한마디 댓글 작성
    @PostMapping("/api/comments")
    @ResponseBody
    public ScheduleCommentDto createComment(
            @RequestParam(defaultValue = "N10") String atptCode,
            @RequestParam(defaultValue = "8181104") String schoolCode,
            @RequestParam String date,
            @RequestParam(defaultValue = "1") String grade,
            @RequestParam(defaultValue = "1") String classNm,
            @RequestParam String content,
            Authentication authentication) {

        return scheduleCommentService.createComment(atptCode, schoolCode, parseDate(date), grade, classNm, authentication.getName(), content, false);
    }

    // 7. 댓글 수정 (본인 작성 댓글만)
    @PutMapping("/api/comments/{id}")
    @ResponseBody
    public ScheduleCommentDto updateComment(@PathVariable Long id, @RequestParam String content, Authentication authentication) {
        return scheduleCommentService.updateComment(id, authentication.getName(), content);
    }

    // 8. 댓글 삭제 (본인 작성 댓글만)
    @DeleteMapping("/api/comments/{id}")
    @ResponseBody
    public Map<String, Object> deleteComment(@PathVariable Long id, Authentication authentication) {
        scheduleCommentService.deleteComment(id, authentication.getName());
        return Map.of("deleted", true);
    }

    // 9. 댓글 신고 - 서로 다른 사용자 3명이 신고하면 자동 블라인드 (PostCommentController와 동일 패턴)
    @PostMapping("/api/comments/{id}/report")
    @ResponseBody
    public ScheduleCommentReportResultDto reportComment(@PathVariable Long id,
                                                          @RequestParam(required = false) String reason,
                                                          Authentication authentication) {
        return scheduleCommentService.reportComment(id, authentication.getName(), reason);
    }

    // 10. 댓글 좋아요/북마크 토글 - PostController/PostCommentController와 동일한 패턴
    @PostMapping("/api/comments/{id}/like")
    @ResponseBody
    public Map<String, Object> likeComment(@PathVariable Long id, Authentication authentication) {
        return scheduleCommentService.toggleLike(id, authentication.getName());
    }

    @PostMapping("/api/comments/{id}/bookmark")
    @ResponseBody
    public Map<String, Object> bookmarkComment(@PathVariable Long id, Authentication authentication) {
        boolean bookmarked = scheduleCommentService.toggleBookmark(id, authentication.getName());
        return Map.of("bookmarked", bookmarked);
    }

    private LocalDate parseDate(String date) {
        return LocalDate.parse(date, DateTimeFormatter.ofPattern("yyyyMMdd"));
    }

    // PostController.buildPollRequest()와 동일한 조립 로직 - 설문 첨부 파라미터를 받는 화면(게시글
    // 작성/한마디 작성) 두 곳뿐이라 공용 유틸로 뽑지 않고 그대로 중복해 둔다.
    private PollCreateRequest buildPollRequest(String question, List<String> options, boolean allowMultiple,
                                                boolean allowCustomOption, boolean anonymous,
                                                String visibilityScope, boolean sameSchoolOnly, String expiresAt) {
        PollCreateRequest req = new PollCreateRequest();
        req.setQuestion(question);
        req.setOptions(options);
        req.setAllowMultiple(allowMultiple);
        req.setAllowCustomOption(allowCustomOption);
        req.setAnonymous(anonymous);
        req.setVisibilityScope(visibilityScope);
        req.setSameSchoolOnly(sameSchoolOnly);
        req.setExpiresAt(expiresAt);
        return req;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseBody
    public org.springframework.http.ResponseEntity<Map<String, String>> handleBadRequest(IllegalArgumentException e) {
        return org.springframework.http.ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }
}