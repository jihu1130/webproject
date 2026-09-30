package com.webschool.webschool;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.Map;

// @EnableScheduling - 수정사항.md 지적(#5, 임시 업로드 파일 정리) 대응으로 처음 도입한 스케줄 작업
// (EditorUploadCleanupService)을 위해 추가. 이 프로젝트의 첫 @Scheduled 사용처.
@EnableScheduling
@SpringBootApplication
public class WebschoolApplication {

	public static void main(String[] args) {
		SpringApplication app = new SpringApplication(WebschoolApplication.class);
		// 모든 로그 줄의 레벨 뒤에 [요청ID 사용자]를 찍는다(RequestLoggingFilter/JwtAuthenticationFilter가
		// MDC에 채움, 요청 밖의 배치 로그는 [- -]). application.yml은 git 비추적이라 로컬/운영에 각각
		// 따로 있어서 거기 넣으면 한쪽만 바뀌기 쉽다 - 코드 기본값으로 두고, 필요하면 yml의
		// logging.pattern.level로 덮어쓸 수 있다(기본값은 우선순위가 가장 낮음).
		app.setDefaultProperties(Map.of(
				"logging.pattern.level", "%5p [%X{requestId:--} %X{user:--}]"));
		app.run(args);
	}

}
