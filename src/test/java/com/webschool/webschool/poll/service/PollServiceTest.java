package com.webschool.webschool.poll.service;

import com.webschool.webschool.global.error.BusinessException;
import com.webschool.webschool.poll.domain.Poll;
import com.webschool.webschool.poll.domain.PollOption;
import com.webschool.webschool.poll.domain.PollVote;
import com.webschool.webschool.poll.dto.PollCreateRequest;
import com.webschool.webschool.poll.dto.PollResultDto;
import com.webschool.webschool.poll.repository.PollOptionRepository;
import com.webschool.webschool.poll.repository.PollRepository;
import com.webschool.webschool.poll.repository.PollVoteRepository;
import com.webschool.webschool.post.domain.Post;
import com.webschool.webschool.post.repository.PostRepository;
import com.webschool.webschool.school.comment.domain.ScheduleComment;
import com.webschool.webschool.school.comment.repository.ScheduleCommentRepository;
import com.webschool.webschool.user.domain.User;
import com.webschool.webschool.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

// 설문(PollService)의 "누가 볼 수 있고 투표할 수 있는가" 회귀 테스트. 설문 API는 게시글/한마디 화면과 따로
// 호출되는 위젯이라(GET /polls/by-post/{id} 등), 본체 화면이 막고 있어도 여기 접근 규칙이 빠지면 API로 그대로 새어 나간다.
@ExtendWith(MockitoExtension.class)
class PollServiceTest {

    private static final Long POLL_ID = 100L;

    @Mock private PollRepository pollRepository;
    @Mock private PollOptionRepository pollOptionRepository;
    @Mock private PollVoteRepository pollVoteRepository;
    @Mock private PostRepository postRepository;
    @Mock private ScheduleCommentRepository scheduleCommentRepository;
    @Mock private UserRepository userRepository;

    @InjectMocks private PollService pollService;

    private User creator;
    private User classmate;      // 같은 학교·학년·반
    private User sameGrade;      // 같은 학교·학년, 다른 반
    private User otherSchool;    // 다른 학교, 같은 학년·반 번호
    private User admin;
    private Poll poll;
    private PollOption optionA;
    private PollOption optionB;
    private final List<PollVote> votes = new ArrayList<>();

    @BeforeEach
    void setUp() {
        creator = user(1L, "creator", "N10", "1000", "3", "1");
        classmate = user(2L, "classmate", "N10", "1000", "3", "1");
        sameGrade = user(3L, "samegrade", "N10", "1000", "3", "2");
        otherSchool = user(4L, "otherschool", "B10", "2000", "3", "1");
        admin = user(5L, "admin", "B10", "2000", "1", "9");
        admin.setRole(User.Role.ROLE_ADMIN);

        poll = new Poll();
        poll.setId(POLL_ID);
        poll.setCreator(creator);
        poll.setQuestion("급식 뭐가 제일 맛있어?");
        poll.setVisibilityScope(Poll.VisibilityScope.SAME_CLASS);
        poll.setScheduleComment(new ScheduleComment());
        lenient().when(pollRepository.findById(POLL_ID)).thenReturn(Optional.of(poll));

        optionA = option(11L, "돈까스");
        optionB = option(12L, "떡볶이");
        lenient().when(pollOptionRepository.findByPoll_IdOrderByIdAsc(POLL_ID)).thenReturn(List.of(optionA, optionB));
        lenient().when(pollVoteRepository.findByOption_Poll_Id(POLL_ID)).thenReturn(votes);
    }

    private User user(Long id, String username, String atptCode, String schoolCode, String grade, String classNum) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setNickname(username + "닉");
        user.setRole(User.Role.ROLE_USER);
        user.setAtptCode(atptCode);
        user.setSchoolCode(schoolCode);
        user.setGrade(grade);
        user.setClassNum(classNum);
        lenient().when(userRepository.findByUsername(username)).thenReturn(Optional.of(user));
        return user;
    }

    private PollOption option(Long id, String label) {
        PollOption option = new PollOption();
        option.setId(id);
        option.setPoll(poll);
        option.setLabel(label);
        return option;
    }

    private void voted(User voter, PollOption option) {
        PollVote vote = new PollVote();
        vote.setVoter(voter);
        vote.setOption(option);
        votes.add(vote);
    }

    private boolean canSee(User viewer) {
        try {
            pollService.getResult(POLL_ID, viewer == null ? null : viewer.getUsername());
            return true;
        } catch (BusinessException e) {
            return false;
        }
    }

    private void attachToPost(Post.Visibility visibility) {
        Post post = new Post();
        post.setVisibility(visibility);
        poll.setScheduleComment(null);
        poll.setPost(post);
    }

    // ---- 한마디 설문: 공개범위(같은 반 / 같은 학년 / 전체) ----

    @Test
    void sameClassScope_onlyClassmatesOfSameSchool() {
        poll.setVisibilityScope(Poll.VisibilityScope.SAME_CLASS);

        assertTrue(canSee(classmate));
        assertFalse(canSee(sameGrade));
        // 학년·반 번호가 같아도 학교가 다르면 "같은 반"이 아니다.
        assertFalse(canSee(otherSchool));
    }

    @Test
    void sameGradeScope_respectsSameSchoolOnlyOption() {
        poll.setVisibilityScope(Poll.VisibilityScope.SAME_GRADE);

        poll.setSameSchoolOnly(true);
        assertTrue(canSee(sameGrade));
        assertFalse(canSee(otherSchool));

        poll.setSameSchoolOnly(false);
        assertTrue(canSee(otherSchool));
    }

    @Test
    void publicScope_anyLoggedInUser_butNeverAnonymous() {
        poll.setVisibilityScope(Poll.VisibilityScope.PUBLIC_LINK);

        assertTrue(canSee(otherSchool));
        assertFalse(canSee(null));
    }

    @Test
    void creatorAndAdminAlwaysSee() {
        poll.setVisibilityScope(Poll.VisibilityScope.SAME_CLASS);

        assertTrue(canSee(creator));
        assertTrue(canSee(admin));
    }

    // 작성자가 하드 삭제되면 반/학년을 비교할 기준이 없다 - 전체공개만 남기고 막는다(NPE로 500이 나도 안 됨).
    @Test
    void creatorHardDeleted_onlyPublicPollsStayVisible() {
        poll.setCreator(null);

        poll.setVisibilityScope(Poll.VisibilityScope.SAME_CLASS);
        assertFalse(canSee(classmate));

        poll.setVisibilityScope(Poll.VisibilityScope.PUBLIC_LINK);
        assertTrue(canSee(classmate));
    }

    // ---- 게시글 설문: 게시글의 열람 조건을 따른다 ----

    @Test
    void postPoll_followsPostVisibility() {
        attachToPost(Post.Visibility.PUBLIC);
        assertTrue(canSee(otherSchool));

        attachToPost(Post.Visibility.UNLISTED);
        assertTrue(canSee(otherSchool));

        attachToPost(Post.Visibility.PRIVATE);
        assertFalse(canSee(otherSchool));
        assertTrue(canSee(creator));
    }

    // 게시글이 신고로 블라인드되거나 삭제되면 상세 화면은 "없는 글"이 된다(PostService.assertReadable, 보안 점검 M2).
    // 설문 API는 순번 id로 따로 호출되므로 여기서도 같이 막지 않으면 질문·선택지·투표자 이름이 그대로 보인다.
    @Test
    void postPoll_hiddenWhenPostIsBlindOrDeleted() {
        attachToPost(Post.Visibility.PUBLIC);

        poll.getPost().setBlind(true);
        assertFalse(canSee(otherSchool));
        assertTrue(canSee(creator));
        assertTrue(canSee(admin));

        poll.getPost().setBlind(false);
        poll.getPost().setDeleted(true);
        assertFalse(canSee(otherSchool));
    }

    @Test
    void commentPoll_hiddenWhenCommentIsBlindOrDeleted() {
        poll.setVisibilityScope(Poll.VisibilityScope.PUBLIC_LINK);

        poll.getScheduleComment().setBlind(true);
        assertFalse(canSee(classmate));
        assertTrue(canSee(creator));

        poll.getScheduleComment().setBlind(false);
        poll.getScheduleComment().setDeleted(true);
        assertFalse(canSee(classmate));
    }

    @Test
    void deletedPoll_isNotFoundForEveryone() {
        poll.setDeleted(true);

        assertFalse(canSee(creator));
        assertFalse(canSee(admin));
    }

    // ---- 익명 투표 ----

    @Test
    void anonymousPoll_hidesVoterNamesButKeepsCounts() {
        voted(classmate, optionA);
        voted(creator, optionA);
        poll.setAnonymous(true);

        PollResultDto result = pollService.getResult(POLL_ID, "classmate");

        assertEquals(2, result.getOptions().get(0).getVoteCount());
        assertTrue(result.getOptions().get(0).getVoterNicknames().isEmpty());
        assertTrue(result.getOptions().get(0).isVotedByMe());
        assertEquals(2, result.getTotalVoters());
    }

    @Test
    void namedPoll_showsVoterNames() {
        voted(classmate, optionA);
        poll.setAnonymous(false);

        PollResultDto result = pollService.getResult(POLL_ID, "creator");

        assertEquals(List.of("classmate닉"), result.getOptions().get(0).getVoterNicknames());
        assertFalse(result.isVotedByMe());
        assertTrue(result.isMine());
    }

    // ---- 투표 ----

    @Test
    void vote_outsideScope_isForbidden() {
        assertThrows(BusinessException.class,
                () -> pollService.vote(POLL_ID, List.of(11L), null, "otherschool"));
        verify(pollVoteRepository, never()).save(any());
    }

    @Test
    void vote_afterDeadline_isRejected() {
        poll.setExpiresAt(LocalDateTime.now().minusMinutes(1));

        assertThrows(IllegalArgumentException.class,
                () -> pollService.vote(POLL_ID, List.of(11L), null, "classmate"));
        verify(pollVoteRepository, never()).save(any());
    }

    @Test
    void vote_multipleOnSingleChoicePoll_isRejected() {
        poll.setAllowMultiple(false);

        assertThrows(IllegalArgumentException.class,
                () -> pollService.vote(POLL_ID, List.of(11L, 12L), null, "classmate"));
        verify(pollVoteRepository, never()).save(any());
    }

    // 다른 설문의 옵션 id를 끼워 넣어 남의 설문 집계를 건드리는 경로.
    @Test
    void vote_withOptionFromAnotherPoll_isRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> pollService.vote(POLL_ID, List.of(999L), null, "classmate"));
        verify(pollVoteRepository, never()).save(any());
    }

    @Test
    void vote_customOption_onlyWhenPollAllowsIt() {
        poll.setAllowCustomOption(false);

        assertThrows(IllegalArgumentException.class,
                () -> pollService.vote(POLL_ID, List.of(), "제육볶음", "classmate"));
        verify(pollOptionRepository, never()).save(any());
    }

    // 같은 옵션 id를 여러 번 보내도 한 표만 들어간다.
    @Test
    void vote_duplicateOptionIds_countOnce() {
        poll.setAllowMultiple(true);

        pollService.vote(POLL_ID, List.of(11L, 11L, 12L), null, "classmate");

        verify(pollVoteRepository, times(2)).save(any());
    }

    @Test
    void vote_withNoSelection_cancelsExistingVotes() {
        pollService.vote(POLL_ID, List.of(), null, "classmate");

        verify(pollVoteRepository).deleteAll(any());
        verify(pollVoteRepository, never()).save(any());
    }

    // ---- 생성 전 검증 ----

    private PollCreateRequest request(String question, List<String> options) {
        PollCreateRequest request = new PollCreateRequest();
        request.setQuestion(question);
        request.setOptions(options);
        return request;
    }

    // 질문이 비어 있으면 "설문 없음"으로 보고 통과시킨다 - 설문은 게시글/한마디의 선택 사항이다.
    @Test
    void validate_blankQuestion_meansNoPoll() {
        assertDoesNotThrow(() -> pollService.validate(request("  ", null)));
        assertDoesNotThrow(() -> pollService.validate(null));
    }

    @Test
    void validate_optionCountAfterTrimAndDedup() {
        // 공백·중복을 걷어내면 1개뿐이다.
        assertThrows(IllegalArgumentException.class,
                () -> pollService.validate(request("질문", List.of("A", " A ", "  "))));

        List<String> eleven = new ArrayList<>();
        for (int i = 0; i < 11; i++) {
            eleven.add("옵션" + i);
        }
        assertThrows(IllegalArgumentException.class, () -> pollService.validate(request("질문", eleven)));

        assertDoesNotThrow(() -> pollService.validate(request("질문", List.of("A", "B"))));
    }

    @Test
    void validate_rejectsBadScopeDeadlineAndLongQuestion() {
        PollCreateRequest badScope = request("질문", List.of("A", "B"));
        badScope.setVisibilityScope("EVERYONE");
        assertThrows(IllegalArgumentException.class, () -> pollService.validate(badScope));

        PollCreateRequest badDeadline = request("질문", List.of("A", "B"));
        badDeadline.setExpiresAt("내일");
        assertThrows(IllegalArgumentException.class, () -> pollService.validate(badDeadline));

        assertThrows(IllegalArgumentException.class,
                () -> pollService.validate(request("가".repeat(201), List.of("A", "B"))));
    }

    // ---- 삭제 ----

    @Test
    void deletePollForComment_onlyByCreator() {
        lenient().when(pollRepository.findByScheduleComment_IdAndDeletedFalse(7L)).thenReturn(Optional.of(poll));

        assertThrows(BusinessException.class, () -> pollService.deletePollForComment(7L, "classmate"));
        assertFalse(poll.isDeleted());

        pollService.deletePollForComment(7L, "creator");
        assertTrue(poll.isDeleted());
    }
}
