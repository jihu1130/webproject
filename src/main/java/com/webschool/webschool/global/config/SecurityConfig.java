package com.webschool.webschool.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable()) // 개발용
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/", "/register", "/login", "/css/**", "/js/**", "/images/**", "/uploads/**", "/api/users/check-username", "/school/api/search", "/school/api/classes").permitAll()
                        // 게시물 작성/수정/삭제/신고는 로그인 필요, 목록/상세/댓글 조회는 누구나 가능
                        .requestMatchers(HttpMethod.GET, "/posts/new", "/posts/*/edit").authenticated()
                        .requestMatchers(HttpMethod.GET, "/posts", "/posts/*", "/posts/*/comments").permitAll()
                        .requestMatchers("/posts/**").authenticated()
                        // 커뮤니티 공개 프로필(작성자 이름 클릭) - 게시글 조회와 동일하게 로그인 없이도 열람 가능
                        .requestMatchers(HttpMethod.GET, "/users/*").permitAll()
                        // 캘린더(학사/급식 조회)는 로그인한 사용자만 이용 가능
                        .requestMatchers("/school/**").authenticated()
                        // 관리자 전용 화면은 ROLE_ADMIN(부관리자)/ROLE_SUPER_ADMIN(총관리자) 둘 다 접근 가능.
                        // 그 안에서 구체적으로 어떤 메뉴(신고/게시글/한마디/계정 관리)까지 볼 수 있는지는
                        // AdminAccessInterceptor가 계정별 권한 플래그로 한 번 더 세밀하게 가른다.
                        .requestMatchers("/admin/**").hasAnyRole("ADMIN", "SUPER_ADMIN")
                        .anyRequest().authenticated()
                )
                .formLogin(login -> login
                        .loginPage("/login")
                        // alwaysUse=false: 로그인 페이지로 리다이렉트되기 전 원래 요청했던 URL(예: /school/calendar)이 있으면 그곳으로 되돌아간다
                        .defaultSuccessUrl("/", false)
                        .failureUrl("/login?error=true")
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/")
                        .invalidateHttpSession(true)
                )
                .exceptionHandling(exceptions -> exceptions
                        // 부관리자가 권한 없는 관리자 메뉴에 접근하면 whitelabel 403 대신 안내 화면으로 보낸다
                        .accessDeniedHandler((request, response, ex) -> {
                            String target = request.getRequestURI().startsWith("/admin/") ? "/admin/access-denied" : "/";
                            response.sendRedirect(target);
                        })
                );

        return http.build();
    }
}