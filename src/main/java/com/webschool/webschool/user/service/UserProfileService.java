package com.webschool.webschool.user.service;

import com.webschool.webschool.post.domain.Post;
import com.webschool.webschool.post.repository.PostRepository;
import com.webschool.webschool.user.dto.PublicUserProfileDto;
import com.webschool.webschool.user.dto.PublicUserProfilePostDto;
import com.webschool.webschool.user.domain.User;
import com.webschool.webschool.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;

// 커뮤니티(일반 사용자)에서 작성자 이름을 눌렀을 때 보여주는 공개 프로필 조회 - 회원가입/마이페이지를
// 다루는 UserService(자기 자신 관리)나 총관리자 전용 AdminUserService(계정 관리)와는 책임이 달라서
// (다른 사용자를 "조회"만 하는 새 책임) 별도 서비스로 분리했다.
@Service
@RequiredArgsConstructor
public class UserProfileService {

    private static final DateTimeFormatter DISPLAY_FORMAT = DateTimeFormatter.ofPattern("MM.dd HH:mm");
    private static final int PAGE_SIZE = 10;

    private final UserRepository userRepository;
    private final PostRepository postRepository;

    public PublicUserProfileDto getProfile(Long userId, int page) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        // 탈퇴한 계정은 프로필 자체를 보여주지 않는다 - 콘텐츠 화면에서도 "탈퇴한 사용자"로만
        // 표시되고 링크 자체가 안 걸리지만(authorLinkable), URL을 직접 알고 접근하는 경우까지 방어.
        if (user.isDeleted()) {
            throw new IllegalArgumentException("사용자를 찾을 수 없습니다.");
        }

        Pageable pageable = PageRequest.of(Math.max(page, 0), PAGE_SIZE);
        Page<PublicUserProfilePostDto> posts = postRepository
                .findByAuthor_IdAndCategoryNotAndDeletedFalseAndBlindFalseAndVisibilityOrderByCreatedAtDesc(
                        userId, Post.Category.ANONYMOUS, Post.Visibility.PUBLIC, pageable)
                .map(this::toPostDto);

        return PublicUserProfileDto.builder()
                .id(user.getId())
                .nickname(user.getNickname())
                .bio(user.getBio())
                .postCount(posts.getTotalElements())
                .posts(posts)
                .build();
    }

    private PublicUserProfilePostDto toPostDto(Post p) {
        return PublicUserProfilePostDto.builder()
                .uuid(p.getUuid())
                .title(p.getTitle())
                .categoryLabel(p.getCategory().getLabel())
                .createdAt(p.getCreatedAt().format(DISPLAY_FORMAT))
                .viewCount(p.getViewCount())
                .build();
    }
}
