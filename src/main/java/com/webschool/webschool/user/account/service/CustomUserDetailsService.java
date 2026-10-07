package com.webschool.webschool.user.account.service;

import com.webschool.webschool.user.service.UserPenaltyService;
import com.webschool.webschool.user.domain.User;
import com.webschool.webschool.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;
    private final UserPenaltyService userPenaltyService;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("사용자를 찾을 수 없습니다: " + username));

        UserDetails details = org.springframework.security.core.userdetails.User
                .builder()
                .username(user.getUsername())
                .password(user.getPassword())
                .roles(user.getRole().name().replace("ROLE_", ""))
                // 탈퇴했거나, 관리자가 즉시 비활성화했거나(User.active), 기간제 계정 비활성화조치
                // (UserPenalty, DEACTIVATION)가 지금 시점 기준 유효하면 로그인 차단
                .disabled(user.isDeleted() || !user.isActive() || userPenaltyService.isDeactivated(user.getId()))
                // 로그인 연속 실패는 더 이상 계정을 잠그지 않는다(2026-10-07) - 여기서 accountLocked를 켜면
                // 남이 비밀번호를 5번 틀린 것만으로 이미 로그인해 있던 본인까지 로그아웃됐다(이 UserDetails를
                // JwtAuthenticationFilter가 매 요청 다시 읽으므로). 대입 방지는 LoginAttemptService가
                // "시도한 쪽만 기다리게" 하는 방식으로 한다.
                .build();
        // 로그아웃/비밀번호 변경 이전에 발급된 JWT를 JwtAuthenticationFilter가 걸러낼 수 있게 기준 시각을 같이 넘긴다.
        return new AccountUserDetails(details, user.getTokensInvalidBefore());
    }
}