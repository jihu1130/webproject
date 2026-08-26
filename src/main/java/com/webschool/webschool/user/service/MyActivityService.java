package com.webschool.webschool.user.service;

import com.webschool.webschool.global.util.HtmlSanitizer;
import com.webschool.webschool.global.util.PageUtils;
import com.webschool.webschool.post.domain.Post;
import com.webschool.webschool.post.domain.PostComment;
import com.webschool.webschool.post.repository.CommentBookmarkRepository;
import com.webschool.webschool.post.repository.CommentLikeRepository;
import com.webschool.webschool.post.repository.CommentReportRepository;
import com.webschool.webschool.post.repository.PostBookmarkRepository;
import com.webschool.webschool.post.repository.PostCommentRepository;
import com.webschool.webschool.post.repository.PostLikeRepository;
import com.webschool.webschool.post.repository.PostReportRepository;
import com.webschool.webschool.post.repository.PostRepository;
import com.webschool.webschool.school.domain.ScheduleComment;
import com.webschool.webschool.school.repository.ScheduleCommentBookmarkRepository;
import com.webschool.webschool.school.repository.ScheduleCommentLikeRepository;
import com.webschool.webschool.school.repository.ScheduleCommentReportRepository;
import com.webschool.webschool.school.repository.ScheduleCommentRepository;
import com.webschool.webschool.user.dto.MyCommentSummaryDto;
import com.webschool.webschool.user.dto.MyPageStatsDto;
import com.webschool.webschool.user.dto.MyPostSummaryDto;
import com.webschool.webschool.user.dto.MyScheduleCommentSummaryDto;
import com.webschool.webschool.user.domain.User;
import com.webschool.webschool.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

// 마이페이지 "내 활동내역" - 본인이 작성한 게시글/댓글/오늘의 한마디 + 좋아요/북마크한 게시글을 한
// 곳에서 모아보고(검색 포함) 관리할 수 있게 해주는 화면의 조회 로직. 다른 사람의 글을 다루지 않으므로
// (항상 본인 것만) 공개 프로필용 UserProfileService와 달리 익명 카테고리 제외 같은 프라이버시 필터가
// 필요 없다. 검색어 필터링은 관리자 목록 화면들과 동일하게 메모리에서 처리한다(본인 소유 데이터만
// 대상이라 규모가 작다고 가정 - AdminPostService.matches() 등과 동일 패턴, PageUtils 참고).
@Service
@RequiredArgsConstructor
public class MyActivityService {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("MM.dd HH:mm");
    private static final DateTimeFormatter DATE_ONLY = DateTimeFormatter.ofPattern("yyyy.MM.dd");
    private static final int PAGE_SIZE = 10;

    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final PostCommentRepository postCommentRepository;
    private final ScheduleCommentRepository scheduleCommentRepository;
    private final PostBookmarkRepository postBookmarkRepository;
    private final PostLikeRepository postLikeRepository;
    private final ScheduleCommentBookmarkRepository scheduleCommentBookmarkRepository;
    private final ScheduleCommentLikeRepository scheduleCommentLikeRepository;
    private final CommentBookmarkRepository commentBookmarkRepository;
    private final CommentLikeRepository commentLikeRepository;
    private final PostReportRepository postReportRepository;
    private final CommentReportRepository commentReportRepository;
    private final ScheduleCommentReportRepository scheduleCommentReportRepository;

    // 마이페이지 프로필 카드 상단 통계 바(게시글/댓글/받은 좋아요) - 프로필_디자인.md 설계 반영.
    public MyPageStatsDto getStats(String username) {
        Long userId = resolveUserId(username);
        return MyPageStatsDto.builder()
                .postCount(postRepository.countByAuthor_IdAndDeletedFalse(userId))
                .commentCount(postCommentRepository.countByAuthor_IdAndDeletedFalse(userId))
                .likeCount(postRepository.sumLikeCountByAuthor_Id(userId))
                .build();
    }

    public Page<MyPostSummaryDto> getMyPosts(String username, int page, String keyword) {
        Long userId = resolveUserId(username);
        List<MyPostSummaryDto> filtered = postRepository.findByAuthor_IdAndDeletedFalseOrderByCreatedAtDesc(userId)
                .stream()
                .filter(p -> matches(keyword, p.getTitle(), p.getContent()))
                .map(this::toPostDto)
                .collect(Collectors.toList());
        return PageUtils.paginate(filtered, page, PAGE_SIZE);
    }

    public Page<MyCommentSummaryDto> getMyComments(String username, int page, String keyword) {
        Long userId = resolveUserId(username);
        List<MyCommentSummaryDto> filtered = postCommentRepository.findByAuthor_IdAndDeletedFalseOrderByCreatedAtDesc(userId)
                .stream()
                .filter(c -> matches(keyword, c.getContent(), c.getPost().getTitle()))
                .map(this::toCommentDto)
                .collect(Collectors.toList());
        return PageUtils.paginate(filtered, page, PAGE_SIZE);
    }

    public Page<MyScheduleCommentSummaryDto> getMyScheduleComments(String username, int page, String keyword) {
        Long userId = resolveUserId(username);
        List<MyScheduleCommentSummaryDto> filtered = scheduleCommentRepository
                .findByUser_IdAndDeletedFalseOrderByCreatedAtDesc(userId)
                .stream()
                .filter(c -> matches(keyword, c.getContent()))
                .map(this::toScheduleCommentDto)
                .collect(Collectors.toList());
        return PageUtils.paginate(filtered, page, PAGE_SIZE);
    }

    // 마이페이지 "북마크" 탭(게시글 서브탭) - 내가 북마크한 게시글 목록.
    public Page<MyPostSummaryDto> getBookmarkedPosts(String username, int page, String keyword) {
        Long userId = resolveUserId(username);
        List<MyPostSummaryDto> filtered = postBookmarkRepository.findByUser_IdOrderByCreatedAtDesc(userId)
                .stream()
                .map(bookmark -> bookmark.getPost())
                .filter(p -> matches(keyword, p.getTitle(), p.getContent()))
                .map(this::toPostDto)
                .collect(Collectors.toList());
        return PageUtils.paginate(filtered, page, PAGE_SIZE);
    }

    // 마이페이지 "북마크" 탭(한마디 서브탭) - getBookmarkedPosts()와 동일한 패턴, 오늘의 한마디 대상.
    public Page<MyScheduleCommentSummaryDto> getBookmarkedScheduleComments(String username, int page, String keyword) {
        Long userId = resolveUserId(username);
        List<MyScheduleCommentSummaryDto> filtered = scheduleCommentBookmarkRepository.findByUser_IdOrderByCreatedAtDesc(userId)
                .stream()
                .map(bookmark -> bookmark.getComment())
                .filter(c -> matches(keyword, c.getContent()))
                .map(this::toScheduleCommentDto)
                .collect(Collectors.toList());
        return PageUtils.paginate(filtered, page, PAGE_SIZE);
    }

    // 마이페이지 "북마크" 탭(댓글 서브탭) - getBookmarkedPosts()와 동일한 패턴, 댓글 대상.
    public Page<MyCommentSummaryDto> getBookmarkedComments(String username, int page, String keyword) {
        Long userId = resolveUserId(username);
        List<MyCommentSummaryDto> filtered = commentBookmarkRepository.findByUser_IdOrderByCreatedAtDesc(userId)
                .stream()
                .map(bookmark -> bookmark.getComment())
                .filter(c -> matches(keyword, c.getContent(), c.getPost().getTitle()))
                .map(this::toCommentDto)
                .collect(Collectors.toList());
        return PageUtils.paginate(filtered, page, PAGE_SIZE);
    }

    // 마이페이지 "좋아요" 탭(게시글 서브탭) - getBookmarkedPosts()와 동일한 패턴, 좋아요한 게시글만 대상.
    public Page<MyPostSummaryDto> getLikedPosts(String username, int page, String keyword) {
        Long userId = resolveUserId(username);
        List<MyPostSummaryDto> filtered = postLikeRepository.findByUser_IdOrderByCreatedAtDesc(userId)
                .stream()
                .map(like -> like.getPost())
                .filter(p -> matches(keyword, p.getTitle(), p.getContent()))
                .map(this::toPostDto)
                .collect(Collectors.toList());
        return PageUtils.paginate(filtered, page, PAGE_SIZE);
    }

    // 마이페이지 "좋아요" 탭(한마디 서브탭) - getBookmarkedScheduleComments()와 동일한 패턴, 좋아요한
    // 오늘의 한마디 대상. **버그 수정**: 예전엔 이 목록 화면 자체가 없어서 한마디를 좋아요해도 마이페이지
    // 활동내역 "좋아요" 탭에는 게시글만 보이고 한마디는 어디서도 확인할 수 없었다.
    public Page<MyScheduleCommentSummaryDto> getLikedScheduleComments(String username, int page, String keyword) {
        Long userId = resolveUserId(username);
        List<MyScheduleCommentSummaryDto> filtered = scheduleCommentLikeRepository.findByUser_IdOrderByCreatedAtDesc(userId)
                .stream()
                .map(like -> like.getComment())
                .filter(c -> matches(keyword, c.getContent()))
                .map(this::toScheduleCommentDto)
                .collect(Collectors.toList());
        return PageUtils.paginate(filtered, page, PAGE_SIZE);
    }

    // 마이페이지 "좋아요" 탭(댓글 서브탭) - getBookmarkedComments()와 동일한 패턴, 좋아요한 댓글 대상.
    public Page<MyCommentSummaryDto> getLikedComments(String username, int page, String keyword) {
        Long userId = resolveUserId(username);
        List<MyCommentSummaryDto> filtered = commentLikeRepository.findByUser_IdOrderByCreatedAtDesc(userId)
                .stream()
                .map(like -> like.getComment())
                .filter(c -> matches(keyword, c.getContent(), c.getPost().getTitle()))
                .map(this::toCommentDto)
                .collect(Collectors.toList());
        return PageUtils.paginate(filtered, page, PAGE_SIZE);
    }

    // 마이페이지 "신고" 탭(게시글 서브탭) - 내가 신고한 게시글 목록. 신고 취소 시 PostService
    // .cancelReport()로 신고 row 자체가 삭제되므로 그 이후엔 이 목록에서 자연히 빠진다.
    public Page<MyPostSummaryDto> getReportedPosts(String username, int page, String keyword) {
        Long userId = resolveUserId(username);
        List<MyPostSummaryDto> filtered = postReportRepository.findByReporter_IdOrderByCreatedAtDesc(userId)
                .stream()
                .map(report -> report.getPost())
                .filter(p -> matches(keyword, p.getTitle(), p.getContent()))
                .map(this::toPostDto)
                .collect(Collectors.toList());
        return PageUtils.paginate(filtered, page, PAGE_SIZE);
    }

    // 마이페이지 "신고" 탭(한마디 서브탭) - getReportedPosts()와 동일한 패턴.
    public Page<MyScheduleCommentSummaryDto> getReportedScheduleComments(String username, int page, String keyword) {
        Long userId = resolveUserId(username);
        List<MyScheduleCommentSummaryDto> filtered = scheduleCommentReportRepository.findByReporter_IdOrderByCreatedAtDesc(userId)
                .stream()
                .map(report -> report.getComment())
                .filter(c -> matches(keyword, c.getContent()))
                .map(this::toScheduleCommentDto)
                .collect(Collectors.toList());
        return PageUtils.paginate(filtered, page, PAGE_SIZE);
    }

    // 마이페이지 "신고" 탭(댓글 서브탭) - getReportedPosts()와 동일한 패턴.
    public Page<MyCommentSummaryDto> getReportedComments(String username, int page, String keyword) {
        Long userId = resolveUserId(username);
        List<MyCommentSummaryDto> filtered = commentReportRepository.findByReporter_IdOrderByCreatedAtDesc(userId)
                .stream()
                .map(report -> report.getComment())
                .filter(c -> matches(keyword, c.getContent(), c.getPost().getTitle()))
                .map(this::toCommentDto)
                .collect(Collectors.toList());
        return PageUtils.paginate(filtered, page, PAGE_SIZE);
    }

    private boolean matches(String keyword, String... fields) {
        if (keyword == null || keyword.isBlank()) {
            return true;
        }
        String lower = keyword.toLowerCase();
        for (String field : fields) {
            if (field != null && field.toLowerCase().contains(lower)) {
                return true;
            }
        }
        return false;
    }

    private Long resolveUserId(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        return user.getId();
    }

    private MyPostSummaryDto toPostDto(Post p) {
        return MyPostSummaryDto.builder()
                .uuid(p.getUuid())
                .title(p.getTitle())
                .categoryLabel(p.getCategory().getLabel())
                .createdAt(p.getCreatedAt().format(DATE_TIME))
                .viewCount(p.getViewCount())
                .blind(p.isBlind())
                .visibilityLabel(p.getVisibility() == Post.Visibility.PUBLIC ? null : p.getVisibility().getLabel())
                .build();
    }

    private MyCommentSummaryDto toCommentDto(PostComment c) {
        return MyCommentSummaryDto.builder()
                .id(c.getId())
                .content(c.getContent())
                .createdAt(c.getCreatedAt().format(DATE_TIME))
                .postUuid(c.getPost().getUuid())
                .postTitle(c.getPost().getTitle())
                .blind(c.isBlind())
                .build();
    }

    private MyScheduleCommentSummaryDto toScheduleCommentDto(ScheduleComment c) {
        return MyScheduleCommentSummaryDto.builder()
                .id(c.getId())
                // 한마디 본문이 리치 에디터 HTML이라(2026-08-19) 목록 미리보기에서는 태그를 걷어낸
                // 순수 텍스트만 보여준다 - 이미지/동영상까지 그대로 렌더링하면 목록이 너무 무거워짐.
                .content(HtmlSanitizer.toPlainText(c.getContent()))
                .targetDate(c.getTargetDate().format(DATE_ONLY))
                .createdAt(c.getCreatedAt().format(DATE_TIME))
                .schoolName(c.getSchool().getSchoolName())
                .grade(c.getGrade())
                .classNm(c.getClassNm())
                .blind(c.isBlind())
                .build();
    }
}
