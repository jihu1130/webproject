package com.webschool.webschool.user.service;

import com.webschool.webschool.global.util.PageUtils;
import com.webschool.webschool.post.domain.Post;
import com.webschool.webschool.post.domain.PostComment;
import com.webschool.webschool.post.repository.PostBookmarkRepository;
import com.webschool.webschool.post.repository.PostCommentRepository;
import com.webschool.webschool.post.repository.PostLikeRepository;
import com.webschool.webschool.post.repository.PostRepository;
import com.webschool.webschool.school.domain.ScheduleComment;
import com.webschool.webschool.school.repository.ScheduleCommentBookmarkRepository;
import com.webschool.webschool.school.repository.ScheduleCommentRepository;
import com.webschool.webschool.user.dto.MyCommentSummaryDto;
import com.webschool.webschool.user.dto.MyPostSummaryDto;
import com.webschool.webschool.user.dto.MyScheduleCommentSummaryDto;
import com.webschool.webschool.user.entity.User;
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

    // 마이페이지 "북마크" 탭(게시글 서브탭) - 내가 북마크한 게시글 목록. 댓글 북마크는 토글 자체는
    // 지원하지만(게시글 상세에서 아이콘으로 켜고 끌 수 있음) 목록 화면은 아직 없다 - 필요해지면
    // CommentBookmarkRepository에 findByUser_IdOrderByCreatedAtDesc를 추가하고 여기 한마디 서브탭과
    // 동일한 패턴으로 세 번째 서브탭을 붙이면 된다.
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

    // 마이페이지 "좋아요" 탭 - getBookmarkedPosts()와 동일한 패턴, 좋아요한 게시글만 대상(댓글/한마디
    // 좋아요는 북마크와 마찬가지로 이 라운드에서는 목록 화면을 만들지 않음).
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
                .content(c.getContent())
                .targetDate(c.getTargetDate().format(DATE_ONLY))
                .createdAt(c.getCreatedAt().format(DATE_TIME))
                .schoolName(c.getSchool().getSchoolName())
                .grade(c.getGrade())
                .classNm(c.getClassNm())
                .blind(c.isBlind())
                .build();
    }
}
