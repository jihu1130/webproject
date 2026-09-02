package com.webschool.webschool.user.service;

import com.webschool.webschool.admin.service.AdminActionLogService;
import com.webschool.webschool.global.mail.MailService;
import com.webschool.webschool.user.domain.User;
import com.webschool.webschool.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

// "탈퇴한 구글 계정 재로그인 시 자동 복구"(CustomOAuth2UserService.loadUser())의 판단 로직
// 회귀 테스트. loadUser() 전체는 DefaultOAuth2UserService.super.loadUser()가 실제 구글
// 서버로 HTTP 요청을 보내서 그대로 단위 테스트하기 어려우므로, 그 판단만 뽑아둔
// shouldAutoReactivate()를 직접 검증한다. 실사용자 지적으로 발견한 버그(관리자 강제 탈퇴
// (deletedByAdmin) 계정까지 재로그인 한 번으로 되살아나던 문제)의 회귀 방지용.
@ExtendWith(MockitoExtension.class)
class CustomOAuth2UserServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private UserPenaltyService userPenaltyService;
    @Mock private AdminActionLogService adminActionLogService;
    @Mock private MailService mailService;

    @InjectMocks
    private CustomOAuth2UserService customOAuth2UserService;

    @Test
    void selfDeletedAccount_isReactivated() {
        User user = new User();
        user.setDeleted(true);
        user.setDeletedByAdmin(false);

        assertTrue(customOAuth2UserService.shouldAutoReactivate(user));
    }

    @Test
    void adminDeletedAccount_isNotReactivated() {
        User user = new User();
        user.setDeleted(true);
        user.setDeletedByAdmin(true);

        assertFalse(customOAuth2UserService.shouldAutoReactivate(user));
    }

    @Test
    void nonDeletedAccount_hasNothingToReactivate() {
        User user = new User();
        user.setDeleted(false);
        user.setDeletedByAdmin(false);

        assertFalse(customOAuth2UserService.shouldAutoReactivate(user));
    }
}
