package com.webschool.webschool.global.config;

import com.webschool.webschool.global.security.CookieOAuth2AuthorizationRequestRepository;
import com.webschool.webschool.global.security.JwtAuthenticationFilter;
import com.webschool.webschool.global.security.JwtService;
import com.webschool.webschool.global.security.LoginFailureHandler;
import com.webschool.webschool.global.security.LoginSuccessHandler;
import com.webschool.webschool.global.security.OAuth2LoginSuccessHandler;
import com.webschool.webschool.user.service.CustomOAuth2UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.savedrequest.NullRequestCache;

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
    private final LoginSuccessHandler loginSuccessHandler;
    private final LoginFailureHandler loginFailureHandler;
    private final OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;
    private final CookieOAuth2AuthorizationRequestRepository cookieOAuth2AuthorizationRequestRepository;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JwtService jwtService;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // 배포 전 CSRF 재활성화(todo.md 항목) - JS로 직접 요청을 만드는 곳(admin-bulk.js,
                // rich-editor.js, calendar.js, post-detail.js)만 static/js/csrf.js 헬퍼로
                // 토큰을 같이 보내도록 손봤고, 나머지 th:action 폼은
                // thymeleaf-extras-springsecurity6가 자동으로 hidden 토큰을 넣어준다.
                // 세션 인증 → JWT 전체 교체(2026-09-14)로 CsrfTokenRepository만 세션 기반에서
                // 쿠키 기반(CookieCsrfTokenRepository)으로 바꿨다 - Thymeleaf의 자동 hidden
                // input과 WebSchoolCsrf.headers() 둘 다 요청 속성(메타태그/hidden input)에서
                // 토큰을 읽는 방식이라 이 교체를 몰라도 그대로 동작한다(템플릿/JS 무수정). 이
                // 앱의 JS는 document.cookie로 CSRF 쿠키를 직접 읽는 코드가 전혀 없으므로(항상
                // 서버가 렌더링한 meta 태그에서만 읽음, csrf.js 참고) withHttpOnlyFalse()가
                // 필요한 "JS가 쿠키를 읽어 헤더에 넣는" 고전적 더블서밋 패턴이 아니다 - 기본
                // 생성자(httpOnly=true)로 XSS 표면을 한 겹 더 줄인다. SameSite를 jwt 쿠키와
                // 동일하게 명시적으로 Lax로 맞춘다(기본값은 브라우저마다 미표기 쿠키를 다르게
                // 취급할 수 있어 명시하는 편이 안전).
                .csrf(csrf -> {
                    CookieCsrfTokenRepository repository = new CookieCsrfTokenRepository();
                    repository.setCookieCustomizer(cookie -> cookie.sameSite("Lax"));
                    csrf.csrfTokenRepository(repository);
                })
                .authorizeHttpRequests(auth -> auth
                        // "/error"가 permitAll이 아니면, 핸들러가 없는 요청(존재하지 않는 정적 파일 등 -
                        // 예: /favicon.ico)이 Spring Boot 기본 에러 처리로 "/error"에 내부 포워드될 때
                        // 그 포워드 자체가 다시 보안 필터를 타면서 anyRequest().authenticated()에 걸려
                        // 비로그인 사용자를 /login으로 튕겨버린다(실제 에러 페이지 대신). 직접 재현해서
                        // 확인함(2026-09-07) - /favicon.ico permitAll만 추가해선 안 고쳐지고 "/error"도
                        // 같이 permitAll이어야 함(오류 페이지 자체는 누구나 볼 수 있어야 하는 게 맞기도
                        // 하다 - BasicErrorController가 렌더링하는 templates/error.html 참고).
                        .requestMatchers("/", "/register", "/login", "/oauth2/**", "/login/oauth2/**",
                                "/css/**", "/js/**", "/images/**", "/uploads/**", "/favicon.ico", "/error",
                                "/api/users/check-username", "/school/api/search", "/school/api/classes",
                                // /actuator/prometheus - 로컬 Prometheus 컨테이너가 인증 없이 스크레이프하므로
                                // health와 동일하게 permitAll(todo.md #24, 로컬 모니터링 범위). 나중에 이
                                // 스택을 운영 서버에 배포하게 되면 JVM/요청량 같은 내부 지표가 공개
                                // 인터넷에 그대로 노출되니, 그 시점엔 nginx IP 화이트리스트 등으로 별도
                                // 제한을 다시 고려할 것.
                                "/actuator/health", "/actuator/prometheus",
                                "/find-username", "/forgot-password", "/reset-password", "/verify-email")
                        .permitAll()
                        // 버그 리포트는 비로그인 사용자도 제출 가능(사용자 확정 정책)
                        .requestMatchers("/bug-reports/new").permitAll()
                        // 게시물 작성/수정/삭제/신고는 로그인 필요, 목록/상세/댓글 조회는 누구나 가능
                        .requestMatchers(HttpMethod.GET, "/posts/new", "/posts/*/edit").authenticated()
                        .requestMatchers(HttpMethod.GET, "/posts", "/posts/*", "/posts/*/comments").permitAll()
                        .requestMatchers("/posts/**").authenticated()
                        // 공지사항 조회는 커뮤니티 목록/상세와 동일하게 로그인 없이도 가능(공지는 공개 정보이므로)
                        .requestMatchers(HttpMethod.GET, "/notices", "/notices/*").permitAll()
                        // 통합검색(커뮤니티+공지)도 비로그인 조회 가능 - 캘린더 일정 부분만 로그인 사용자에
                        // 한해 조용히 채워진다(SearchController.findScheduleEvent 참고)
                        .requestMatchers(HttpMethod.GET, "/search").permitAll()
                        // 커뮤니티 공개 프로필(작성자 이름 클릭) - 게시글 조회와 동일하게 로그인 없이도 열람 가능
                        .requestMatchers(HttpMethod.GET, "/users/*").permitAll()
                        // 포인트/티어 랭킹 - 공개 프로필이 이미 포인트·티어를 permitAll로 보여주고
                        // 있어 같은 공개 수준으로 로그인 없이도 열람 가능
                        .requestMatchers(HttpMethod.GET, "/ranking", "/ranking/tiers").permitAll()
                        // 캘린더 비로그인 열람 허용(Feature 3, 사용자 확정) - 아래 4개 규칙은 선언
                        // 순서가 그대로 우선순위(먼저 매칭되는 규칙이 적용)라 반드시 이 순서를 유지할 것.
                        //
                        // 1) 개인 일정은 원천적으로 비공개 데이터라 항상 인증 필요 - 나중에 실수로
                        //    "/school/api/**" 같은 넓은 permitAll로 바뀌는 걸 막기 위해 이 규칙을
                        //    이 블록 맨 앞에 명시적으로 둔다.
                        .requestMatchers("/school/api/personal-events/**").authenticated()
                        // 2) 한마디 작성/수정 "페이지 진입"(GET)은 로그인 필요 - /posts/new, /posts/*/edit과 동일한 이유
                        .requestMatchers(HttpMethod.GET, "/school/comments/new", "/school/comments/*/edit").authenticated()
                        // 3) 캘린더 페이지·조회성 API·한마디 목록/퍼머링크·날씨 위젯은 /posts처럼 비로그인도 열람 가능
                        .requestMatchers(HttpMethod.GET,
                                "/school/calendar",
                                "/school/api/timetable",
                                "/school/api/calendar-details",
                                "/school/api/calendar-events",
                                "/school/api/calendar-events/search",
                                "/school/api/vacation-dday",
                                "/school/api/comments",
                                "/school/comments/*"
                        ).permitAll()
                        // 4) 나머지 /school/** 전부(한마디 작성/수정/삭제/신고/좋아요/북마크 등 쓰기 동작)는 로그인 필요
                        .requestMatchers("/school/**").authenticated()
                        // 관리자 전용 화면은 ROLE_ADMIN(부관리자)/ROLE_SUPER_ADMIN(총관리자) 둘 다 접근 가능.
                        // 그 안에서 구체적으로 어떤 메뉴(신고/게시글/한마디/계정 관리)까지 볼 수 있는지는
                        // AdminAccessInterceptor가 계정별 권한 플래그로 한 번 더 세밀하게 가른다.
                        .requestMatchers("/admin/**").hasAnyRole("ADMIN", "SUPER_ADMIN")
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .formLogin(login -> login
                        .loginPage("/login")
                        // 로그인 시도 횟수 제한(todo.md "고도화 후보") - 성공 시 실패 카운터 리셋,
                        // 실패 시 카운터 증가/잠금 판단은 각각 LoginSuccessHandler/LoginFailureHandler로 위임.
                        // successHandler가 이제 JWT 쿠키를 발급하고 항상 홈으로 보낸다(사용자 확정,
                        // "원래 요청했던 페이지로 복귀" 기능은 OAuth2 로그인과 동일하게 단순화됨).
                        .successHandler(loginSuccessHandler)
                        .failureHandler(loginFailureHandler)
                        .permitAll()
                )
                // 로그인/인가 실패 시 요청을 저장했다가 로그인 후 되돌아가는 기능은 이제 안 쓴다
                // (사용자 확정 - 항상 홈으로 단순화) - 저장 자체를 하지 않아 불필요한 세션 생성도 막는다.
                .requestCache(cache -> cache.requestCache(new NullRequestCache()))
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessHandler((request, response, authentication) -> {
                            response.addHeader(HttpHeaders.SET_COOKIE,
                                    jwtService.buildExpiredCookie(request.isSecure()).toString());
                            response.sendRedirect("/");
                        })
                )
                .exceptionHandling(exceptions -> exceptions
                        // 부관리자가 권한 없는 관리자 메뉴에 접근하면 whitelabel 403 대신 안내 화면으로 보낸다.
                        // 버그 수정: AdminAccessInterceptor가 던지는 예외에는 "신고 관리 권한이
                        // 없습니다." 처럼 구체적인 이유가 담겨 있는데, 예전엔 여기서 버려지고 항상
                        // 똑같은 뭉뚱그린 안내문만 보였다 - 세션에 한 번만 담아 access-denied 화면에서
                        // 보여준다(RedirectAttributes.addFlashAttribute와 같은 원리를 여기서는 이
                        // 핸들러가 Spring MVC 컨트롤러가 아니라서 직접 세션에 심어 흉내낸다). 참고:
                        // sessionCreationPolicy(STATELESS)는 Spring Security 자신이 SecurityContext를
                        // 세션에 두지 않겠다는 설정일 뿐, 앱 코드가 request.getSession()으로 평범한
                        // HttpSession을 쓰는 것 자체는 막지 않는다(RedirectAttributes.addFlashAttribute
                        // 등 이 앱 곳곳의 flash 메시지 패턴도 전부 동일하게 계속 동작함).
                        .accessDeniedHandler((request, response, ex) -> {
                            String target = request.getRequestURI().startsWith("/admin/") ? "/admin/access-denied" : "/";
                            if (target.equals("/admin/access-denied") && ex.getMessage() != null) {
                                request.getSession().setAttribute("flashError", ex.getMessage());
                            }
                            response.sendRedirect(target);
                        })
                );

        if (clientRegistrationRepositoryProvider.getIfAvailable() != null) {
            http.oauth2Login(oauth2 -> oauth2
                    .loginPage("/login")
                    .authorizationEndpoint(authorization -> authorization
                            .authorizationRequestRepository(cookieOAuth2AuthorizationRequestRepository))
                    .userInfoEndpoint(userInfo -> userInfo.userService(customOAuth2UserService))
                    .successHandler(oAuth2LoginSuccessHandler)
                    .failureUrl("/login?error=true")
            );
        }

        return http.build();
    }
}
