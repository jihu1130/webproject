package com.webschool.webschool.global.config;

import com.webschool.webschool.global.security.LoginFailureHandler;
import com.webschool.webschool.global.security.LoginSuccessHandler;
import com.webschool.webschool.user.service.CustomOAuth2UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.session.HttpSessionEventPublisher;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.NegatedRequestMatcher;

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

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // 배포 전 CSRF 재활성화(todo.md 항목) - JS로 직접 요청을 만드는 곳(admin-bulk.js,
                // rich-editor.js, calendar.js, post-detail.js)만 static/js/csrf.js 헬퍼로
                // 토큰을 같이 보내도록 손봤고, 나머지 th:action 폼은
                // thymeleaf-extras-springsecurity6가 자동으로 hidden 토큰을 넣어준다.
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
                .formLogin(login -> login
                        .loginPage("/login")
                        // alwaysUse=false: 로그인 페이지로 리다이렉트되기 전 원래 요청했던 URL(예: /school/calendar)이 있으면 그곳으로 되돌아간다.
                        // 로그인 시도 횟수 제한(todo.md "고도화 후보") - 성공 시 실패 카운터 리셋,
                        // 실패 시 카운터 증가/잠금 판단은 각각 LoginSuccessHandler/LoginFailureHandler로 위임.
                        .successHandler(loginSuccessHandler)
                        .failureHandler(loginFailureHandler)
                        .permitAll()
                )
                // 아래 requestCache() 참고 - 네비바 알림 배지 폴링 요청이 로그인 후 리다이렉트
                // 대상으로 잘못 저장되는 버그(todo.md #15) 수정.
                .requestCache(cache -> cache.requestCache(requestCache()))
                // 관리자 대시보드의 "현재 로그인 세션 수"(AdminDashboardController)가 읽는
                // 레지스트리 - 세션 최대 개수 제한 등은 안 걸고 등록/조회 용도로만 쓴다.
                // sessionRegistry()만 등록해선 세션 만료(로그아웃/타임아웃) 이벤트를 못 받아
                // getAllSessions(expiredOnly=false)가 죽은 세션을 계속 살아있는 것처럼
                // 세도록 아래 httpSessionEventPublisher() 빈이 반드시 같이 있어야 한다.
                .sessionManagement(session -> session
                        .sessionConcurrency(concurrency -> concurrency
                                .sessionRegistry(sessionRegistry())
                                // maximumSessions를 실제로 호출해야 RegisterSessionAuthenticationStrategy가
                                // 인증 흐름에 실제로 붙는다(sessionRegistry()만 지정하면 등록 자체가 안
                                // 일어나서 대시보드의 세션 수가 항상 0으로 나오는 걸 직접 겪고 알게 됨) -
                                // 동시 세션 개수를 막을 생각은 없어서 -1(무제한)로 둔다.
                                .maximumSessions(-1)))
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/")
                        .invalidateHttpSession(true)
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
                    .userInfoEndpoint(userInfo -> userInfo.userService(customOAuth2UserService))
                    .defaultSuccessUrl("/", false)
                    .failureUrl("/login?error=true")
            );
        }

        return http.build();
    }

    // 네비바 종 배지가 20초마다 폴링하는 /notifications/unread-count(notification.js)는
    // fetch()로 호출되는데 X-Requested-With 헤더를 안 붙인다 - Spring Security 기본
    // RequestCache(HttpSessionRequestCache)는 이런 요청을 일반 페이지 이동과 구분하지 못하고,
    // 세션이 만료된 시점에 마침 이 폴링이 나가면 "로그인 성공 후 되돌아갈 곳"으로 그 요청을
    // 저장해버린다. 그 결과 로그인에 성공해도 홈이 아니라 이 API의 JSON 응답
    // ({"count":0})만 그대로 보이는 버그가 있었다(실사용자 신고, 2026-09-02, todo.md #15).
    // 이 경로만 저장 대상에서 제외해서 항상 원래 의도대로(홈 또는 실제로 요청했던 페이지로)
    // 리다이렉트되게 한다.
    @Bean
    public RequestCache requestCache() {
        HttpSessionRequestCache requestCache = new HttpSessionRequestCache();
        requestCache.setRequestMatcher(new NegatedRequestMatcher(
                PathPatternRequestMatcher.pathPattern("/notifications/unread-count")));
        return requestCache;
    }

    @Bean
    public SessionRegistry sessionRegistry() {
        return new SessionRegistryImpl();
    }

    // ServletContext에 HttpSessionEventPublisher 리스너를 등록해서 세션 생성/소멸 이벤트를
    // SessionRegistry에 전달한다(WebConfig에 별도로 등록하지 않고 여기 빈으로만 선언해도
    // Spring Boot가 ServletListenerRegistrationBean 없이 자동으로 리스너로 등록해준다).
    @Bean
    public HttpSessionEventPublisher httpSessionEventPublisher() {
        return new HttpSessionEventPublisher();
    }
}
