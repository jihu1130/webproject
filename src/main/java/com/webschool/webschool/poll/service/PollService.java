package com.webschool.webschool.poll.service;

import com.webschool.webschool.poll.domain.Poll;
import com.webschool.webschool.poll.domain.PollOption;
import com.webschool.webschool.poll.domain.PollVote;
import com.webschool.webschool.poll.dto.PollCreateRequest;
import com.webschool.webschool.poll.dto.PollOptionResultDto;
import com.webschool.webschool.poll.dto.PollResultDto;
import com.webschool.webschool.poll.repository.PollOptionRepository;
import com.webschool.webschool.poll.repository.PollRepository;
import com.webschool.webschool.poll.repository.PollVoteRepository;
import com.webschool.webschool.post.domain.Post;
import com.webschool.webschool.post.repository.PostRepository;
import com.webschool.webschool.school.domain.ScheduleComment;
import com.webschool.webschool.school.repository.ScheduleCommentRepository;
import com.webschool.webschool.user.domain.User;
import com.webschool.webschool.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

// 게시글/한마디 첨부형 설문(todo.md 4번 항목). Post/ScheduleComment 양쪽에 붙을 수 있어 poll
// 패키지가 post/school 양쪽을 의존한다(반대 방향 의존은 없음 - post/school 서비스는 poll을 모른다,
// 위젯이 자기 데이터를 스스로 불러오는 기존 컨벤션과 동일하게 화면 쪽에서 별도 API로 조회).
@Service
@RequiredArgsConstructor
public class PollService {

    private static final DateTimeFormatter EXPIRES_AT_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    private static final int MAX_QUESTION_LENGTH = 200;
    private static final int MIN_OPTIONS = 2;
    private static final int MAX_OPTIONS = 10;

    private final PollRepository pollRepository;
    private final PollOptionRepository pollOptionRepository;
    private final PollVoteRepository pollVoteRepository;
    private final PostRepository postRepository;
    private final ScheduleCommentRepository scheduleCommentRepository;
    private final UserRepository userRepository;

    // 게시글/한마디 작성 폼 제출 전에 먼저 호출 - 설문 데이터가 잘못됐는데 본체(게시글/한마디)부터
    // 저장해버리면, 뒤이은 설문 저장 실패로 에러 화면이 뜬 뒤 사용자가 다시 제출할 때 게시글이
    // 중복 생성될 수 있다. 그래서 DB에 아무것도 쓰지 않는 이 검증을 컨트롤러가 본체 저장 전에 먼저
    // 호출해서 그 위험을 없앤다.
    public void validate(PollCreateRequest req) {
        if (isBlank(req)) {
            return;
        }
        validateQuestion(req.getQuestion());
        cleanOptions(req.getOptions());
        parseScope(req.getVisibilityScope());
        parseExpiresAt(req.getExpiresAt());
    }

    @Transactional
    public void createPollForPost(Long postId, String creatorUsername, PollCreateRequest req) {
        if (isBlank(req)) {
            return;
        }
        List<String> options = cleanOptions(req.getOptions());
        validateQuestion(req.getQuestion());

        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("게시물을 찾을 수 없습니다."));
        User creator = userRepository.findByUsername(creatorUsername)
                .orElseThrow(() -> new IllegalArgumentException("사용자 정보를 찾을 수 없습니다."));

        Poll poll = buildPoll(creator, req);
        poll.setPost(post);
        persistPollWithOptions(poll, options);
    }

    @Transactional
    public void createPollForComment(Long scheduleCommentId, String creatorUsername, PollCreateRequest req) {
        if (isBlank(req)) {
            return;
        }
        List<String> options = cleanOptions(req.getOptions());
        validateQuestion(req.getQuestion());

        ScheduleComment comment = scheduleCommentRepository.findById(scheduleCommentId)
                .orElseThrow(() -> new IllegalArgumentException("한마디를 찾을 수 없습니다."));
        User creator = userRepository.findByUsername(creatorUsername)
                .orElseThrow(() -> new IllegalArgumentException("사용자 정보를 찾을 수 없습니다."));

        Poll poll = buildPoll(creator, req);
        poll.setScheduleComment(comment);
        persistPollWithOptions(poll, options);
    }

    public Optional<PollResultDto> findResultByPost(Long postId, String viewerUsername) {
        return pollRepository.findByPost_IdAndDeletedFalse(postId).map(poll -> buildResult(poll, viewerUsername));
    }

    public Optional<PollResultDto> findResultByComment(Long scheduleCommentId, String viewerUsername) {
        return pollRepository.findByScheduleComment_IdAndDeletedFalse(scheduleCommentId).map(poll -> buildResult(poll, viewerUsername));
    }

    public PollResultDto getResult(Long pollId, String viewerUsername) {
        Poll poll = pollRepository.findById(pollId)
                .filter(p -> !p.isDeleted())
                .orElseThrow(() -> new IllegalArgumentException("설문을 찾을 수 없습니다."));
        return buildResult(poll, viewerUsername);
    }

    // 관리자 설문 관리 화면 전용 - getResult()의 "삭제 안 됨" 필터를 걷어낸 버전. 삭제된 설문도
    // 관리자가 상세를 열람할 수 있어야 삭제/복구 여부를 판단할 수 있다. canAccess()의 관리자 예외는
    // 그대로 적용되므로 실제 관리자가 아니면 여전히 접근이 막힌다.
    public PollResultDto getResultForAdmin(Long pollId, String adminUsername) {
        Poll poll = pollRepository.findById(pollId)
                .orElseThrow(() -> new IllegalArgumentException("설문을 찾을 수 없습니다."));
        return buildResult(poll, adminUsername);
    }

    // 한마디 수정 화면에서 기존 설문 질문을 보여주기 위한 가벼운 조회 - getForEdit()가 이미 본인
    // 작성 한마디인지 확인한 뒤에만 호출되므로 여기서 별도 권한 검사를 하지 않는다.
    public Optional<String> findQuestionForComment(Long scheduleCommentId) {
        return pollRepository.findByScheduleComment_IdAndDeletedFalse(scheduleCommentId).map(Poll::getQuestion);
    }

    // 한마디 수정 화면의 "설문 삭제" 체크 처리 - 물리 삭제 대신 소프트 삭제(이 코드베이스의 삭제 전
    // 규칙). 이미 없거나 이미 삭제된 설문이면 조용히 통과(멱등).
    @Transactional
    public void deletePollForComment(Long scheduleCommentId, String username) {
        pollRepository.findByScheduleComment_IdAndDeletedFalse(scheduleCommentId).ifPresent(poll -> {
            if (!poll.getCreator().getUsername().equals(username)) {
                throw new IllegalArgumentException("본인이 작성한 설문만 삭제할 수 있습니다.");
            }
            poll.setDeleted(true);
            poll.setDeletedAt(LocalDateTime.now());
        });
    }

    // 단일선택/복수선택 모두 "새로 고른 것으로 교체" 방식 - 이 사용자의 기존 투표를 지우고 새로 저장한다
    // (QnA 답변 채택의 "새로 채택하면 기존 채택 자동 해제"와 동일한 원자적 교체 패턴). 옵션을 바꿔서 다시
    // 투표하는 것도, 선택을 취소하고 싶을 때도 이 메서드 하나로 처리한다(선택 없이 호출하면 투표 취소).
    @Transactional
    public void vote(Long pollId, List<Long> optionIds, String customOptionText, String username) {
        Poll poll = pollRepository.findById(pollId)
                .filter(p -> !p.isDeleted())
                .orElseThrow(() -> new IllegalArgumentException("설문을 찾을 수 없습니다."));
        User voter = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("사용자 정보를 찾을 수 없습니다."));

        if (!canAccess(poll, voter)) {
            throw new IllegalArgumentException("이 설문에 참여할 수 없습니다.");
        }
        if (poll.isExpired()) {
            throw new IllegalArgumentException("마감된 설문입니다.");
        }

        List<PollOption> options = new ArrayList<>(pollOptionRepository.findByPoll_IdOrderByIdAsc(pollId));
        List<Long> selected = new ArrayList<>(optionIds == null ? List.of() : optionIds);

        if (customOptionText != null && !customOptionText.isBlank()) {
            if (!poll.isAllowCustomOption()) {
                throw new IllegalArgumentException("이 설문은 기타 옵션 추가를 허용하지 않습니다.");
            }
            String trimmed = customOptionText.trim();
            if (trimmed.length() > 100) {
                throw new IllegalArgumentException("옵션은 100자 이내로 입력해주세요.");
            }
            PollOption custom = new PollOption();
            custom.setPoll(poll);
            custom.setLabel(trimmed);
            custom.setCustom(true);
            custom.setAddedBy(voter);
            custom = pollOptionRepository.save(custom);
            options.add(custom);
            selected.add(custom.getId());
        }

        // 기존 투표 정리(취소든 교체든 항상 먼저 비운다) - 삭제와 삽입이 같은 (poll_option_id, voter_id)
        // 유니크 제약을 건드릴 수 있어(같은 옵션을 다시 선택) flush로 삭제를 먼저 커밋해야 삽입이 안전하다.
        List<PollVote> existing = pollVoteRepository.findByOption_Poll_IdAndVoter_Id(pollId, voter.getId());
        pollVoteRepository.deleteAll(existing);
        pollVoteRepository.flush();

        if (selected.isEmpty()) {
            return; // 아무것도 선택 안 하고 제출 = 투표 취소
        }
        if (!poll.isAllowMultiple() && selected.size() > 1) {
            throw new IllegalArgumentException("이 설문은 한 개만 선택할 수 있습니다.");
        }

        Map<Long, PollOption> optionById = options.stream()
                .collect(Collectors.toMap(PollOption::getId, o -> o));
        Set<Long> distinctSelected = Set.copyOf(selected);
        for (Long optionId : distinctSelected) {
            PollOption option = optionById.get(optionId);
            if (option == null) {
                throw new IllegalArgumentException("올바르지 않은 옵션입니다.");
            }
            PollVote vote = new PollVote();
            vote.setOption(option);
            vote.setVoter(voter);
            pollVoteRepository.save(vote);
        }
    }

    private void persistPollWithOptions(Poll poll, List<String> options) {
        Poll saved = pollRepository.save(poll);
        for (String label : options) {
            PollOption option = new PollOption();
            option.setPoll(saved);
            option.setLabel(label);
            pollOptionRepository.save(option);
        }
    }

    private Poll buildPoll(User creator, PollCreateRequest req) {
        Poll poll = new Poll();
        poll.setCreator(creator);
        poll.setQuestion(req.getQuestion().trim());
        poll.setAllowMultiple(req.isAllowMultiple());
        poll.setAllowCustomOption(req.isAllowCustomOption());
        poll.setAnonymous(req.isAnonymous());
        poll.setVisibilityScope(parseScope(req.getVisibilityScope()));
        poll.setSameSchoolOnly(req.isSameSchoolOnly());
        poll.setExpiresAt(parseExpiresAt(req.getExpiresAt()));
        return poll;
    }

    private PollResultDto buildResult(Poll poll, String viewerUsername) {
        User viewer = viewerUsername == null ? null
                : userRepository.findByUsername(viewerUsername).orElse(null);

        if (!canAccess(poll, viewer)) {
            throw new IllegalArgumentException("이 설문을 볼 수 없습니다.");
        }

        List<PollOption> options = pollOptionRepository.findByPoll_IdOrderByIdAsc(poll.getId());
        List<PollVote> allVotes = pollVoteRepository.findByOption_Poll_Id(poll.getId());

        Map<Long, List<PollVote>> votesByOption = allVotes.stream()
                .collect(Collectors.groupingBy(v -> v.getOption().getId()));

        Set<Long> myVotedOptionIds = viewer == null ? Set.of() : allVotes.stream()
                .filter(v -> v.getVoter().getId().equals(viewer.getId()))
                .map(v -> v.getOption().getId())
                .collect(Collectors.toSet());

        List<PollOptionResultDto> optionDtos = options.stream()
                .map(o -> {
                    List<PollVote> votes = votesByOption.getOrDefault(o.getId(), List.of());
                    return PollOptionResultDto.builder()
                            .id(o.getId())
                            .label(o.getLabel())
                            .voteCount(votes.size())
                            .votedByMe(myVotedOptionIds.contains(o.getId()))
                            .voterNicknames(poll.isAnonymous() ? List.of() : votes.stream()
                                    .map(v -> v.getVoter().getNickname())
                                    .collect(Collectors.toList()))
                            .build();
                })
                .collect(Collectors.toList());

        long totalVoters = allVotes.stream().map(v -> v.getVoter().getId()).distinct().count();

        return PollResultDto.builder()
                .id(poll.getId())
                .question(poll.getQuestion())
                .allowMultiple(poll.isAllowMultiple())
                .allowCustomOption(poll.isAllowCustomOption())
                .anonymous(poll.isAnonymous())
                .visibilityScope(poll.getVisibilityScope().name())
                .expiresAt(poll.getExpiresAt() != null ? poll.getExpiresAt().format(EXPIRES_AT_FORMAT) : null)
                .expired(poll.isExpired())
                .totalVoters((int) totalVoters)
                .votedByMe(!myVotedOptionIds.isEmpty())
                .mine(viewer != null && viewer.getId().equals(poll.getCreator().getId()))
                .options(optionDtos)
                .build();
    }

    // 작성자 본인/관리자는 공개범위와 무관하게 항상 접근 가능(탈퇴 계정 닉네임 치환의 "관리자 예외"와
    // 같은 원칙). 그 외에는 visibilityScope 조건을 만족해야 한다. 비로그인 사용자는 항상 불가 -
    // 이 앱은 학교 커뮤니티라 "전체공개"도 "가입 사용자라면 누구나"를 의미하지, 비로그인 방문자까지
    // 포함하지 않는다(post/detail.html에서 로그인 사용자에게만 설문 위젯 자체를 렌더링하므로 실제로는
    // 이 분기까지 오지 않는다 - 여기서는 방어적으로만 처리).
    private boolean canAccess(Poll poll, User viewer) {
        if (viewer == null) {
            return false;
        }
        if (Objects.equals(viewer.getId(), poll.getCreator().getId())) {
            return true;
        }
        if (viewer.isAdmin()) {
            return true;
        }
        return matchesScope(poll, viewer);
    }

    // 게시글에 붙은 설문은 자체 공개범위를 따로 갖지 않고 게시글의 공개범위(Post.Visibility)를
    // 그대로 따른다(사용자 요청) - PRIVATE면 작성자/관리자만(이미 canAccess()의 앞 두 분기에서
    // 걸러짐), PUBLIC/UNLISTED면 이 학교 커뮤니티에 로그인한 누구나(= 게시글 상세를 열람할 수
    // 있는 사람과 동일 조건). 한마디(ScheduleComment)는 대응되는 공개범위 개념이 없으므로
    // Poll.VisibilityScope(같은반/같은학년/전체공개)를 그대로 유지한다.
    private boolean matchesScope(Poll poll, User viewer) {
        if (poll.getPost() != null) {
            return poll.getPost().getVisibility() != Post.Visibility.PRIVATE;
        }
        User creator = poll.getCreator();
        return switch (poll.getVisibilityScope()) {
            case PUBLIC_LINK -> true;
            case SAME_GRADE -> {
                if (!equalsSafe(creator.getGrade(), viewer.getGrade())) {
                    yield false;
                }
                yield !poll.isSameSchoolOnly() || isSameSchool(creator, viewer);
            }
            case SAME_CLASS -> isSameSchool(creator, viewer)
                    && equalsSafe(creator.getGrade(), viewer.getGrade())
                    && equalsSafe(creator.getClassNum(), viewer.getClassNum());
        };
    }

    private boolean isSameSchool(User a, User b) {
        return equalsSafe(a.getSchoolCode(), b.getSchoolCode()) && equalsSafe(a.getAtptCode(), b.getAtptCode());
    }

    private boolean equalsSafe(String a, String b) {
        return a != null && a.equals(b);
    }

    private void validateQuestion(String question) {
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("설문 질문을 입력해주세요.");
        }
        if (question.trim().length() > MAX_QUESTION_LENGTH) {
            throw new IllegalArgumentException("설문 질문은 " + MAX_QUESTION_LENGTH + "자 이내로 입력해주세요.");
        }
    }

    private List<String> cleanOptions(List<String> raw) {
        List<String> cleaned = (raw == null ? List.<String>of() : raw).stream()
                .filter(o -> o != null && !o.isBlank())
                .map(String::trim)
                .distinct()
                .collect(Collectors.toList());
        if (cleaned.size() < MIN_OPTIONS) {
            throw new IllegalArgumentException("설문 옵션은 " + MIN_OPTIONS + "개 이상 입력해주세요.");
        }
        if (cleaned.size() > MAX_OPTIONS) {
            throw new IllegalArgumentException("설문 옵션은 " + MAX_OPTIONS + "개 이하로 입력해주세요.");
        }
        return cleaned;
    }

    private LocalDateTime parseExpiresAt(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(value);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("올바르지 않은 마감 기한입니다.");
        }
    }

    private Poll.VisibilityScope parseScope(String value) {
        if (value == null || value.isBlank()) {
            return Poll.VisibilityScope.SAME_CLASS;
        }
        try {
            return Poll.VisibilityScope.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("올바르지 않은 설문 공개범위입니다.");
        }
    }

    private boolean isBlank(PollCreateRequest req) {
        return req == null || req.getQuestion() == null || req.getQuestion().isBlank();
    }
}
