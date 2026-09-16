package com.webschool.webschool.post.service;

import com.webschool.webschool.notification.domain.Notification;
import com.webschool.webschool.notification.service.NotificationService;
import com.webschool.webschool.post.domain.Post;
import com.webschool.webschool.post.domain.PostDailyBestResult;
import com.webschool.webschool.post.domain.PostRecommend;
import com.webschool.webschool.post.dto.PostDailyBestResultDto;
import com.webschool.webschool.post.dto.PostRecommendRankDto;
import com.webschool.webschool.post.repository.PostDailyBestResultRepository;
import com.webschool.webschool.post.repository.PostRecommendCount;
import com.webschool.webschool.post.repository.PostRecommendRepository;
import com.webschool.webschool.post.repository.PostRepository;
import com.webschool.webschool.user.domain.User;
import com.webschool.webschool.user.repository.UserRepository;
import com.webschool.webschool.user.service.UserPointService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.time.DayOfWeek;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

// 추천 게시글(2026-09-16, 기존 "인기 게시글 주간 콘테스트"를 대체) - 자유 게시판(FREE) +
// 전체 공개(PUBLIC) 게시글은 본인 신청 없이 자동으로 추천 대상이 되고, 다른 사용자가 추천을 누르면
// PostRecommend가 한 행씩 쌓인다(취소 불가, 본인 글 추천 불가). 일간/주간/월간/전체(초기화 없음)
// 랭킹은 전부 그 시점 기준으로 PostRecommend를 기간별로 집계해서 즉석에서 계산한다 - 예를 들어
// "오늘 3표 받고 내일 2표 받으면" 내일의 일간 집계는 2, 그 주의 주간 집계는 누적 5로 자연히
// 나온다(별도로 카운터를 리셋하는 로직이 없다 - 기간 조건이 바뀌면 집계 결과도 자연히 바뀔 뿐).
@Service
@RequiredArgsConstructor
public class PostRecommendService {

    private static final Logger log = LoggerFactory.getLogger(PostRecommendService.class);
    // 기존 주간 1위 30점을 그대로 가져오되, 주 1회가 아니라 매일 지급되는 만큼 총량이 과하게
    // 늘지 않도록 일간 1위는 그 절반 수준인 15점으로 정했다(사용자가 정확한 액수를 지정하지
    // 않아 판단한 값 - 필요하면 이 상수만 조정하면 됨).
    private static final int DAILY_WINNER_PRIZE_POINTS = 15;

    private final PostRecommendRepository recommendRepository;
    private final PostDailyBestResultRepository dailyBestResultRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final UserPointService userPointService;
    private final NotificationService notificationService;

    public enum Period {
        DAILY, WEEKLY, MONTHLY, ALL_TIME
    }

    @Transactional
    public void recommend(String postUuid, String username) {
        Post post = postRepository.findByUuid(postUuid)
                .orElseThrow(() -> new IllegalArgumentException("게시물을 찾을 수 없습니다."));
        User voter = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("사용자 정보를 찾을 수 없습니다."));

        if (post.isDeleted() || post.isBlind()) {
            throw new IllegalArgumentException("이 게시물은 추천할 수 없습니다.");
        }
        if (post.getCategory() != Post.Category.FREE) {
            throw new IllegalArgumentException("자유 게시판 글만 추천할 수 있습니다.");
        }
        if (post.getVisibility() != Post.Visibility.PUBLIC) {
            throw new IllegalArgumentException("전체 공개 게시글만 추천할 수 있습니다.");
        }
        if (post.getAuthor() != null && post.getAuthor().getId().equals(voter.getId())) {
            throw new IllegalArgumentException("본인 게시물은 추천할 수 없습니다.");
        }
        if (recommendRepository.existsByPost_IdAndVoter_Id(post.getId(), voter.getId())) {
            throw new IllegalArgumentException("이미 추천한 게시물입니다.");
        }

        PostRecommend recommend = new PostRecommend();
        recommend.setPost(post);
        recommend.setVoter(voter);
        recommendRepository.save(recommend);
    }

    // 게시물 상세 페이지의 추천 위젯 - 추천 대상(FREE+PUBLIC)이 아니면 null(위젯 자체를 숨김).
    public PostRecommendRankDto getRecommendInfo(Long postId, String viewerUsername) {
        Post post = postRepository.findById(postId).orElse(null);
        if (post == null || post.getCategory() != Post.Category.FREE
                || post.getVisibility() != Post.Visibility.PUBLIC) {
            return null;
        }
        Long viewerId = resolveViewerId(viewerUsername);
        boolean recommendedByMe = viewerId != null
                && recommendRepository.existsByPost_IdAndVoter_Id(post.getId(), viewerId);
        boolean mine = viewerId != null && post.getAuthor() != null && post.getAuthor().getId().equals(viewerId);
        return PostRecommendRankDto.builder()
                .postUuid(post.getUuid())
                .postTitle(post.getTitle())
                .recommendCount(recommendRepository.countByPost_Id(post.getId()))
                .recommendedByMe(recommendedByMe)
                .mine(mine)
                .build();
    }

    public List<PostRecommendRankDto> getRanking(Period period, String viewerUsername) {
        LocalDateTime start = startOf(period);
        LocalDateTime end = LocalDateTime.now();
        List<PostRecommendCount> counts = recommendRepository.countByPostGroupedInRange(start, end);
        return toRankDtos(counts, viewerUsername);
    }

    private LocalDateTime startOf(Period period) {
        LocalDate today = LocalDate.now();
        return switch (period) {
            case DAILY -> today.atStartOfDay();
            case WEEKLY -> today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).atStartOfDay();
            case MONTHLY -> today.withDayOfMonth(1).atStartOfDay();
            case ALL_TIME -> LocalDateTime.of(2000, 1, 1, 0, 0);
        };
    }

    private List<PostRecommendRankDto> toRankDtos(List<PostRecommendCount> counts, String viewerUsername) {
        if (counts.isEmpty()) {
            return List.of();
        }
        List<Long> postIds = counts.stream().map(PostRecommendCount::getPostId).collect(Collectors.toList());
        Map<Long, Post> postsById = postRepository.findAllById(postIds).stream()
                .collect(Collectors.toMap(Post::getId, Function.identity()));
        Long viewerId = resolveViewerId(viewerUsername);

        List<PostRecommendCount> sorted = counts.stream()
                .filter(c -> postsById.containsKey(c.getPostId()))
                .sorted((a, b) -> Long.compare(b.getCount(), a.getCount()))
                .collect(Collectors.toList());

        List<PostRecommendRankDto> result = new java.util.ArrayList<>();
        int rank = 1;
        for (PostRecommendCount count : sorted) {
            Post post = postsById.get(count.getPostId());
            boolean recommendedByMe = viewerId != null
                    && recommendRepository.existsByPost_IdAndVoter_Id(post.getId(), viewerId);
            boolean mine = viewerId != null && post.getAuthor() != null && post.getAuthor().getId().equals(viewerId);
            result.add(PostRecommendRankDto.builder()
                    .rank(rank++)
                    .postUuid(post.getUuid())
                    .postTitle(post.getTitle())
                    .authorNickname(displayAuthorNickname(post))
                    .recommendCount(count.getCount())
                    .recommendedByMe(recommendedByMe)
                    .mine(mine)
                    .build());
        }
        return result;
    }

    private String displayAuthorNickname(Post post) {
        User author = post.getAuthor();
        return author == null || author.isDeleted() ? "탈퇴한 사용자" : author.getNickname();
    }

    private Long resolveViewerId(String viewerUsername) {
        return viewerUsername == null ? null
                : userRepository.findByUsername(viewerUsername).map(User::getId).orElse(null);
    }

    // 매일 자정, 그 전날(00:00~24:00) 추천을 가장 많이 받은 게시글 1개에 포인트를 지급한다.
    // EditorUploadCleanupService와 동일한 @Scheduled 패턴(@EnableScheduling은
    // WebschoolApplication에 이미 켜져 있음). 일일 획득 한도를 건너뛰는 UserPointService.
    // awardBonus()를 쓴다 - 그날 이미 다른 활동으로 한도를 채웠어도 우승 보상은 항상 전액
    // 지급돼야 하기 때문. existsByResultDate()로 같은 날짜가 이미 집계됐으면 건너뛰어
    // 재실행에도 안전하게 만든다.
    @Scheduled(cron = "0 0 0 * * *")
    @Transactional
    public void tallyPreviousDay() {
        LocalDate previousDay = LocalDate.now().minusDays(1);
        if (dailyBestResultRepository.existsByResultDate(previousDay)) {
            return;
        }

        LocalDateTime dayStart = previousDay.atStartOfDay();
        LocalDateTime dayEnd = dayStart.plusDays(1);
        List<PostRecommendCount> counts = recommendRepository.countByPostGroupedForDay(dayStart, dayEnd);
        if (counts.isEmpty()) {
            return;
        }

        PostRecommendCount winner = counts.stream()
                .max((a, b) -> Long.compare(a.getCount(), b.getCount()))
                .orElseThrow();
        Post post = postRepository.findById(winner.getPostId()).orElse(null);
        if (post == null || post.getAuthor() == null) {
            return;
        }

        User author = post.getAuthor();
        userPointService.awardBonus(author, DAILY_WINNER_PRIZE_POINTS, "오늘의 추천 게시글 1위");
        notificationService.notify(author, Notification.Type.CONTEST_WIN,
                "'" + truncate(post.getTitle()) + "'이(가) 오늘의 추천 게시글 1위(" + winner.getCount()
                        + "표)에 선정돼 " + DAILY_WINNER_PRIZE_POINTS + "포인트를 받았어요!",
                "/posts/" + post.getUuid());

        PostDailyBestResult result = new PostDailyBestResult();
        result.setResultDate(previousDay);
        result.setPost(post);
        result.setAuthor(author);
        result.setRecommendCount((int) winner.getCount());
        result.setPrizePoints(DAILY_WINNER_PRIZE_POINTS);
        dailyBestResultRepository.save(result);
    }

    public Page<PostDailyBestResultDto> getDailyBestHistory(int page, int size) {
        return dailyBestResultRepository.findAllByOrderByResultDateDesc(PageRequest.of(page, size))
                .map(this::toDailyBestDto);
    }

    private PostDailyBestResultDto toDailyBestDto(PostDailyBestResult result) {
        return PostDailyBestResultDto.builder()
                .resultDate(result.getResultDate())
                .postUuid(result.getPost().getUuid())
                .postTitle(result.getPost().getTitle())
                .authorNickname(result.getAuthor() == null || result.getAuthor().isDeleted()
                        ? "탈퇴한 사용자" : result.getAuthor().getNickname())
                .recommendCount(result.getRecommendCount())
                .prizePoints(result.getPrizePoints())
                .build();
    }

    private String truncate(String text) {
        int limit = 40;
        return text.length() > limit ? text.substring(0, limit) + "..." : text;
    }
}
