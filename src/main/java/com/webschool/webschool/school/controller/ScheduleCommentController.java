package com.webschool.webschool.school.controller;

import com.webschool.webschool.poll.dto.PollCreateRequest;
import com.webschool.webschool.poll.service.PollService;
import com.webschool.webschool.school.domain.ScheduleComment;
import com.webschool.webschool.school.dto.ScheduleCommentDto;
import com.webschool.webschool.school.dto.ScheduleCommentReportResultDto;
import com.webschool.webschool.school.service.ScheduleCommentService;
import com.webschool.webschool.school.service.SchoolService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// "오늘의 한마디" - 작성/수정 전용 페이지(/school/comments/**)와 캘린더 패널이 쓰는 JSON API
// (/school/api/comments/**: 조회/작성/수정/삭제/신고/좋아요/북마크). 2026-09-28
// SchoolController에서 분리. 번호 주석(4-4, 5~10)은 분리 전 SchoolController 기준 순서다.
@Controller
@RequestMapping("/school")
@RequiredArgsConstructor
public class ScheduleCommentController {

    private final ScheduleCommentService scheduleCommentService;
    private final PollService pollService;

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
    // 자동으로 하이라이트되게 한다. 검증 실패를 그냥 흘려보내면 GlobalExceptionHandler가 에러 화면으로
    // 보내버리므로(입력하던 내용이 날아감) 여기서 직접 잡아서 같은 폼을 에러와 함께 다시 그린다.
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

        // 이 GET은 Feature 3부터 비로그인도 호출 가능(SecurityConfig permitAll) - 익명 요청은
        // Authentication 파라미터 자체가 null로 들어온다(Spring MVC가 Authentication을 Principal로
        // 취급해 request.getUserPrincipal()로 해석하는데, SecurityContextHolderAwareRequestWrapper가
        // 익명 인증은 일부러 null로 감춘다 - AnonymousAuthenticationToken.isAuthenticated()가 true라고
        // 방심하면 안 되는 지점). ScheduleCommentService.getComments()는 이미 currentUsername==null을
        // "비로그인"으로 처리하도록 설계돼 있으므로(toDto()의 mine/likedByMe 등 null 체크 참고) 여기서는
        // null을 그대로 넘기기만 하면 된다.
        String currentUsername = authentication != null ? authentication.getName() : null;
        return scheduleCommentService.getComments(atptCode, schoolCode, parseDate(date), grade, classNm, currentUsername);
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
        return SchoolService.parseYmd(date);
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
}
