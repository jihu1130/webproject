package com.webschool.webschool.global.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Swagger UI(/swagger-ui.html) 문서 정보. 이 앱은 로그인 시 httpOnly 쿠키(jwt)로 인증하므로
// (JwtAuthenticationFilter 참고) 인증 방식도 Bearer 헤더가 아니라 쿠키로 적어둔다 - 총관리자로
// 로그인한 브라우저에서 Swagger UI를 열면 그 쿠키가 그대로 실려 "Try it out"이 동작한다.
// 문서 자체의 접근 제한은 SecurityConfig(총관리자만)에서 한다.
@Configuration
public class OpenApiConfig {

    private static final String JWT_COOKIE_SCHEME = "jwtCookie";

    @Bean
    public OpenAPI webSchoolOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("WebSchool API")
                        .description("학교 일정 확인 + 같은 학교 학생 커뮤니티 - 화면(Thymeleaf)과 JSON API 엔드포인트 목록")
                        .version("v1"))
                .components(new Components()
                        .addSecuritySchemes(JWT_COOKIE_SCHEME, new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.COOKIE)
                                .name("jwt")
                                .description("로그인 시 발급되는 httpOnly JWT 쿠키")))
                .addSecurityItem(new SecurityRequirement().addList(JWT_COOKIE_SCHEME));
    }
}
