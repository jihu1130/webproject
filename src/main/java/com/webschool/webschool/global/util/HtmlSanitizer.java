package com.webschool.webschool.global.util;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Safelist;

// 리치 에디터(Quill)로 작성된 본문(게시글/오늘의 한마디)을 저장/렌더링하기 전에 거치는 HTML
// 새니타이저. 저장 시(PostService/ScheduleCommentService) 한 번 정제해서 DB에는 항상 안전한
// HTML만 남긴다 - th:utext로 그대로 렌더링해도 XSS가 나지 않도록 여기서 막는 게 유일한 방어선이다.
public class HtmlSanitizer {

    // Safelist.relaxed()가 기본으로 허용하는 서식/링크/이미지 태그에 자체 호스팅 동영상 재생에
    // 필요한 video/source만 추가한다. iframe·script·style·on* 이벤트 속성 등은 전부 제외된다.
    // preserveRelativeLinks(true): 업로드 파일 URL이 전부 "/uploads/..." 상대경로라 반드시 필요 -
    // 이게 없으면 Jsoup이 baseUri("") 기준으로 상대경로를 절대경로로 못 만들어서 src/href를 통째로
    // 잘라낸다(처음엔 프로토콜 화이트리스트에 "#relative"라는 문자열을 추가하면 되는 줄 알았는데
    // 그런 API가 존재하지 않아 아무 효과가 없었다 - preserveRelativeLinks가 실제 정식 API).
    // figure/aside: rich-editor.js가 파일 다운로드 카드(RichFileBlot)/"바로가기" 임베드 카드
    // (RichEmbedBlot)를 감싸는 데 쓰는 Quill 커스텀 블롯의 루트 태그 - <a>는 Quill 기본 link
    // 포맷이 이미 쓰고 있어서 태그명이 겹치면 저장된 글을 다시 열었을 때 Quill이 어느 블롯으로
    // 되살릴지 헷갈려 하므로 각각 다른 태그로 분리했다(rich-editor.js 주석 참고).
    private static final Safelist SAFELIST = Safelist.relaxed()
            .preserveRelativeLinks(true)
            .addTags("video", "source", "figure", "aside")
            .addAttributes("video", "src", "controls", "width", "height", "poster")
            .addAttributes("source", "src", "type")
            .addProtocols("video", "src", "http", "https")
            .addProtocols("source", "src", "http", "https")
            .addAttributes("a", "target", "rel", "download")
            .addAttributes(":all", "class");

    private HtmlSanitizer() {
    }

    // baseUri가 비어있으면 preserveRelativeLinks(true)여도 Jsoup이 상대경로("/uploads/...")를
    // 애초에 검증(resolve)할 기준점이 없어서 무조건 잘라낸다 - 실제로 저 URL로 요청을 보내지는
    // 않고 오직 "이 상대경로가 유효한 구조인지" 판단하는 기준으로만 쓰이므로 아무 절대 URL이나 넣어도 된다.
    private static final String DUMMY_BASE_URI = "http://localhost/";

    // prettyPrint(false): Jsoup 기본값은 태그 사이에 들여쓰기/줄바꿈을 끼워넣는데, Quill이 그 결과를
    // quill.root.innerHTML = ... 로 다시 읽어들일 때 그 공백이 빈 <p> 문단처럼 섞여 들어간다
    // (수정 화면 재진입 시 확인됨) - 순수 문자열 그대로 유지해야 저장/재편집 왕복이 깨끗하다.
    private static final Document.OutputSettings OUTPUT_SETTINGS = new Document.OutputSettings().prettyPrint(false);

    public static String sanitize(String html) {
        if (html == null) {
            return "";
        }
        return Jsoup.clean(html, DUMMY_BASE_URI, SAFELIST, OUTPUT_SETTINGS);
    }

    // 목록/알림 등에서 미리보기용 순수 텍스트가 필요할 때 태그만 걷어내고 텍스트만 반환.
    public static String toPlainText(String html) {
        if (html == null) {
            return "";
        }
        return Jsoup.parse(html).text();
    }
}
