package com.webschool.webschool;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

// @EnableScheduling - 수정사항.md 지적(#5, 임시 업로드 파일 정리) 대응으로 처음 도입한 스케줄 작업
// (EditorUploadCleanupService)을 위해 추가. 이 프로젝트의 첫 @Scheduled 사용처.
@EnableScheduling
@SpringBootApplication
public class WebschoolApplication {

	public static void main(String[] args) {
		SpringApplication.run(WebschoolApplication.class, args);
	}

}
