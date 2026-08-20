package com.webschool.webschool.user.service;

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

        return org.springframework.security.core.userdetails.User
                .builder()
                .username(user.getUsername())
                .password(user.getPassword())
                .roles(user.getRole().name().replace("ROLE_", ""))
                // 탈퇴했거나, 관리자가 즉시 비활성화했거나(User.active), 기간제 계정 비활성화조치
                // (UserPenalty, DEACTIVATION)가 지금 시점 기준 유효하면 로그인 차단
                .disabled(user.isDeleted() || !user.isActive() || userPenaltyService.isDeactivated(user.getId()))
                .build();
    }
}