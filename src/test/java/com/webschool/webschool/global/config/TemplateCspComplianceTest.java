package com.webschool.webschool.global.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

// 보안 점검 L4(2026-10-07) - Content-Security-Policy가 nonce 없는 인라인 <script>와 on* 속성을 막는다.
// 템플릿에 하나라도 섞이면 에러 화면 없이 그 스크립트만 조용히 안 돌아서(브라우저 콘솔에만 뜸) 놓치기 쉽다 -
// 실제로 도입 때 로그인한 사용자에게만 렌더링되는 <script th:if=...> 2개를 빠뜨려 캘린더가 내 학교 대신
// 기본 학교를 보여줬다. 그래서 화면을 하나씩 열어보는 대신 템플릿 파일 전체를 검사한다.
class TemplateCspComplianceTest {

    private static final Path TEMPLATES = Path.of("src/main/resources/templates");
    private static final Pattern SCRIPT_TAG = Pattern.compile("<script\\b[^>]*>");
    private static final Pattern INLINE_HANDLER = Pattern.compile("\\s(th:)?on[a-z]+\\s*=\\s*[\"']");
    private static final Pattern TAG = Pattern.compile("<[a-zA-Z][^>]*>", Pattern.DOTALL);

    @Test
    void everyInlineScriptCarriesNonce() throws IOException {
        List<String> violations = new ArrayList<>();
        forEachTemplate((file, html) -> {
            Matcher matcher = SCRIPT_TAG.matcher(html);
            while (matcher.find()) {
                String tag = matcher.group();
                boolean external = tag.contains("src=");
                if (!external && !tag.contains("th:attr=\"nonce=${cspNonce}\"")) {
                    violations.add(file + ": " + tag);
                }
            }
        });

        assertTrue(violations.isEmpty(),
                "인라인 <script>에 th:attr=\"nonce=${cspNonce}\"가 없습니다:\n" + String.join("\n", violations));
    }

    @Test
    void noInlineEventHandlerAttributes() throws IOException {
        List<String> violations = new ArrayList<>();
        forEachTemplate((file, html) -> {
            Matcher tags = TAG.matcher(html);
            while (tags.find()) {
                if (INLINE_HANDLER.matcher(tags.group()).find()) {
                    violations.add(file + ": " + tags.group().replaceAll("\\s+", " "));
                }
            }
        });

        assertTrue(violations.isEmpty(),
                "on* 속성은 CSP에 막힙니다 - static/js/ui-actions.js의 data 속성이나 addEventListener를 쓰세요:\n"
                        + String.join("\n", violations));
    }

    // CDN에서 불러오는 스크립트/스타일에는 integrity(SRI 해시)가 있어야 한다 - CSP는 출처만 제한하므로, 허용된
    // CDN의 파일 내용이 바뀌는 것은 이 해시로만 막을 수 있다. crossorigin이 없으면 브라우저가 해시를 검사하지
    // 못하고 파일을 아예 막는다.
    @Test
    void everyCdnResourceHasIntegrity() throws IOException {
        Pattern external = Pattern.compile("<(script|link)\\b[^>]*\\b(src|href)=\"https?://[^>]*>", Pattern.DOTALL);
        List<String> violations = new ArrayList<>();
        forEachTemplate((file, html) -> {
            Matcher matcher = external.matcher(html);
            while (matcher.find()) {
                String tag = matcher.group();
                if (!tag.contains("integrity=\"sha") || !tag.contains("crossorigin=\"anonymous\"")) {
                    violations.add(file + ": " + tag.replaceAll("\\s+", " "));
                }
            }
        });

        assertTrue(violations.isEmpty(),
                "CDN 자원에 integrity/crossorigin이 없습니다(fragments/head.html 주석 참고):\n"
                        + String.join("\n", violations));
    }

    // 우리 서버의 정적 파일은 반드시 th:href/th:src="@{...}"로 써야 한다(2026-10-07). 정적 파일에 내용 해시가 붙은
    // 주소 + 1년 캐시를 쓰는데(WebschoolApplication 기본 속성), 해시는 @{...}를 거칠 때만 붙는다 - href="/css/x.css"처럼
    // 직접 쓰면 그 파일은 고쳐도 사용자 브라우저에 옛 버전이 1년간 남는다.
    @Test
    void localStaticFilesAreReferencedThroughThymeleafUrls() throws IOException {
        Pattern plain = Pattern.compile("\\s(src|href)=\"/(css|js|images)/[^\"]*\"");
        List<String> violations = new ArrayList<>();
        forEachTemplate((file, html) -> {
            Matcher matcher = plain.matcher(html);
            while (matcher.find()) {
                violations.add(file + ": " + matcher.group().trim());
            }
        });

        assertTrue(violations.isEmpty(),
                "정적 파일 주소는 th:href/th:src=\"@{...}\"로 써야 캐시 무효화용 해시가 붙습니다:\n"
                        + String.join("\n", violations));
    }

    private interface TemplateVisitor {
        void visit(Path file, String html);
    }

    private void forEachTemplate(TemplateVisitor visitor) throws IOException {
        try (Stream<Path> files = Files.walk(TEMPLATES)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".html")).toList()) {
                visitor.visit(TEMPLATES.relativize(file), Files.readString(file, StandardCharsets.UTF_8));
            }
        }
    }
}
