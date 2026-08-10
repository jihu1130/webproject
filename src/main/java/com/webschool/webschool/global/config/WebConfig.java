package com.webschool.webschool.global.config;

import com.webschool.webschool.global.security.AdminAccessInterceptor;
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

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = Paths.get(uploadDir).toAbsolutePath().normalize().toUri().toString();
        registry.addResourceHandler("/uploads/**").addResourceLocations(location);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(adminAccessInterceptor).addPathPatterns("/admin/**");
    }
}
