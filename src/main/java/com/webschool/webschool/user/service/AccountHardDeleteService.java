package com.webschool.webschool.user.service;

import com.webschool.webschool.bugreport.repository.BugReportRepository;
import com.webschool.webschool.notice.repository.NoticeRepository;
import com.webschool.webschool.notification.repository.NotificationRepository;
import com.webschool.webschool.poll.repository.PollOptionRepository;
import com.webschool.webschool.poll.repository.PollRepository;
import com.webschool.webschool.poll.repository.PollVoteRepository;
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
import com.webschool.webschool.school.repository.PersonalEventRepository;
import com.webschool.webschool.school.repository.ScheduleCommentBookmarkRepository;
import com.webschool.webschool.school.repository.ScheduleCommentLikeRepository;
import com.webschool.webschool.school.repository.ScheduleCommentReportRepository;
import com.webschool.webschool.school.repository.ScheduleCommentRepository;
import com.webschool.webschool.user.domain.User;
import com.webschool.webschool.user.repository.AttendanceLogRepository;
import com.webschool.webschool.user.repository.EmailTokenRepository;
import com.webschool.webschool.user.repository.UserBlockRepository;
import com.webschool.webschool.user.repository.UserPenaltyRepository;
import com.webschool.webschool.user.repository.UserPointLogRepository;
import com.webschool.webschool.user.repository.UserRepository;
import com.webschool.webschool.user.repository.UserShopItemRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

// 탈퇴 계정 하드 삭제(todo.md #20, 2026-09-02 결정) - 본인 탈퇴(deletedByAdmin=false) 계정이
// 소프트 삭제 상태로 7일이 지나면 users 행을 실제로 DELETE한다(익명화가 아니라 진짜 삭제 - 사용자
// 확정). 관리자 강제 탈퇴 계정은 제재/신고 이력 때문에 보관 기간을 더 길게 가져갈 가능성이 있어
// 이번 범위에서 제외.
//
// users를 참조하는 FK 29개(2026-09-05 information_schema로 직접 확인, CLAUDE.md의 "29개"와 일치)를
// 두 그룹으로 나눠 처리한다 - A그룹(콘텐츠 보존, FK만 NULL로 끊음: 게시글/댓글/한마디/공지/설문/
// 콘테스트 수상기록/신고/문의/제재 이력)과 B그룹(개인 활동 흔적이라 행 자체를 함께 삭제: 좋아요/
// 북마크/투표/구매내역/포인트내역/출석/토큰/알림/차단관계/개인 캘린더 일정). 상세 분류는 각
// 리포지토리의 detach*()/deleteAllBy*() 메서드 주석 참고. FK 위반을 피하려면 반드시 B그룹 삭제
// → A그룹 NULL-out → users 행 삭제 순서를 지켜야 한다.
@Service
@RequiredArgsConstructor
public class AccountHardDeleteService {

    private static final Logger log = LoggerFactory.getLogger(AccountHardDeleteService.class);
    private static final int RETENTION_DAYS = 7;

    private final UserRepository userRepository;

    // A그룹 - 콘텐츠 보존, FK만 NULL
    private final PostRepository postRepository;
    private final PostCommentRepository postCommentRepository;
    private final ScheduleCommentRepository scheduleCommentRepository;
    private final NoticeRepository noticeRepository;
    private final PollRepository pollRepository;
    private final PollOptionRepository pollOptionRepository;
    private final PostContestResultRepository postContestResultRepository;
    private final PostReportRepository postReportRepository;
    private final CommentReportRepository commentReportRepository;
    private final ScheduleCommentReportRepository scheduleCommentReportRepository;
    private final UserPenaltyRepository userPenaltyRepository;
    private final BugReportRepository bugReportRepository;

    // B그룹 - 개인 활동 흔적, 행 자체를 삭제
    private final PostLikeRepository postLikeRepository;
    private final PostBookmarkRepository postBookmarkRepository;
    private final CommentLikeRepository commentLikeRepository;
    private final CommentBookmarkRepository commentBookmarkRepository;
    private final ScheduleCommentLikeRepository scheduleCommentLikeRepository;
    private final ScheduleCommentBookmarkRepository scheduleCommentBookmarkRepository;
    private final PersonalEventRepository personalEventRepository;
    private final PollVoteRepository pollVoteRepository;
    private final PostContestVoteRepository postContestVoteRepository;
    private final PostContestEntryRepository postContestEntryRepository;
    private final UserShopItemRepository userShopItemRepository;
    private final UserPointLogRepository userPointLogRepository;
    private final AttendanceLogRepository attendanceLogRepository;
    private final EmailTokenRepository emailTokenRepository;
    private final NotificationRepository notificationRepository;
    private final UserBlockRepository userBlockRepository;

    // 매일 새벽 4시 10분 - DB 백업(새벽 3시)/CloudWatch 로그 수집과 겹치지 않는 시간대.
    // PostContestService.tallyPreviousWeek()와 동일한 배치 패턴(cron 직접 명시, 전체를 한 트랜잭션
    // 대신 대상자별로 개별 하드 삭제 - 한 명 실패가 나머지 처리를 막지 않도록 대상자 단위로 분리).
    @Scheduled(cron = "0 10 4 * * *")
    public void hardDeleteExpiredAccounts() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(RETENTION_DAYS);
        List<User> targets = userRepository.findAllByDeletedTrueAndDeletedByAdminFalseAndDeletedAtBefore(cutoff);
        if (targets.isEmpty()) {
            return;
        }
        int success = 0;
        for (User target : targets) {
            try {
                hardDelete(target.getId());
                success++;
            } catch (Exception e) {
                log.error("계정 하드 삭제 실패 - userId={}", target.getId(), e);
            }
        }
        log.info("탈퇴 계정 하드 삭제 배치 완료 - 대상 {}명 중 {}명 처리", targets.size(), success);
    }

    @Transactional
    public void hardDelete(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        // 총관리자는 deleteAccount()에서 애초에 탈퇴가 막혀있어 이 배치 대상이 될 수 없지만,
        // 방어적으로 한 번 더 막는다(다른 경로로 실수로 호출되는 것까지 대비).
        if (user.isSuperAdmin()) {
            throw new IllegalArgumentException("총관리자 계정은 하드 삭제할 수 없습니다.");
        }

        // 1. B그룹 - 개인 활동 흔적 완전 삭제
        postLikeRepository.deleteAllByUserId(userId);
        postBookmarkRepository.deleteAllByUserId(userId);
        commentLikeRepository.deleteAllByUserId(userId);
        commentBookmarkRepository.deleteAllByUserId(userId);
        scheduleCommentLikeRepository.deleteAllByUserId(userId);
        scheduleCommentBookmarkRepository.deleteAllByUserId(userId);
        personalEventRepository.deleteAllByUserId(userId);
        pollVoteRepository.deleteAllByVoterId(userId);
        postContestVoteRepository.deleteAllByVoterId(userId);
        postContestEntryRepository.deleteAllByNominatorId(userId);
        userShopItemRepository.deleteAllByUserId(userId);
        userPointLogRepository.deleteAllByUserId(userId);
        attendanceLogRepository.deleteAllByUserId(userId);
        emailTokenRepository.deleteAllByUserId(userId);
        notificationRepository.deleteAllByRecipientId(userId);
        userBlockRepository.deleteAllByBlockerId(userId);
        userBlockRepository.deleteAllByBlockedId(userId);

        // 2. A그룹 - 콘텐츠/이력 보존, FK만 NULL
        postRepository.detachAuthor(userId);
        postCommentRepository.detachAuthor(userId);
        scheduleCommentRepository.detachUser(userId);
        noticeRepository.detachAuthor(userId);
        pollRepository.detachCreator(userId);
        pollOptionRepository.detachAddedBy(userId);
        postContestResultRepository.detachAuthor(userId);
        postReportRepository.detachReporter(userId);
        commentReportRepository.detachReporter(userId);
        scheduleCommentReportRepository.detachReporter(userId);
        userPenaltyRepository.detachTarget(userId);
        userPenaltyRepository.detachIssuedBy(userId);
        bugReportRepository.detachReporter(userId);

        // 3. 마지막으로 users 행 자체를 삭제
        userRepository.delete(user);
    }
}
