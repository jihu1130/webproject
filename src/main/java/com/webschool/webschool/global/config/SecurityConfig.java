package com.webschool.webschool.global.config;

import com.webschool.webschool.user.service.CustomOAuth2UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final CustomOAuth2UserService customOAuth2UserService;
    // spring.security.oauth2.client.registration.google.* 설정이 없으면 Spring Boot가 이 빈을
    // 아예 만들지 않는다(에러 없이 조용히 생략됨) - 그래서 ObjectProvider로 받아서 있을 때만
    // .oauth2Login()을 붙인다. 실제 client-id/secret 없이 무작정 등록하면 앱 자체가 기동 실패한다
    // (ClientRegistration.Builder가 "clientId cannot be empty"로 즉시 예외를 던짐) - 구글 OAuth
    // 앱을 만들어 자격증명을 받기 전까지는 이 기능이 조용히 비활성 상태로 남아있어야 한다.
    private final ObjectProvider<ClientRegistrationRepository> clientRegistrationRepositoryProvider;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable()) // 개발용
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/", "/register", "/login", "/oauth2/**", "/login/oauth2/**",
                                "/css/**", "/js/**", "/images/**", "/uploads/**",
                                "/api/users/check-username", "/school/api/search", "/school/api/classes").permitAll()
                        // 게시물 작성/수정/삭제/신고는 로그인 필요, 목록/상세/댓글 조회는 누구나 가능
                        .requestMatchers(HttpMethod.GET, "/posts/new", "/posts/*/edit").authenticated()
                        .requestMatchers(HttpMethod.GET, "/posts", "/posts/*", "/posts/*/comments").permitAll()
                        .requestMatchers("/posts/**").authenticated()
                        // 공지사항 조회는 커뮤니티 목록/상세와 동일하게 로그인 없이도 가능(공지는 공개 정보이므로)
                        .requestMatchers(HttpMethod.GET, "/notices", "/notices/*").permitAll()
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

        if (clientRegistrationRepositoryProvider.getIfAvailable() != null) {
            http.oauth2Login(oauth2 -> oauth2
                    .loginPage("/login")
                    .userInfoEndpoint(userInfo -> userInfo.userService(customOAuth2UserService))
                    .defaultSuccessUrl("/", false)
                    .failureUrl("/login?error=true")
            );
        }

        return http.build();
    }
}
