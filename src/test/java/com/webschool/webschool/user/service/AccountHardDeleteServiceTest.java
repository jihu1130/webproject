package com.webschool.webschool.user.service;

import com.webschool.webschool.bugreport.domain.BugReport;
import com.webschool.webschool.bugreport.repository.BugReportRepository;
import com.webschool.webschool.notice.domain.Notice;
import com.webschool.webschool.notice.repository.NoticeRepository;
import com.webschool.webschool.notification.domain.Notification;
import com.webschool.webschool.notification.repository.NotificationRepository;
import com.webschool.webschool.poll.domain.Poll;
import com.webschool.webschool.poll.domain.PollOption;
import com.webschool.webschool.poll.domain.PollVote;
import com.webschool.webschool.poll.repository.PollOptionRepository;
import com.webschool.webschool.poll.repository.PollRepository;
import com.webschool.webschool.poll.repository.PollVoteRepository;
import com.webschool.webschool.post.domain.CommentBookmark;
import com.webschool.webschool.post.domain.CommentLike;
import com.webschool.webschool.post.domain.CommentReport;
import com.webschool.webschool.post.domain.Post;
import com.webschool.webschool.post.domain.PostBookmark;
import com.webschool.webschool.post.domain.PostComment;
import com.webschool.webschool.post.domain.PostContestEntry;
import com.webschool.webschool.post.domain.PostContestResult;
import com.webschool.webschool.post.domain.PostContestVote;
import com.webschool.webschool.post.domain.PostLike;
import com.webschool.webschool.post.domain.PostReport;
import com.webschool.webschool.post.repository.CommentBookmarkRepository;
import com.webschool.webschool.post.repository.CommentLikeRepository;
import com.webschool.webschool.post.repository.CommentReportRepository;
import com.webschool.webschool.post.repository.PostBookmarkRepository;
import com.webschool.webschool.post.repository.PostCommentRepository;
import com.webschool.webschool.post.repository.PostContestEntryRepository;
import com.webschool.webschool.post.repository.PostContestResultRepository;
import com.webschool.webschool.post.repository.PostContestVoteRepository;
import com.webschool.webschool.post.repository.PostLikeRepository;
import com.webschool.webschool.post.repository.PostReportRepository;
import com.webschool.webschool.post.repository.PostRepository;
import com.webschool.webschool.school.domain.School;
import com.webschool.webschool.school.domain.ScheduleComment;
import com.webschool.webschool.school.domain.ScheduleCommentBookmark;
import com.webschool.webschool.school.domain.ScheduleCommentLike;
import com.webschool.webschool.school.domain.ScheduleCommentReport;
import com.webschool.webschool.school.repository.ScheduleCommentBookmarkRepository;
import com.webschool.webschool.school.repository.ScheduleCommentLikeRepository;
import com.webschool.webschool.school.repository.ScheduleCommentReportRepository;
import com.webschool.webschool.school.repository.ScheduleCommentRepository;
import com.webschool.webschool.school.repository.SchoolRepository;
import com.webschool.webschool.user.domain.AttendanceLog;
import com.webschool.webschool.user.domain.EmailToken;
import com.webschool.webschool.user.domain.ShopItem;
import com.webschool.webschool.user.domain.User;
import com.webschool.webschool.user.domain.UserBlock;
import com.webschool.webschool.user.domain.UserPenalty;
import com.webschool.webschool.user.domain.UserPointLog;
import com.webschool.webschool.user.domain.UserShopItem;
import com.webschool.webschool.user.repository.AttendanceLogRepository;
import com.webschool.webschool.user.repository.EmailTokenRepository;
import com.webschool.webschool.user.repository.ShopItemRepository;
import com.webschool.webschool.user.repository.UserBlockRepository;
import com.webschool.webschool.user.repository.UserPenaltyRepository;
import com.webschool.webschool.user.repository.UserPointLogRepository;
import com.webschool.webschool.user.repository.UserRepository;
import com.webschool.webschool.user.repository.UserShopItemRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

// 탈퇴 계정 하드 삭제(todo.md #20) 엔드투엔드 검증. TestDataSeeder와 동일하게 실제 DB에 직접
// fixture를 심고 AccountHardDeleteService.hardDelete()를 실제로 호출해 확인하는 실행용 테스트 -
// 리포지토리 29개에 새로 추가한 JPQL 벌크 쿼리(@Modifying @Query)는 Mockito로는 문법 오류를 잡을
// 수 없어서(모킹하면 쿼리 자체가 실행되지 않음) 반드시 이렇게 실제 DB로 확인해야 한다.
// A그룹(콘텐츠 보존, FK만 NULL)과 B그룹(개인 활동 흔적, 행 자체 삭제) 전부를 최소 1개씩 실제로
// 심어서 hardDelete() 호출 후 기대한 대로 처리됐는지 검증한다. 실행: `./gradlew test --tests
// "com.webschool.webschool.user.service.AccountHardDeleteServiceTest"`
@SpringBootTest
class AccountHardDeleteServiceTest {

    @Autowired private AccountHardDeleteService accountHardDeleteService;
    @Autowired private UserRepository userRepository;
    @Autowired private SchoolRepository schoolRepository;
    @Autowired private PostRepository postRepository;
    @Autowired private PostCommentRepository postCommentRepository;
    @Autowired private PostLikeRepository postLikeRepository;
    @Autowired private PostBookmarkRepository postBookmarkRepository;
    @Autowired private CommentLikeRepository commentLikeRepository;
    @Autowired private CommentBookmarkRepository commentBookmarkRepository;
    @Autowired private PostReportRepository postReportRepository;
    @Autowired private CommentReportRepository commentReportRepository;
    @Autowired private ScheduleCommentRepository scheduleCommentRepository;
    @Autowired private ScheduleCommentLikeRepository scheduleCommentLikeRepository;
    @Autowired private ScheduleCommentBookmarkRepository scheduleCommentBookmarkRepository;
    @Autowired private ScheduleCommentReportRepository scheduleCommentReportRepository;
    @Autowired private NoticeRepository noticeRepository;
    @Autowired private PollRepository pollRepository;
    @Autowired private PollOptionRepository pollOptionRepository;
    @Autowired private PollVoteRepository pollVoteRepository;
    @Autowired private PostContestEntryRepository postContestEntryRepository;
    @Autowired private PostContestVoteRepository postContestVoteRepository;
    @Autowired private PostContestResultRepository postContestResultRepository;
    @Autowired private UserPenaltyRepository userPenaltyRepository;
    @Autowired private BugReportRepository bugReportRepository;
    @Autowired private EmailTokenRepository emailTokenRepository;
    @Autowired private ShopItemRepository shopItemRepository;
    @Autowired private UserShopItemRepository userShopItemRepository;
    @Autowired private UserPointLogRepository userPointLogRepository;
    @Autowired private AttendanceLogRepository attendanceLogRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private UserBlockRepository userBlockRepository;

    @Test
    void hardDelete_detachesAGroupAndDeletesBGroupThenRemovesUser() {
        // --- 사전 정리: 이전 실행이 실패로 중간에 남긴 계정이 있으면 지우고 새로 시작(재실행 안전) ---
        userRepository.findByUsername("hdtest_target").ifPresent(userRepository::delete);
        userRepository.findByUsername("hdtest_helper").ifPresent(userRepository::delete);

        User target = newUser("hdtest_target", "하드삭제대상");
        User helper = newUser("hdtest_helper", "구경꾼");
        userRepository.save(target);
        userRepository.save(helper);

        School school = schoolRepository.save(School.builder()
                .sdSchulCode("HDTEST001")
                .atptOfcdcScCode("HDTEST")
                .schoolName("하드삭제테스트학교")
                .build());

        // ===== A그룹 픽스처 (콘텐츠 보존, FK만 NULL 기대) =====
        Post post = new Post();
        post.setTitle("하드삭제 테스트 글");
        post.setContent("본문");
        post.setCategory(Post.Category.FREE);
        post.setAuthor(target);
        post.setVisibility(Post.Visibility.PUBLIC);
        postRepository.save(post);

        PostComment comment = new PostComment();
        comment.setPost(post);
        comment.setAuthor(target);
        comment.setContent("댓글");
        postCommentRepository.save(comment);

        PostReport postReport = new PostReport();
        postReport.setPost(post);
        postReport.setReporter(target);
        postReport.setReason("테스트 신고");
        postReportRepository.save(postReport);

        CommentReport commentReport = new CommentReport();
        commentReport.setComment(comment);
        commentReport.setReporter(target);
        commentReportRepository.save(commentReport);

        Notice notice = new Notice();
        notice.setTitle("테스트 공지");
        notice.setContent("공지 본문");
        notice.setAuthor(target);
        noticeRepository.save(notice);

        Poll poll = new Poll();
        poll.setPost(post);
        poll.setCreator(target);
        poll.setQuestion("테스트 설문?");
        pollRepository.save(poll);

        PollOption pollOption = new PollOption();
        pollOption.setPoll(poll);
        pollOption.setLabel("옵션1");
        pollOption.setCustom(true);
        pollOption.setAddedBy(target);
        pollOptionRepository.save(pollOption);

        PostContestResult contestResult = new PostContestResult();
        contestResult.setWeekStart(LocalDate.now());
        contestResult.setRank(1);
        contestResult.setPost(post);
        contestResult.setAuthor(target);
        contestResult.setVoteCount(3);
        contestResult.setPrizePoints(30);
        postContestResultRepository.save(contestResult);

        UserPenalty penaltyAsTarget = new UserPenalty();
        penaltyAsTarget.setTarget(target);
        penaltyAsTarget.setIssuedBy(helper);
        penaltyAsTarget.setType(UserPenalty.Type.WARNING);
        penaltyAsTarget.setReason("테스트 제재");
        userPenaltyRepository.save(penaltyAsTarget);

        UserPenalty penaltyIssuedByTarget = new UserPenalty();
        penaltyIssuedByTarget.setTarget(helper);
        penaltyIssuedByTarget.setIssuedBy(target);
        penaltyIssuedByTarget.setType(UserPenalty.Type.WARNING);
        penaltyIssuedByTarget.setReason("테스트 제재2");
        userPenaltyRepository.save(penaltyIssuedByTarget);

        BugReport bugReport = new BugReport();
        bugReport.setTitle("테스트 문의");
        bugReport.setContent("문의 내용");
        bugReport.setReporter(target);
        bugReportRepository.save(bugReport);

        // ===== B그룹 픽스처 (개인 활동 흔적, 행 자체 삭제 기대) =====
        PostLike postLike = new PostLike();
        postLike.setPost(post);
        postLike.setUser(target);
        postLikeRepository.save(postLike);

        PostBookmark postBookmark = new PostBookmark();
        postBookmark.setPost(post);
        postBookmark.setUser(target);
        postBookmarkRepository.save(postBookmark);

        CommentLike commentLike = new CommentLike();
        commentLike.setComment(comment);
        commentLike.setUser(target);
        commentLikeRepository.save(commentLike);

        CommentBookmark commentBookmark = new CommentBookmark();
        commentBookmark.setComment(comment);
        commentBookmark.setUser(target);
        commentBookmarkRepository.save(commentBookmark);

        ScheduleComment scheduleComment = new ScheduleComment();
        scheduleComment.setSchool(school);
        scheduleComment.setTargetDate(LocalDate.now());
        scheduleComment.setGrade("1");
        scheduleComment.setClassNm("1");
        scheduleComment.setUser(target);
        scheduleComment.setContent("한마디");
        scheduleCommentRepository.save(scheduleComment);

        ScheduleCommentLike scheduleCommentLike = new ScheduleCommentLike();
        scheduleCommentLike.setComment(scheduleComment);
        scheduleCommentLike.setUser(target);
        scheduleCommentLikeRepository.save(scheduleCommentLike);

        ScheduleCommentBookmark scheduleCommentBookmark = new ScheduleCommentBookmark();
        scheduleCommentBookmark.setComment(scheduleComment);
        scheduleCommentBookmark.setUser(target);
        scheduleCommentBookmarkRepository.save(scheduleCommentBookmark);

        ScheduleCommentReport scheduleCommentReport = new ScheduleCommentReport();
        scheduleCommentReport.setComment(scheduleComment);
        scheduleCommentReport.setReporter(target);
        scheduleCommentReportRepository.save(scheduleCommentReport);

        PollVote pollVote = new PollVote();
        pollVote.setOption(pollOption);
        pollVote.setVoter(target);
        pollVoteRepository.save(pollVote);

        PostContestEntry contestEntry = new PostContestEntry();
        contestEntry.setPost(post);
        contestEntry.setNominator(target);
        contestEntry.setWeekStart(LocalDate.now());
        postContestEntryRepository.save(contestEntry);

        PostContestVote contestVote = new PostContestVote();
        contestVote.setEntry(contestEntry);
        contestVote.setVoter(target);
        contestVote.setWeekStart(LocalDate.now());
        postContestVoteRepository.save(contestVote);

        ShopItem shopItem = shopItemRepository.save(newShopItem());
        UserShopItem userShopItem = new UserShopItem();
        userShopItem.setUser(target);
        userShopItem.setShopItem(shopItem);
        userShopItemRepository.save(userShopItem);

        UserPointLog pointLog = new UserPointLog();
        pointLog.setUser(target);
        pointLog.setPoints(5);
        pointLog.setReason("테스트 적립");
        userPointLogRepository.save(pointLog);

        AttendanceLog attendanceLog = new AttendanceLog();
        attendanceLog.setUser(target);
        attendanceLog.setAttendanceDate(LocalDate.now());
        attendanceLogRepository.save(attendanceLog);

        EmailToken emailToken = new EmailToken();
        emailToken.setUser(target);
        emailToken.setToken("hdtest-token-" + System.nanoTime());
        emailToken.setPurpose(EmailToken.Purpose.VERIFY_EMAIL);
        emailToken.setExpiresAt(LocalDateTime.now().plusHours(1));
        emailTokenRepository.save(emailToken);

        Notification notification = new Notification();
        notification.setRecipient(target);
        notification.setType(Notification.Type.COMMENT);
        notification.setMessage("테스트 알림");
        notificationRepository.save(notification);

        UserBlock blockByTarget = new UserBlock();
        blockByTarget.setBlocker(target);
        blockByTarget.setBlocked(helper);
        userBlockRepository.save(blockByTarget);

        UserBlock blockOfTarget = new UserBlock();
        blockOfTarget.setBlocker(helper);
        blockOfTarget.setBlocked(target);
        userBlockRepository.save(blockOfTarget);

        Long targetId = target.getId();

        // ===== 실행 =====
        accountHardDeleteService.hardDelete(targetId);

        // ===== 검증: users 행 자체가 사라졌는지 =====
        assertTrue(userRepository.findById(targetId).isEmpty(), "users 행이 실제로 삭제돼야 한다");

        // ===== 검증: A그룹 - 콘텐츠는 남고 FK만 null =====
        assertTrue(postRepository.findById(post.getId()).isPresent(), "게시글은 삭제되지 않고 남아야 한다");
        assertNull(postRepository.findById(post.getId()).get().getAuthor(), "게시글 작성자 참조는 null이어야 한다");
        assertNull(postCommentRepository.findById(comment.getId()).get().getAuthor());
        assertNull(postReportRepository.findById(postReport.getId()).get().getReporter());
        assertNull(commentReportRepository.findById(commentReport.getId()).get().getReporter());
        assertNull(noticeRepository.findById(notice.getId()).get().getAuthor());
        assertNull(pollRepository.findById(poll.getId()).get().getCreator());
        assertNull(pollOptionRepository.findById(pollOption.getId()).get().getAddedBy());
        assertNull(postContestResultRepository.findById(contestResult.getId()).get().getAuthor());
        assertNull(userPenaltyRepository.findById(penaltyAsTarget.getId()).get().getTarget());
        assertNull(userPenaltyRepository.findById(penaltyIssuedByTarget.getId()).get().getIssuedBy());
        // 대상이 아니었던 쪽(penaltyAsTarget의 issuedBy=helper, penaltyIssuedByTarget의 target=helper)은
        // 그대로 살아있어야 한다 - "관련 없는 행까지 잘못 건드리지 않는지" 확인.
        assertEquals(helper.getId(), userPenaltyRepository.findById(penaltyAsTarget.getId()).get().getIssuedBy().getId());
        assertEquals(helper.getId(), userPenaltyRepository.findById(penaltyIssuedByTarget.getId()).get().getTarget().getId());
        assertNull(bugReportRepository.findById(bugReport.getId()).get().getReporter());

        // ===== 검증: B그룹 - 행 자체가 삭제됐는지 =====
        assertTrue(postLikeRepository.findById(postLike.getId()).isEmpty());
        assertTrue(postBookmarkRepository.findById(postBookmark.getId()).isEmpty());
        assertTrue(commentLikeRepository.findById(commentLike.getId()).isEmpty());
        assertTrue(commentBookmarkRepository.findById(commentBookmark.getId()).isEmpty());
        assertTrue(scheduleCommentLikeRepository.findById(scheduleCommentLike.getId()).isEmpty());
        assertTrue(scheduleCommentBookmarkRepository.findById(scheduleCommentBookmark.getId()).isEmpty());
        assertTrue(pollVoteRepository.findById(pollVote.getId()).isEmpty());
        assertTrue(postContestEntryRepository.findById(contestEntry.getId()).isEmpty());
        assertTrue(postContestVoteRepository.findById(contestVote.getId()).isEmpty());
        assertTrue(userShopItemRepository.findById(userShopItem.getId()).isEmpty());
        assertTrue(userPointLogRepository.findById(pointLog.getId()).isEmpty());
        assertTrue(attendanceLogRepository.findById(attendanceLog.getId()).isEmpty());
        assertTrue(emailTokenRepository.findById(emailToken.getId()).isEmpty());
        assertTrue(notificationRepository.findById(notification.getId()).isEmpty());
        assertTrue(userBlockRepository.findById(blockByTarget.getId()).isEmpty());
        assertTrue(userBlockRepository.findById(blockOfTarget.getId()).isEmpty());

        // 한마디(ScheduleComment)는 A그룹 - 신고와 마찬가지로 콘텐츠 보존
        assertTrue(scheduleCommentRepository.findById(scheduleComment.getId()).isPresent());
        assertNull(scheduleCommentRepository.findById(scheduleComment.getId()).get().getUser());
        assertNull(scheduleCommentReportRepository.findById(scheduleCommentReport.getId()).get().getReporter());

        // ===== 뒷정리: 이 테스트가 만든 잔여 fixture 제거(콘텐츠 보존 대상들 + helper 계정) =====
        pollVoteRepository.deleteById(pollVote.getId());
        pollOptionRepository.deleteById(pollOption.getId());
        pollRepository.deleteById(poll.getId());
        postContestVoteRepository.deleteById(contestVote.getId());
        postContestEntryRepository.deleteById(contestEntry.getId());
        postContestResultRepository.deleteById(contestResult.getId());
        scheduleCommentReportRepository.deleteById(scheduleCommentReport.getId());
        scheduleCommentRepository.deleteById(scheduleComment.getId());
        commentReportRepository.deleteById(commentReport.getId());
        postReportRepository.deleteById(postReport.getId());
        postCommentRepository.deleteById(comment.getId());
        postRepository.deleteById(post.getId());
        noticeRepository.deleteById(notice.getId());
        bugReportRepository.deleteById(bugReport.getId());
        userPenaltyRepository.deleteById(penaltyAsTarget.getId());
        userPenaltyRepository.deleteById(penaltyIssuedByTarget.getId());
        shopItemRepository.deleteById(shopItem.getId());
        schoolRepository.deleteById(school.getId());
        userRepository.deleteById(helper.getId());
    }

    private User newUser(String username, String nickname) {
        User user = new User();
        user.setUsername(username);
        user.setPassword("{noop}test");
        user.setNickname(nickname);
        user.setRole(User.Role.ROLE_USER);
        return user;
    }

    private ShopItem newShopItem() {
        ShopItem item = new ShopItem();
        item.setType(ShopItem.Type.TITLE);
        item.setLabel("테스트 칭호 " + System.nanoTime());
        item.setValue("테스트 칭호");
        item.setPrice(0);
        return item;
    }
}
