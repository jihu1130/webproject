package com.webschool.webschool;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.HashMap;
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
		Map<String, Object> defaults = new HashMap<>();
		defaults.put("logging.pattern.level", "%5p [%X{requestId:--} %X{user:--}]");

		// 정적 파일(css/js/images) 캐시와 압축(2026-10-07). 예전엔 정적 파일까지 Spring Security 기본값인
		// "Cache-Control: no-store"로 나가서 페이지를 넘길 때마다 CSS/JS 전부를 압축 없이 다시 받았다.
		// - 내용 해시를 파일명에 붙인 주소로 내보낸다(/css/theme.css -> /css/theme-<해시>.css). 템플릿의
		//   th:href/th:src="@{...}"가 자동으로 바뀌므로 화면 코드는 그대로다. 파일 내용이 바뀌면 주소가
		//   바뀌니 1년 캐시를 걸어도 옛 파일이 남지 않는다.
		// - **정적 파일 주소를 @{...} 없이 직접 쓰면(href="/css/x.css", JS 안의 '/js/x.js') 해시가 안 붙어서
		//   그 파일은 고쳐도 사용자 브라우저에 1년간 옛 버전이 남는다** - 반드시 th:href/th:src로 쓸 것.
		// - 업로드 파일(/uploads/**)은 WebConfig의 별도 핸들러라 이 설정과 무관하다.
		defaults.put("spring.web.resources.chain.strategy.content.enabled", "true");
		defaults.put("spring.web.resources.chain.strategy.content.paths", "/css/**,/js/**,/images/**");
		defaults.put("spring.web.resources.cache.cachecontrol.max-age", "365d");
		defaults.put("spring.web.resources.cache.cachecontrol.cache-public", "true");
		// 응답 압축(gzip) - 2KB 넘는 html/css/js/json. 앞단 nginx가 따로 압축하지 않아서 앱에서 한다.
		defaults.put("server.compression.enabled", "true");
		defaults.put("server.compression.mime-types",
				"text/html,text/css,text/javascript,application/javascript,application/json,image/svg+xml,text/plain");

		app.setDefaultProperties(defaults);
		app.run(args);
	}

}
