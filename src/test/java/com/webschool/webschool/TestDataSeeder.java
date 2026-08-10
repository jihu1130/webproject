package com.webschool.webschool;

import com.webschool.webschool.post.domain.Post;
import com.webschool.webschool.post.dto.PostFormDto;
import com.webschool.webschool.post.repository.PostRepository;
import com.webschool.webschool.post.service.PostService;
import com.webschool.webschool.user.dto.RegisterDto;
import com.webschool.webschool.user.repository.UserRepository;
import com.webschool.webschool.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

// 개발용 테스트 데이터 생성기. 실행하면 user1~user5(아이디=비밀번호) 계정과
// 카테고리별 게시글 3개씩을 만든다. 이미 있는 계정/게시글은 건너뛰므로 여러 번 실행해도 안전하다.
@SpringBootTest
class TestDataSeeder {

    private static final int USER_COUNT = 5;
    private static final int POSTS_PER_CATEGORY = 3;

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PostService postService;

    @Autowired
    private PostRepository postRepository;

    @Test
    void seedTestData() {
        for (int i = 1; i <= USER_COUNT; i++) {
            createUserIfAbsent("user" + i);
        }

        String authorUsername = "user1";
        for (Post.Category category : Post.Category.values()) {
            for (int i = 1; i <= POSTS_PER_CATEGORY; i++) {
                createPostIfAbsent(authorUsername, category, i);
            }
        }
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
        dto.setSchoolName("테스트중학교");
        dto.setSchoolCode("T000000001");
        dto.setAtptCode("T10");
        dto.setSchoolKind("중학교");
        dto.setGrade("1");
        dto.setClassNum("1");

        userService.register(dto);
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
