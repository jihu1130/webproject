package com.webschool.webschool.post.service;

import com.webschool.webschool.global.util.PageUtils;
import com.webschool.webschool.notification.domain.Notification;
import com.webschool.webschool.notification.service.NotificationService;
import com.webschool.webschool.post.domain.Post;
import com.webschool.webschool.post.domain.PostContestEntry;
import com.webschool.webschool.post.domain.PostContestResult;
import com.webschool.webschool.post.domain.PostContestVote;
import com.webschool.webschool.post.dto.ContestWeekResultDto;
import com.webschool.webschool.post.dto.PostContestEntryDto;
import com.webschool.webschool.post.dto.PostContestResultDto;
import com.webschool.webschool.post.repository.PostContestEntryRepository;
import com.webschool.webschool.post.repository.PostContestResultRepository;
import com.webschool.webschool.post.repository.PostContestVoteRepository;
import com.webschool.webschool.post.repository.PostRepository;
import com.webschool.webschool.user.domain.User;
import com.webschool.webschool.user.repository.UserRepository;
import com.webschool.webschool.user.service.UserPointService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
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

    private static final Logger log = LoggerFactory.getLogger(PostContestService.class);
    private static final int[] PRIZE_POINTS = {30, 20, 10}; // 인덱스 0=1위, 1=2위, 2=3위

    private final PostContestEntryRepository entryRepository;
    private final PostContestVoteRepository voteRepository;
    private final PostContestResultRepository resultRepository;
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

            PostContestResult result = new PostContestResult();
            result.setWeekStart(previousWeekStart);
            result.setRank(rank);
            result.setPost(entry.getPost());
            result.setAuthor(author);
            result.setVoteCount((int) voteCount);
            result.setPrizePoints(prize);
            resultRepository.save(result);
        }
    }

    // 콘테스트 마감 임박 리마인더(todo.md "콘테스트/설문 후속" 항목) - 인앱 알림만(이메일 제외,
    // 사용자 확정), 마이페이지에서 직접 이 알림을 켠 사용자에게만 보낸다(기본 꺼짐 - 옵트인,
    // User.contestDeadlineAlertEnabled 참고). 실제 마감/시상은 여전히 tallyPreviousWeek()가 다음날
    // (월요일) 자정에 처리하고, 이 메서드는 그 전날(일요일) 저녁에 예고만 한다 - "이번 주가 곧
    // 끝난다"는 시점 안내라 특정 후보 신청자로 대상을 좁히지 않고, 이 알림을 원한다고 스스로 설정한
    // 사용자 전체(탈퇴 계정 제외)에게 보낸다.
    @Scheduled(cron = "0 0 20 * * SUN")
    @Transactional
    public void sendDeadlineReminder() {
        List<User> optedIn = userRepository.findAllByOrderByIdAsc().stream()
                .filter(u -> !u.isDeleted() && u.isContestDeadlineAlertEnabled())
                .collect(Collectors.toList());
        for (User user : optedIn) {
            notificationService.notify(user, Notification.Type.CONTEST_DEADLINE_SOON,
                    "이번 주 인기 게시글 콘테스트 마감이 얼마 남지 않았어요! 후보를 신청하거나 투표해보세요.",
                    "/posts/contest");
        }
        log.info("콘테스트 마감 임박 알림 발송 완료 - {}명", optedIn.size());
    }

    // 콘테스트 과거 우승 이력(todo.md "콘테스트/설문 후속" 항목) - 회차(주)별로 묶어서 최신 순으로
    // 보여준다. 회차당 최대 3행뿐이라 DB 페이지 쿼리 대신 PageUtils로 회차 단위 페이지네이션한다
    // (PageUtils 설계 의도와 동일 - 데이터 규모가 작다고 가정).
    public Page<ContestWeekResultDto> getResultHistory(int page, int size) {
        List<PostContestResult> all = resultRepository.findAllByOrderByWeekStartDescRankAsc();

        List<ContestWeekResultDto> weeks = new ArrayList<>();
        LocalDate currentWeek = null;
        List<PostContestResultDto> currentResults = null;
        for (PostContestResult result : all) {
            if (currentWeek == null || !currentWeek.equals(result.getWeekStart())) {
                if (currentWeek != null) {
                    weeks.add(ContestWeekResultDto.builder().weekStart(currentWeek).results(currentResults).build());
                }
                currentWeek = result.getWeekStart();
                currentResults = new ArrayList<>();
            }
            currentResults.add(toResultDto(result));
        }
        if (currentWeek != null) {
            weeks.add(ContestWeekResultDto.builder().weekStart(currentWeek).results(currentResults).build());
        }

        return PageUtils.paginate(weeks, page, size);
    }

    private PostContestResultDto toResultDto(PostContestResult result) {
        return PostContestResultDto.builder()
                .rank(result.getRank())
                .postUuid(result.getPost().getUuid())
                .postTitle(result.getPost().getTitle())
                .authorNickname(result.getAuthor().isDeleted() ? "탈퇴한 사용자" : result.getAuthor().getNickname())
                .voteCount(result.getVoteCount())
                .prizePoints(result.getPrizePoints())
                .build();
    }

    private String truncate(String text) {
        int limit = 40;
        return text.length() > limit ? text.substring(0, limit) + "..." : text;
    }
}
