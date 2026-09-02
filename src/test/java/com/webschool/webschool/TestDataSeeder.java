package com.webschool.webschool;

import com.webschool.webschool.notice.repository.NoticeRepository;
import com.webschool.webschool.notice.service.NoticeService;
import com.webschool.webschool.post.domain.Post;
import com.webschool.webschool.post.dto.PostCommentDto;
import com.webschool.webschool.post.dto.PostFormDto;
import com.webschool.webschool.post.repository.CommentReportRepository;
import com.webschool.webschool.post.repository.PostBookmarkRepository;
import com.webschool.webschool.post.repository.PostCommentRepository;
import com.webschool.webschool.post.repository.PostLikeRepository;
import com.webschool.webschool.post.repository.PostReportRepository;
import com.webschool.webschool.post.repository.PostRepository;
import com.webschool.webschool.post.service.PostCommentService;
import com.webschool.webschool.post.service.PostService;
import com.webschool.webschool.school.domain.School;
import com.webschool.webschool.school.repository.ScheduleCommentBookmarkRepository;
import com.webschool.webschool.school.repository.ScheduleCommentLikeRepository;
import com.webschool.webschool.school.repository.ScheduleCommentReportRepository;
import com.webschool.webschool.school.repository.ScheduleCommentRepository;
import com.webschool.webschool.school.repository.SchoolRepository;
import com.webschool.webschool.school.service.ScheduleCommentService;
import com.webschool.webschool.user.dto.RegisterDto;
import com.webschool.webschool.user.domain.User;
import com.webschool.webschool.user.repository.UserRepository;
import com.webschool.webschool.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

// 개발용 테스트 데이터 생성기. 실행하면 test1~test5(아이디=비밀번호, 닉네임은 실제 유저처럼
// 보이도록 별도 지정) 계정 + 계정별 한마디 1개, admin/admin(ROLE_ADMIN) 계정, 커뮤니티
// 게시글 10개(자유 4 · 익명 3 · 질의응답 3, 목록 화면이 기본 페이지당 10개를 보여주므로
// 첫 페이지가 바로 꽉 차 보이도록 맞춘 개수) + 공지사항(별도 모델) 1개를 만들고, 신고→
// 자동 블라인드 흐름을 화면에서 바로 확인할 수 있도록 게시글/댓글/한마디 신고 내역까지
// 함께 심는다.
//
// 제목/본문은 "OO 테스트 게시글 1"처럼 시더임이 드러나는 문구 대신, 실제로 그 카테고리를
// 쓸 법한 학생이 썼을 만한 구체적인 내용으로 채운다(2026-09-02) - 커뮤니티 화면을 눈으로
// 확인하거나 데모할 때 진짜 서비스처럼 보이게 하려는 목적. 신고 시나리오로 쓰는 게시글/댓글은
// 일부러 "남 얘기를 하거나(반 애들이 시끄럽다, 옆반 애가 지각했다) 퉁명스러운 답변"처럼
// 신고당할 법한 톤으로 골랐다 - "누가 왜 이걸 신고했는지"가 자연스럽게 설명되도록.
//
// 게시글 작성자도 test1(민서) 한 명에게 몰아주지 않고 test1~test5 다섯 계정에 골고루
// 분산시킨다(2026-09-02) - 한 사람이 커뮤니티 글을 전부 쓴 것처럼 보이면 실제 서비스처럼
// 보이지 않는다는 지적으로 수정. 자유/질의응답 글은 작성자가 화면에 그대로 노출되고, 익명 글은
// 어차피 서버가 닉네임을 "익명"으로 치환해서 보여주지만(PostService.displayNickname 참고)
// 내부 작성자까지 한 명으로 몰리지 않도록 마찬가지로 분산한다.
//
// 좋아요/북마크도 서로 주고받도록 심는다(2026-09-02) - 글/한마디만 있고 반응이 하나도 없으면
// 그것도 시더 티가 나므로, 글마다 좋아요/북마크 개수가 제각각인 것처럼 보이게 계정별로 다르게
// 눌러둔다(자기 글에 자기가 좋아요/북마크를 누르는 경우는 없음). 신고 대상으로 쓴 글(우리반이
// 시끄럽다는 글, 하준의 "옆반 애 지각" 한마디)은 일부러 반응을 안 심었다 - 논란이 된 글에
// 좋아요가 붙어있으면 오히려 부자연스럽다.
//
// 2026-09-02: 학교도 가짜 "테스트중학교"(school code 없음)에서 실제 존재하는 "아산배방중학교"로
// 바꿨다. 실제 NEIS 학교 코드(atptCode=N10/schoolCode=8181104, NEIS schoolInfo API로 확인)를
// 쓰면 캘린더/시간표/급식 연동까지 더미 없이 바로 확인할 수 있고, 마침 이 코드가
// SchoolController의 기본 파라미터값과 정확히 같아서 화면에 별도 파라미터 없이 진입해도
// 시딩된 데이터가 바로 보인다.
//
// 이미 있는 데이터는 건너뛰므로 여러 번 실행해도 안전(멱등).
@SpringBootTest
class TestDataSeeder {

    private static final int USER_COUNT = 5;
    private static final String USERNAME_PREFIX = "test";
    private static final String ADMIN_USERNAME = "admin";
    private static final String ADMIN_PASSWORD = "admin";
    private static final String SCHOOL_NAME = "아산배방중학교";
    private static final String SCHOOL_CODE = "8181104";
    private static final String ATPT_CODE = "N10";
    private static final String SCHOOL_KIND = "중학교";
    private static final String GRADE = "1";
    private static final String CLASS_NUM = "1";

    // 로그인 아이디(test1~test5)와 화면에 보이는 닉네임을 분리 - 아이디는 문서화된 대로 단순하게
    // 유지하고, 닉네임만 실제 유저처럼 보이도록 별도로 지정한다.
    private static final String MINSEO = "test1"; // 닉네임: 민서
    private static final String HAJUN = "test2";  // 닉네임: 하준
    private static final String JIWOO = "test3";  // 닉네임: 지우
    private static final String SEOYEON = "test4"; // 닉네임: 서연
    private static final String DOYUN = "test5";  // 닉네임: 도윤

    private static final List<String> NICKNAMES = List.of("민서", "하준", "지우", "서연", "도윤");

    private record PostSeed(Post.Category category, String authorUsername, String title, String content,
                             List<String> likedBy, List<String> bookmarkedBy) {
    }

    private record ScheduleCommentSeed(String username, String content,
                                        List<String> likedBy, List<String> bookmarkedBy) {
    }

    // 신고 시나리오에서 직접 참조해야 해서 상수로 분리 - 자유 1번은 3명이 신고해 자동 블라인드,
    // 2번은 1명만 신고해서 "신고는 있지만 아직 블라인드는 아닌" 상태를 함께 보여준다.
    private static final String BLIND_TARGET_POST_TITLE = "우리반 애들 너무 시끄러운거 아님?";
    private static final String PARTIAL_REPORT_POST_TITLE = "체육대회 날짜 아시는 분 계세요?";
    private static final String QNA_ANSWER_TARGET_TITLE = "이차함수 최댓값 구하는 법 알려주세요";
    private static final String REPORTED_ANSWER_CONTENT = "그 정도는 스스로 찾아봐야 하는 거 아니에요? 검색하면 바로 나오는데";

    private static final List<PostSeed> POST_SEEDS = List.of(
            new PostSeed(Post.Category.FREE, JIWOO, BLIND_TARGET_POST_TITLE,
                    "쉬는시간마다 너무 시끄러워서 집중이 하나도 안 됨. 다른 반은 안 그런 것 같은데 "
                            + "우리반만 유독 그런듯. 다들 어떻게 생각함?",
                    List.of(), List.of()),
            new PostSeed(Post.Category.FREE, HAJUN, PARTIAL_REPORT_POST_TITLE,
                    "다음달에 체육대회 한다고 들었는데 정확한 날짜 아시는 분 있나요? 반티도 맞춰야 할 것 "
                            + "같아서 미리 알고 싶어요.",
                    List.of(JIWOO, SEOYEON), List.of(SEOYEON)),
            new PostSeed(Post.Category.FREE, MINSEO, "야자 끝나고 다들 집 어떻게 가세요",
                    "저는 버스 타고 가는데 야자 끝나는 시간에 배차간격이 너무 길어서 매번 한참 기다려요. "
                            + "다들 어떻게 다니시나요?",
                    List.of(HAJUN, JIWOO, SEOYEON, DOYUN), List.of(DOYUN)),
            new PostSeed(Post.Category.FREE, HAJUN, "학교 축제 언제쯤 할지 아시는 분 계세요?",
                    "작년에는 11월에 했던 것 같은데 올해는 아직 얘기가 없어서 궁금해요. 부스 신청은 "
                            + "언제부터 받는지도 아시면 알려주세요!",
                    List.of(MINSEO, DOYUN), List.of()),
            new PostSeed(Post.Category.ANONYMOUS, SEOYEON, "시험기간인데 집중이 하나도 안 돼요",
                    "책상에 앉아있긴 한데 자꾸 딴생각만 나고 진도가 하나도 안 나가요. 다들 시험기간에 "
                            + "집중 어떻게 하세요? 좋은 방법 있으면 알려주세요.",
                    List.of(MINSEO, HAJUN, JIWOO), List.of(MINSEO, DOYUN)),
            new PostSeed(Post.Category.ANONYMOUS, DOYUN, "짝사랑하는 애가 반이 바뀌어서 속상해요",
                    "이번에 반 배정 새로 하면서 짝사랑하던 애랑 다른 반이 됐어요. 이제 복도에서 마주치기도 "
                            + "힘들 것 같아서 아쉽네요.",
                    List.of(MINSEO, SEOYEON), List.of()),
            new PostSeed(Post.Category.ANONYMOUS, JIWOO, "선생님한테 질문하는 게 너무 무서워요",
                    "수업시간에 모르는 부분이 있어도 물어보면 눈치 보일까봐 그냥 넘어가게 돼요. 다들 질문 "
                            + "어떻게 하세요?",
                    List.of(HAJUN), List.of(MINSEO)),
            new PostSeed(Post.Category.QNA, DOYUN, QNA_ANSWER_TARGET_TITLE,
                    "y = -2x^2 + 4x + 1 이런 식으로 되어있는 문제에서 최댓값 구하는 법을 잘 모르겠어요. "
                            + "꼭짓점 공식 쓰는 거라고 들었는데 어떻게 적용하는지 예시로 설명해주실 분 계신가요?",
                    List.of(MINSEO, JIWOO, SEOYEON), List.of(HAJUN)),
            new PostSeed(Post.Category.QNA, MINSEO, "동아리 가입 신청 언제까지인가요?",
                    "이번 학기 동아리 새로 가입하고 싶은데 신청 마감이 언제까지인지 아시는 분 계신가요? "
                            + "담당 선생님께 여쭤보려 했는데 못 뵀어요.",
                    List.of(DOYUN), List.of()),
            new PostSeed(Post.Category.QNA, SEOYEON, "기숙사 생활할 때 노트북 꼭 필요한가요?",
                    "다음 학기부터 기숙사 들어가는데 노트북을 가져가야 할지 고민이에요. 과제할 때 많이 "
                            + "쓰나요? 선배님들 조언 부탁드려요.",
                    List.of(MINSEO, HAJUN, DOYUN), List.of(JIWOO))
    );

    // 오늘의 한마디 - 계정(test1~test5) 순서와 1:1 대응. 하준의 한마디는 신고→블라인드 시나리오
    // 대상이라 일부러 좋아요/북마크를 안 심었다.
    private static final List<ScheduleCommentSeed> SCHEDULE_COMMENT_SEEDS = List.of(
            new ScheduleCommentSeed(MINSEO, "오늘따라 유난히 하루가 길게 느껴진다",
                    List.of(HAJUN, JIWOO), List.of()),
            new ScheduleCommentSeed(HAJUN, "옆반 애 오늘도 지각했다던데 진짜 대단하다 ㅋㅋ",
                    List.of(), List.of()),
            new ScheduleCommentSeed(JIWOO, "드디어 금요일이다 이번주도 다들 고생 많았어요",
                    List.of(MINSEO, HAJUN, SEOYEON, DOYUN), List.of(MINSEO)),
            new ScheduleCommentSeed(SEOYEON, "체육대회 연습 때문에 다리 아파 죽겠음",
                    List.of(JIWOO, DOYUN), List.of()),
            new ScheduleCommentSeed(DOYUN, "오늘 저녁 급식 메뉴 뭔지 아시는 분 계세요?",
                    List.of(MINSEO, SEOYEON), List.of(HAJUN))
    );

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PostService postService;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private PostLikeRepository postLikeRepository;

    @Autowired
    private PostBookmarkRepository postBookmarkRepository;

    @Autowired
    private PostCommentService postCommentService;

    @Autowired
    private PostCommentRepository postCommentRepository;

    @Autowired
    private PostReportRepository postReportRepository;

    @Autowired
    private CommentReportRepository commentReportRepository;

    @Autowired
    private ScheduleCommentService scheduleCommentService;

    @Autowired
    private ScheduleCommentRepository scheduleCommentRepository;

    @Autowired
    private ScheduleCommentReportRepository scheduleCommentReportRepository;

    @Autowired
    private ScheduleCommentLikeRepository scheduleCommentLikeRepository;

    @Autowired
    private ScheduleCommentBookmarkRepository scheduleCommentBookmarkRepository;

    @Autowired
    private SchoolRepository schoolRepository;

    @Autowired
    private NoticeService noticeService;

    @Autowired
    private NoticeRepository noticeRepository;

    @Test
    void seedTestData() {
        createSchoolIfAbsent();

        for (int i = 1; i <= USER_COUNT; i++) {
            createUserIfAbsent(USERNAME_PREFIX + i, NICKNAMES.get(i - 1));
        }

        createAdminIfAbsent();

        for (ScheduleCommentSeed seed : SCHEDULE_COMMENT_SEEDS) {
            createScheduleCommentIfAbsent(seed.username(), seed.content());
        }

        for (PostSeed seed : POST_SEEDS) {
            createPostIfAbsent(seed);
        }

        createNoticeIfAbsent();

        seedReportScenarios();
        seedEngagement();
    }

    // ScheduleCommentService.findOrCreateSchool()에 학교 생성을 맡기면 이름이 "우리 학교"로
    // 고정돼버리므로(school code만 알고 실제 이름은 모르는 상태로 자동 생성하는 헬퍼), 실제
    // 학교명이 화면에 그대로 보이도록 여기서 먼저 실제 이름으로 심어둔다.
    private void createSchoolIfAbsent() {
        if (schoolRepository.findBySdSchulCode(SCHOOL_CODE).isPresent()) {
            return;
        }

        schoolRepository.save(School.builder()
                .sdSchulCode(SCHOOL_CODE)
                .atptOfcdcScCode(ATPT_CODE)
                .schoolName(SCHOOL_NAME)
                .build());
    }

    private void createUserIfAbsent(String username, String nickname) {
        if (userRepository.existsByUsername(username)) {
            return;
        }

        RegisterDto dto = new RegisterDto();
        dto.setUsername(username);
        dto.setPassword(username);
        dto.setConfirmPassword(username);
        dto.setNickname(nickname);
        dto.setEmail(username + "@test.local");
        dto.setSchoolName(SCHOOL_NAME);
        dto.setSchoolCode(SCHOOL_CODE);
        dto.setAtptCode(ATPT_CODE);
        dto.setSchoolKind(SCHOOL_KIND);
        dto.setGrade(GRADE);
        dto.setClassNum(CLASS_NUM);

        userService.register(dto);
    }

    private void createScheduleCommentIfAbsent(String username, String content) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalStateException(username + " 계정을 찾을 수 없습니다."));

        boolean exists = !scheduleCommentRepository
                .findByUser_IdAndDeletedFalseOrderByCreatedAtDesc(user.getId()).isEmpty();
        if (exists) {
            return;
        }

        scheduleCommentService.createComment(ATPT_CODE, SCHOOL_CODE, LocalDate.now(), GRADE, CLASS_NUM,
                username, content, false);
    }

    // admin 계정이 없으면 회원가입시키고, 있으면 기존 계정을 그대로 쓴다. UserService.register()는
    // 항상 ROLE_USER로 고정하므로(관리자 승격 UI 없음), SuperAdminSeeder와 동일하게 리포지토리를
    // 직접 써서 우회해 ROLE_ADMIN + canManageNotices=true로 승격한다 - NoticeService.createNotice()가
    // isSuperAdmin() 또는 canManageNotices==true를 요구하기 때문. 계정 존재 여부와 무관하게 매번
    // 승격 상태를 보장해야 한다(이미 있는 admin 행이 과거에 ROLE_USER로만 만들어졌을 수 있음).
    // 총관리자(ROLE_SUPER_ADMIN) 승격은 기존 설계대로 SuperAdminSeeder의 책임으로 남겨둔다.
    private void createAdminIfAbsent() {
        if (!userRepository.existsByUsername(ADMIN_USERNAME)) {
            RegisterDto dto = new RegisterDto();
            dto.setUsername(ADMIN_USERNAME);
            dto.setPassword(ADMIN_PASSWORD);
            dto.setConfirmPassword(ADMIN_PASSWORD);
            dto.setNickname(ADMIN_USERNAME);
            dto.setEmail(ADMIN_USERNAME + "@test.local");
            dto.setSchoolName(SCHOOL_NAME);
            dto.setSchoolCode(SCHOOL_CODE);
            dto.setAtptCode(ATPT_CODE);
            dto.setSchoolKind(SCHOOL_KIND);
            dto.setGrade(GRADE);
            dto.setClassNum(CLASS_NUM);

            userService.register(dto);
        }

        User admin = userRepository.findByUsername(ADMIN_USERNAME)
                .orElseThrow(() -> new IllegalStateException("admin 계정 생성에 실패했습니다."));
        if (admin.getRole() != User.Role.ROLE_ADMIN && admin.getRole() != User.Role.ROLE_SUPER_ADMIN) {
            admin.setRole(User.Role.ROLE_ADMIN);
        }
        admin.setCanManageNotices(true);
        userRepository.save(admin);
    }

    private void createNoticeIfAbsent() {
        String title = "2학기 학사일정 안내";

        boolean exists = noticeRepository.findAllByDeletedFalseOrderByCreatedAtDesc().stream()
                .anyMatch(n -> n.getTitle().equals(title));
        if (exists) {
            return;
        }

        noticeService.createNotice(ADMIN_USERNAME, title,
                "2학기 학사일정이 확정되어 안내드립니다. 중간고사는 10월 중, 기말고사는 12월 중 진행될 "
                        + "예정이며 세부 일정은 추후 학교 홈페이지 공지사항을 통해 다시 안내드리겠습니다. "
                        + "궁금한 점은 담임 선생님께 문의 바랍니다.");
    }

    private void createPostIfAbsent(PostSeed seed) {
        boolean exists = postRepository.findAllByDeletedFalseOrderByCreatedAtDesc().stream()
                .anyMatch(p -> p.getTitle().equals(seed.title()));
        if (exists) {
            return;
        }

        PostFormDto form = new PostFormDto();
        form.setTitle(seed.title());
        form.setContent(seed.content());
        form.setCategory(seed.category().name());

        postService.createPost(seed.authorUsername(), form, false);
    }

    // 신고→자동 블라인드 흐름(게시글/댓글/한마디 3곳 모두 동일 패턴, CLAUDE.md 참고)을 관리자
    // 화면에서 바로 확인할 수 있도록 신고 내역을 심는다. 자기 글은 신고할 수 없고 같은 사람이
    // 같은 대상을 두 번 신고할 수도 없으므로(각 서비스의 reportXxx() 검증), 신고자 후보에서
    // 작성자 본인은 항상 제외한다(reportPostIfNeeded 등이 방어적으로 한 번 더 걸러낸다).
    // 3명이 신고한 대상은 자동 블라인드되고, 1명만 신고한 대상은 관리자 화면에서 "신고는
    // 있지만 아직 블라인드는 아닌" 상태도 함께 확인할 수 있다.
    private void seedReportScenarios() {
        // 우리반이 시끄럽다는 글(지우 작성) - 저격당했다고 느낀 다른 반 학생들이 신고.
        Long blindTargetPostId = findPostIdByTitle(BLIND_TARGET_POST_TITLE);
        reportPostIfNeeded(blindTargetPostId, JIWOO,
                "우리반을 저격하는 것 같아서 신고합니다.", List.of(MINSEO, HAJUN, SEOYEON));

        // 체육대회 날짜를 묻는 글(하준 작성) - 실수로 신고한 경우처럼, 블라인드까지는 안 가는
        // "신고 1건" 상태를 보여준다.
        Long partialReportPostId = findPostIdByTitle(PARTIAL_REPORT_POST_TITLE);
        reportPostIfNeeded(partialReportPostId, HAJUN,
                "질문 게시판이 아니라 자유 게시판에 올라온 것 같아서 신고합니다.", List.of(JIWOO));

        // 도윤이 올린 이차함수 질문 글에 하준이 퉁명스러운 답변을 달고, 그 말투 때문에
        // 나머지 세 명이 댓글을 신고해 자동 블라인드시킨다.
        Long qnaAnswerTargetPostId = findPostIdByTitle(QNA_ANSWER_TARGET_TITLE);
        if (qnaAnswerTargetPostId != null) {
            Long commentId = createCommentIfAbsent(qnaAnswerTargetPostId, HAJUN, REPORTED_ANSWER_CONTENT);
            reportCommentIfNeeded(commentId, HAJUN,
                    "질문한 사람을 무시하는 듯한 말투라 신고합니다.", List.of(MINSEO, JIWOO, SEOYEON));
        }

        // 하준이 남긴 "옆반 애 지각" 한마디 - 뒷담화성 내용이라 나머지 세 명이 신고해 자동 블라인드.
        Long scheduleCommentId = findScheduleCommentId(HAJUN);
        reportScheduleCommentIfNeeded(scheduleCommentId, HAJUN,
                "다른 사람 이야기를 함부로 하는 것 같아서 신고합니다.", List.of(JIWOO, SEOYEON, DOYUN));
    }

    // 게시글/한마디마다 좋아요·북마크를 서로 다르게 심어서 "아무도 반응하지 않은 글"처럼 보이지
    // 않게 한다. PostService.toggleLike()/toggleBookmark()는 토글이라 이미 눌러둔 상태에서 다시
    // 부르면 오히려 취소돼버리므로, 매번 exists 체크로 먼저 확인하고 없을 때만 누른다(멱등 보장).
    private void seedEngagement() {
        for (PostSeed seed : POST_SEEDS) {
            Long postId = findPostIdByTitle(seed.title());
            likePostIfNeeded(postId, seed.authorUsername(), seed.likedBy());
            bookmarkPostIfNeeded(postId, seed.authorUsername(), seed.bookmarkedBy());
        }

        for (ScheduleCommentSeed seed : SCHEDULE_COMMENT_SEEDS) {
            Long commentId = findScheduleCommentId(seed.username());
            likeScheduleCommentIfNeeded(commentId, seed.username(), seed.likedBy());
            bookmarkScheduleCommentIfNeeded(commentId, seed.username(), seed.bookmarkedBy());
        }
    }

    private Long findPostIdByTitle(String title) {
        return postRepository.findAllByDeletedFalseOrderByCreatedAtDesc().stream()
                .filter(p -> p.getTitle().equals(title))
                .map(Post::getId)
                .findFirst()
                .orElse(null);
    }

    private Long findScheduleCommentId(String username) {
        return userRepository.findByUsername(username)
                .map(user -> scheduleCommentRepository
                        .findByUser_IdAndDeletedFalseOrderByCreatedAtDesc(user.getId())
                        .stream()
                        .findFirst()
                        .map(comment -> comment.getId())
                        .orElse(null))
                .orElse(null);
    }

    private Long createCommentIfAbsent(Long postId, String username, String content) {
        Long existing = postCommentRepository.findByPost_IdAndDeletedFalseOrderByCreatedAtAsc(postId).stream()
                .filter(comment -> content.equals(comment.getContent()))
                .map(comment -> comment.getId())
                .findFirst()
                .orElse(null);
        if (existing != null) {
            return existing;
        }

        PostCommentDto dto = postCommentService.createComment(postId, username, content, null);
        return dto.getId();
    }

    // 작성자 본인이 목록에 섞여 들어와도(사람이 목록을 손으로 짤 때 실수하기 쉬운 부분) 자기 글/
    // 댓글/한마디에 대한 신고·좋아요·북마크는 서비스 단에서 막혀있거나 의미가 없으므로, 여기서
    // 미리 걸러내 예외 없이 넘어간다.
    private List<String> excludeAuthor(List<String> usernames, String authorUsername) {
        return usernames.stream().filter(u -> !u.equals(authorUsername)).collect(Collectors.toList());
    }

    private void reportPostIfNeeded(Long postId, String authorUsername, String reason, List<String> reporters) {
        if (postId == null) {
            return;
        }
        for (String reporter : excludeAuthor(reporters, authorUsername)) {
            if (postReportRepository.existsByPost_IdAndReporter_Username(postId, reporter)) {
                continue;
            }
            postService.reportPost(postId, reporter, reason);
        }
    }

    private void reportCommentIfNeeded(Long commentId, String authorUsername, String reason, List<String> reporters) {
        if (commentId == null) {
            return;
        }
        for (String reporter : excludeAuthor(reporters, authorUsername)) {
            if (commentReportRepository.existsByComment_IdAndReporter_Username(commentId, reporter)) {
                continue;
            }
            postCommentService.reportComment(commentId, reporter, reason);
        }
    }

    private void reportScheduleCommentIfNeeded(Long commentId, String authorUsername, String reason,
                                                List<String> reporters) {
        if (commentId == null) {
            return;
        }
        for (String reporter : excludeAuthor(reporters, authorUsername)) {
            if (scheduleCommentReportRepository.existsByComment_IdAndReporter_Username(commentId, reporter)) {
                continue;
            }
            scheduleCommentService.reportComment(commentId, reporter, reason);
        }
    }

    private void likePostIfNeeded(Long postId, String authorUsername, List<String> likers) {
        if (postId == null) {
            return;
        }
        for (String liker : excludeAuthor(likers, authorUsername)) {
            if (postLikeRepository.existsByPost_IdAndUser_Username(postId, liker)) {
                continue;
            }
            postService.toggleLike(postId, liker);
        }
    }

    private void bookmarkPostIfNeeded(Long postId, String authorUsername, List<String> bookmarkers) {
        if (postId == null) {
            return;
        }
        for (String bookmarker : excludeAuthor(bookmarkers, authorUsername)) {
            if (postBookmarkRepository.existsByPost_IdAndUser_Username(postId, bookmarker)) {
                continue;
            }
            postService.toggleBookmark(postId, bookmarker);
        }
    }

    private void likeScheduleCommentIfNeeded(Long commentId, String authorUsername, List<String> likers) {
        if (commentId == null) {
            return;
        }
        for (String liker : excludeAuthor(likers, authorUsername)) {
            if (scheduleCommentLikeRepository.existsByComment_IdAndUser_Username(commentId, liker)) {
                continue;
            }
            scheduleCommentService.toggleLike(commentId, liker);
        }
    }

    private void bookmarkScheduleCommentIfNeeded(Long commentId, String authorUsername, List<String> bookmarkers) {
        if (commentId == null) {
            return;
        }
        for (String bookmarker : excludeAuthor(bookmarkers, authorUsername)) {
            if (scheduleCommentBookmarkRepository.existsByComment_IdAndUser_Username(commentId, bookmarker)) {
                continue;
            }
            scheduleCommentService.toggleBookmark(commentId, bookmarker);
        }
    }
}
