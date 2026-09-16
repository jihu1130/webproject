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
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
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
                //
                // 세션 인증 → JWT 전체 교체(2026-09-14) 때 CsrfTokenRepository를 세션 기반에서
                // 쿠키 기반(CookieCsrfTokenRepository)으로 바꿨었는데, 2026-09-16 실사용 중
                // "관리자 액션마다 CSRF 토큰 검증 실패"가 재현됨(총관리자 포함 매번, 동시 요청
                // 여부와 무관) - 처음엔 "동시 요청 2개가 쿠키 발급을 경합한다"는 가설로
                // HttpSessionCsrfTokenRepository(세션 저장)로만 되돌려봤는데도 재현이 그대로
                // 남아있어서(todo.md 기록) 원인 재조사 결과, **진짜 원인은
                // sessionCreationPolicy(STATELESS)와 세션 기반 CSRF 저장소의 근본적인 충돌**이었다:
                // curl로 실제 쿠키를 주고받으며 확인해보니 로그인한 상태로 아무 페이지나 열 때마다
                // 매번 완전히 새로운 세션(JSESSIONID)+CSRF 토큰이 발급되고 있었다(연속 요청 3개가
                // 전부 서로 다른 토큰 - 세션이 전혀 재사용되지 않음). 이유: Spring Security는
                // STATELESS에서도 SessionManagementFilter 자체는 그대로 필터체인에 넣고, 이
                // 필터는 "이 인증이 이 세션에서 처음 나타난 것인지"를
                // SecurityContextRepository.containsContext(request)로 판단하는데, STATELESS일 땐
                // 이 repository가 RequestAttributeSecurityContextRepository(요청 하나 안에서만
                // 유효)라 다음 요청엔 항상 비어있다. 그런데 이 앱의 JwtAuthenticationFilter는 매
                // 요청마다 SecurityContextHolder.setAuthentication()을 직접 호출해서 이
                // repository API 자체를 거치지 않으므로 containsContext()가 매 요청 항상 false가
                // 되고, SessionManagementFilter는 모든 요청을 "로그인 직후 첫 요청"으로 오인해
                // 세션 고정 보호(session fixation protection)를 매번 재실행 - 세션을 매번 새로
                // 만들어버린다. 화면에 렌더링된 CSRF 토큰은 그 순간 이미 서버가 버린 세션 소속이라
                // 제출 시점엔 무조건 불일치가 난다(동시성과는 무관하게 100% 재현되는 게 당연했던
                // 구조). sessionCreationPolicy를 IF_REQUIRED로 바꾸면(아래 sessionManagement 참고)
                // 세션이 요청 간에 정상적으로 재사용됨을 확인했고, 실제 브라우저로 관리자 액션
                // (계정 비활성화)까지 성공하는 것으로 최종 검증함(2026-09-16). JWT 인증 자체는
                // 여전히 쿠키만으로 이뤄지고(로그인 상태 판별에 세션을 안 씀), 세션은 CSRF 토큰
                // 저장 용도로만 쓰인다.
                .csrf(csrf -> csrf.csrfTokenRepository(new HttpSessionCsrfTokenRepository()))
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
                // STATELESS였다가 2026-09-16 CSRF 버그 조사로 IF_REQUIRED로 변경(위 csrf() 블록의
                // 상세 원인 참고) - JwtAuthenticationFilter가 SecurityContextRepository API를
                // 거치지 않고 SecurityContextHolder를 직접 채우기 때문에 STATELESS에서는
                // SessionManagementFilter가 매 요청을 "로그인 직후"로 오인해 세션을 매번
                // 새로 만들어버려(세션 기반 CSRF 토큰이 절대 재사용될 수 없는 구조) 세션 고정
                // 보호 자체가 제대로 동작하지 않았다. IF_REQUIRED로 바꿔도 로그인 상태 판별은
                // 여전히 JWT 쿠키만으로 이뤄진다(CustomUserDetailsService를 매 요청 재조회하는
                // JwtAuthenticationFilter 로직 자체는 무수정) - 세션은 CSRF 토큰을 필요한
                // 시점에만 생성해서 재사용하는 용도로만 쓰인다.
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
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
                        // 핸들러가 Spring MVC 컨트롤러가 아니라서 직접 세션에 심어 흉내낸다).
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
