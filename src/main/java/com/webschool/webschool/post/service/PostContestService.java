package com.webschool.webschool.post.service;

import com.webschool.webschool.notification.domain.Notification;
import com.webschool.webschool.notification.service.NotificationService;
import com.webschool.webschool.post.domain.Post;
import com.webschool.webschool.post.domain.PostContestEntry;
import com.webschool.webschool.post.domain.PostContestVote;
import com.webschool.webschool.post.dto.PostContestEntryDto;
import com.webschool.webschool.post.repository.PostContestEntryRepository;
import com.webschool.webschool.post.repository.PostContestVoteRepository;
import com.webschool.webschool.post.repository.PostRepository;
import com.webschool.webschool.user.domain.User;
import com.webschool.webschool.user.repository.UserRepository;
import com.webschool.webschool.user.service.UserPointService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// 인기 게시글 주간 콘테스트(todo.md 4번 항목) - 사용자가 자기 게시물을 후보로 신청하고(회차당 인당
// 1개), 다른 사용자들이 투표해서(회차당 인당 1표) 매주 월요일 자정 지난 주 득표 순위 상위 3명에게
// 포인트를 보너스로 지급한다(1위 30/2위 20/3위 10). 사용자와 확정한 요구사항: 본인 후보에는 투표
// 불가, 득표 0표인 순위는 포상하지 않음.
@Service
@RequiredArgsConstructor
public class PostContestService {

    private static final int[] PRIZE_POINTS = {30, 20, 10}; // 인덱스 0=1위, 1=2위, 2=3위

    private final PostContestEntryRepository entryRepository;
    private final PostContestVoteRepository voteRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final UserPointService userPointService;
    private final NotificationService notificationService;

    // 콘테스트 회차 단위 - 월요일 시작 주. 오늘이 월요일이면 오늘 자신을 반환.
    public LocalDate currentWeekStart() {
        return LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    @Transactional
    public void nominate(String postUuid, String username) {
        Post post = postRepository.findByUuid(postUuid)
                .orElseThrow(() -> new IllegalArgumentException("게시물을 찾을 수 없습니다."));
        User nominator = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("사용자 정보를 찾을 수 없습니다."));

        if (post.isDeleted() || post.isBlind()) {
            throw new IllegalArgumentException("이 게시물은 후보로 신청할 수 없습니다.");
        }
        if (!post.getAuthor().getId().equals(nominator.getId())) {
            throw new IllegalArgumentException("본인이 작성한 게시물만 후보로 신청할 수 있습니다.");
        }

        LocalDate weekStart = currentWeekStart();
        if (entryRepository.existsByNominator_IdAndWeekStart(nominator.getId(), weekStart)) {
            throw new IllegalArgumentException("이번 주에 이미 후보를 신청했습니다.");
        }

        PostContestEntry entry = new PostContestEntry();
        entry.setPost(post);
        entry.setNominator(nominator);
        entry.setWeekStart(weekStart);
        entryRepository.save(entry);
    }

    @Transactional
    public void vote(Long entryId, String username) {
        PostContestEntry entry = entryRepository.findById(entryId)
                .orElseThrow(() -> new IllegalArgumentException("후보를 찾을 수 없습니다."));
        User voter = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("사용자 정보를 찾을 수 없습니다."));

        if (entry.getNominator().getId().equals(voter.getId())) {
            throw new IllegalArgumentException("본인이 신청한 후보에는 투표할 수 없습니다.");
        }
        if (voteRepository.existsByVoter_IdAndWeekStart(voter.getId(), entry.getWeekStart())) {
            throw new IllegalArgumentException("이번 주에 이미 투표했습니다.");
        }

        PostContestVote vote = new PostContestVote();
        vote.setEntry(entry);
        vote.setVoter(voter);
        vote.setWeekStart(entry.getWeekStart());
        voteRepository.save(vote);
    }

    public List<PostContestEntryDto> getCurrentWeekEntries(String viewerUsername) {
        LocalDate weekStart = currentWeekStart();
        List<PostContestEntry> entries = entryRepository.findByWeekStartOrderByIdAsc(weekStart);
        List<PostContestVote> votes = voteRepository.findByWeekStart(weekStart);

        Map<Long, List<PostContestVote>> votesByEntry = votes.stream()
                .collect(Collectors.groupingBy(v -> v.getEntry().getId()));

        Long viewerId = resolveViewerId(viewerUsername);

        return entries.stream()
                .map(entry -> toDto(entry, votesByEntry.getOrDefault(entry.getId(), List.of()), viewerId))
                .sorted((a, b) -> Integer.compare(b.getVoteCount(), a.getVoteCount()))
                .collect(Collectors.toList());
    }

    // 게시물 상세 페이지에서 "이번 주 인기글 후보"로 신청된 상태를 바로 보여주고 그 자리에서
    // 투표할 수 있게 하기 위한 조회(원래는 /posts/contest 목록에서만 투표 가능했는데, 글을 읽던
    // 중 바로 투표할 수 있어야 자연스럽다는 피드백으로 추가). 이번 주 후보가 아니면 빈 값.
    public java.util.Optional<PostContestEntryDto> findEntryForPost(Long postId, String viewerUsername) {
        LocalDate weekStart = currentWeekStart();
        Long viewerId = resolveViewerId(viewerUsername);
        return entryRepository.findByPost_IdAndWeekStart(postId, weekStart)
                .map(entry -> toDto(entry, voteRepository.findByEntry_Id(entry.getId()), viewerId));
    }

    private Long resolveViewerId(String viewerUsername) {
        return viewerUsername == null ? null
                : userRepository.findByUsername(viewerUsername).map(User::getId).orElse(null);
    }

    private PostContestEntryDto toDto(PostContestEntry entry, List<PostContestVote> entryVotes, Long viewerId) {
        boolean votedByMe = viewerId != null && entryVotes.stream()
                .anyMatch(v -> v.getVoter().getId().equals(viewerId));
        return PostContestEntryDto.builder()
                .entryId(entry.getId())
                .postUuid(entry.getPost().getUuid())
                .postTitle(entry.getPost().getTitle())
                .authorNickname(entry.getNominator().getNickname())
                .voteCount(entryVotes.size())
                .votedByMe(votedByMe)
                .mine(viewerId != null && entry.getNominator().getId().equals(viewerId))
                .build();
    }

    // 매주 월요일 자정, 지난 주(월~일) 콘테스트를 마감하고 득표 상위 3명에게 포인트를 지급한다.
    // EditorUploadCleanupService가 이 프로젝트의 유일한 기존 @Scheduled 예시라 그 형태(평범한
    // @Service + @Scheduled 메서드, @EnableScheduling은 WebschoolApplication에 이미 켜져 있어
    // 추가 설정 불필요)를 그대로 따른다. 일일 획득 한도를 건너뛰는 UserPointService.awardBonus()를
    // 쓴다 - 그날 이미 다른 활동으로 한도를 채웠어도 우승 보상은 항상 전액 지급돼야 하기 때문.
    @Scheduled(cron = "0 0 0 * * MON")
    @Transactional
    public void tallyPreviousWeek() {
        LocalDate previousWeekStart = currentWeekStart().minusWeeks(1);
        List<PostContestEntry> entries = entryRepository.findByWeekStartOrderByIdAsc(previousWeekStart);
        if (entries.isEmpty()) {
            return;
        }

        List<PostContestVote> votes = voteRepository.findByWeekStart(previousWeekStart);
        Map<Long, Long> voteCountByEntry = votes.stream()
                .collect(Collectors.groupingBy(v -> v.getEntry().getId(), Collectors.counting()));

        List<PostContestEntry> ranked = entries.stream()
                .sorted((a, b) -> Long.compare(
                        voteCountByEntry.getOrDefault(b.getId(), 0L),
                        voteCountByEntry.getOrDefault(a.getId(), 0L)))
                .collect(Collectors.toList());

        for (int i = 0; i < ranked.size() && i < PRIZE_POINTS.length; i++) {
            PostContestEntry entry = ranked.get(i);
            long voteCount = voteCountByEntry.getOrDefault(entry.getId(), 0L);
            if (voteCount <= 0) {
                break; // 득표가 0표인 순위부터는 포상하지 않는다(그 아래 순위도 0표이므로 정렬상 중단해도 안전)
            }

            int rank = i + 1;
            int prize = PRIZE_POINTS[i];
            User author = entry.getNominator();
            userPointService.awardBonus(author, prize, "주간 인기 게시글 " + rank + "위");
            notificationService.notify(author, Notification.Type.CONTEST_WIN,
                    "'" + truncate(entry.getPost().getTitle()) + "'이(가) 이번 주 인기 게시글 " + rank
                            + "위(" + voteCount + "표)에 선정돼 " + prize + "포인트를 받았어요!",
                    "/posts/" + entry.getPost().getUuid());
        }
    }

    private String truncate(String text) {
        int limit = 40;
        return text.length() > limit ? text.substring(0, limit) + "..." : text;
    }
}
