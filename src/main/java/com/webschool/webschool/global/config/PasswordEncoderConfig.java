package com.webschool.webschool.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

// PasswordEncoder를 SecurityConfig에서 분리한 별도 설정 클래스 - SecurityConfig가
// CustomOAuth2UserService(구글 로그인 계정에 임의 비밀번호를 encode하는 용도로 PasswordEncoder 필요)에
// 의존하게 되면서, PasswordEncoder를 SecurityConfig 안에 그대로 두면 "SecurityConfig 생성 →
// CustomOAuth2UserService 생성 → PasswordEncoder 필요 → SecurityConfig의 @Bean 메서드 호출 →
// SecurityConfig 필요"로 순환 참조가 생겼다(부팅 실패). 이 빈을 완전히 별도 클래스로 빼서 순환을 끊는다.
@Configuration
public class PasswordEncoderConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
