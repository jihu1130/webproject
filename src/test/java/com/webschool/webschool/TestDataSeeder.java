package com.webschool.webschool;

import com.webschool.webschool.post.domain.Post;
import com.webschool.webschool.post.dto.PostFormDto;
import com.webschool.webschool.post.repository.PostRepository;
import com.webschool.webschool.post.service.PostService;
import com.webschool.webschool.school.repository.ScheduleCommentRepository;
import com.webschool.webschool.school.service.ScheduleCommentService;
import com.webschool.webschool.user.dto.RegisterDto;
import com.webschool.webschool.user.entity.User;
import com.webschool.webschool.user.repository.UserRepository;
import com.webschool.webschool.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;

// 개발용 테스트 데이터 생성기. 실행하면 user1~user5(아이디=비밀번호) 계정 + 계정별 한마디 1개,
// admin/admin(ROLE_ADMIN) 계정, 카테고리별 게시글 3개씩(공지 제외) + 공지(NOTICE) 게시글 1개를
// 만든다. 이미 있는 데이터는 건너뛰므로 여러 번 실행해도 안전하다.
@SpringBootTest
class TestDataSeeder {

    private static final int USER_COUNT = 5;
    private static final int POSTS_PER_CATEGORY = 3;
    private static final String ADMIN_USERNAME = "admin";
    private static final String ADMIN_PASSWORD = "admin";
    private static final String SCHOOL_NAME = "테스트중학교";
    private static final String SCHOOL_CODE = "T000000001";
    private static final String ATPT_CODE = "T10";
    private static final String SCHOOL_KIND = "중학교";
    private static final String GRADE = "1";
    private static final String CLASS_NUM = "1";

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PostService postService;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private ScheduleCommentService scheduleCommentService;

    @Autowired
    private ScheduleCommentRepository scheduleCommentRepository;

    @Test
    void seedTestData() {
        for (int i = 1; i <= USER_COUNT; i++) {
            String username = "user" + i;
            createUserIfAbsent(username);
            createScheduleCommentIfAbsent(username, username + "의 테스트 한마디입니다.");
        }

        createAdminIfAbsent();

        String authorUsername = "user1";
        for (Post.Category category : Post.Category.values()) {
            if (category == Post.Category.NOTICE) {
                continue; // 공지는 관리자만 작성 가능 - createNoticeIfAbsent()에서 admin 명의로 별도 생성
            }
            for (int i = 1; i <= POSTS_PER_CATEGORY; i++) {
                createPostIfAbsent(authorUsername, category, i);
            }
        }

        createNoticeIfAbsent();
    }

    private void createUserIfAbsent(String username) {
        if (userRepository.existsByUsername(username)) {
            return;
        }

        RegisterDto dto = new RegisterDto();
        dto.setUsername(username);
        dto.setPassword(username);
        dto.setConfirmPassword(username);
        dto.setNickname(username);
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
                username, content);
    }

    // admin 계정이 없으면 회원가입시키고 ROLE_ADMIN으로 직접 승격한다. UserService.register()는
    // 항상 ROLE_USER로 고정하므로(관리자 승격 UI 없음), SuperAdminSeeder와 동일하게 리포지토리를
    // 직접 써서 우회한다 - 공지(NOTICE) 게시글은 관리자만 작성할 수 있어 필요. 총관리자(ROLE_SUPER_ADMIN)
    // 승격은 기존 설계대로 SuperAdminSeeder의 책임으로 남겨둔다.
    private void createAdminIfAbsent() {
        if (userRepository.existsByUsername(ADMIN_USERNAME)) {
            return;
        }

        RegisterDto dto = new RegisterDto();
        dto.setUsername(ADMIN_USERNAME);
        dto.setPassword(ADMIN_PASSWORD);
        dto.setConfirmPassword(ADMIN_PASSWORD);
        dto.setNickname(ADMIN_USERNAME);
        dto.setSchoolName(SCHOOL_NAME);
        dto.setSchoolCode(SCHOOL_CODE);
        dto.setAtptCode(ATPT_CODE);
        dto.setSchoolKind(SCHOOL_KIND);
        dto.setGrade(GRADE);
        dto.setClassNum(CLASS_NUM);

        userService.register(dto);

        User admin = userRepository.findByUsername(ADMIN_USERNAME)
                .orElseThrow(() -> new IllegalStateException("admin 계정 생성에 실패했습니다."));
        admin.setRole(User.Role.ROLE_ADMIN);
        userRepository.save(admin);
    }

    private void createNoticeIfAbsent() {
        String title = "공지 테스트 게시글 1";

        boolean exists = postRepository.findAllByDeletedFalseOrderByCreatedAtDesc().stream()
                .anyMatch(p -> p.getTitle().equals(title));
        if (exists) {
            return;
        }

        PostFormDto form = new PostFormDto();
        form.setTitle(title);
        form.setContent("공지 카테고리 테스트 게시글입니다.");
        form.setCategory(Post.Category.NOTICE.name());

        postService.createPost(ADMIN_USERNAME, form);
    }

    private void createPostIfAbsent(String authorUsername, Post.Category category, int index) {
        String title = category.getLabel() + " 테스트 게시글 " + index;

        boolean exists = postRepository.findAllByDeletedFalseOrderByCreatedAtDesc().stream()
                .anyMatch(p -> p.getTitle().equals(title));
        if (exists) {
            return;
        }

        PostFormDto form = new PostFormDto();
        form.setTitle(title);
        form.setContent(category.getLabel() + " 카테고리 테스트 게시글 " + index + "번 내용입니다.");
        form.setCategory(category.name());

        postService.createPost(authorUsername, form);
    }
}
