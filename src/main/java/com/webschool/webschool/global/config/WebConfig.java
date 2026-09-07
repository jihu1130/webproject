package com.webschool.webschool.global.config;

import com.webschool.webschool.global.security.AdminAccessInterceptor;
import com.webschool.webschool.global.security.EmailSetupInterceptor;
import com.webschool.webschool.global.security.PasswordSetupInterceptor;
import com.webschool.webschool.global.security.SchoolSetupInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

// 프로젝트 외부(app.upload.dir)에 저장되는 게시글 첨부 이미지를 /uploads/** 로 정적 서빙하고,
// 관리자 하위 메뉴별 권한 체크(AdminAccessInterceptor)를 등록한다.
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    @Value("${app.upload.dir}")
    private String uploadDir;

    private final AdminAccessInterceptor adminAccessInterceptor;
    private final SchoolSetupInterceptor schoolSetupInterceptor;
    private final EmailSetupInterceptor emailSetupInterceptor;
    private final PasswordSetupInterceptor passwordSetupInterceptor;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = Paths.get(uploadDir).toAbsolutePath().normalize().toUri().toString();
        registry.addResourceHandler("/uploads/**").addResourceLocations(location);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(adminAccessInterceptor).addPathPatterns("/admin/**");
        // 학교 설정 화면 자체가 쓰는 정적 리소스/학교 검색·반 목록 API는 막으면 그 화면 자체가
        // 못 뜨게 되므로 반드시 제외해야 한다.
        registry.addInterceptor(schoolSetupInterceptor).addPathPatterns("/**")
                .excludePathPatterns("/css/**", "/js/**", "/images/**", "/uploads/**",
                        "/oauth2/**", "/login/oauth2/**",
                        "/school/api/search", "/school/api/classes");
        // 이메일 입력 강제 게이트 - 학교 설정을 아직 못 마친 사용자는 위 인터셉터가 먼저 /school-setup
        // 으로 돌려보내고, 그걸 마친 다음 요청부터 이 게이트가 이어서 걸린다(순차 진행).
        registry.addInterceptor(emailSetupInterceptor).addPathPatterns("/**")
                .excludePathPatterns("/css/**", "/js/**", "/images/**", "/uploads/**",
                        "/oauth2/**", "/login/oauth2/**",
                        "/school/api/search", "/school/api/classes");
        // 구글 계정 비밀번호 설정 강제 게이트(todo.md #19) - 위 두 게이트를 마친 다음 요청부터
        // 이어서 걸린다. 로컬 계정/이미 설정을 마친 구글 계정은 needsPasswordSetup()이 항상 false라
        // 아무 영향 없음.
        registry.addInterceptor(passwordSetupInterceptor).addPathPatterns("/**")
                .excludePathPatterns("/css/**", "/js/**", "/images/**", "/uploads/**",
                        "/oauth2/**", "/login/oauth2/**",
                        "/school/api/search", "/school/api/classes");
    }
}
