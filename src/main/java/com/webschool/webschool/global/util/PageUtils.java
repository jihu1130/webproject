package com.webschool.webschool.global.util;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// 관리자 목록 화면들은 검색어 필터링을 DB 쿼리가 아니라 메모리에서 처리한다(AdminPostService 등의
// matches() 헬퍼 참고 - 데이터 규모가 작다고 가정). 그 필터링된 List를 커뮤니티(post/list.html)와
// 동일한 페이지네이션 UI(post-pagination)로 보여주기 위해, 이미 필터링된 List를 잘라서
// Spring Data의 Page로 감싸주는 유틸리티. 실제 DB 페이지 쿼리가 아니므로 목록 규모가 커지면
// 이 방식 대신 리포지토리 단 Pageable 쿼리로 바꿔야 한다.
public final class PageUtils {

    public static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MIN_PAGE_SIZE = 5;
    private static final int MAX_PAGE_SIZE = 100;

    private PageUtils() {
    }

    public static int normalizeSize(Integer requestedSize) {
        if (requestedSize == null) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.max(MIN_PAGE_SIZE, Math.min(MAX_PAGE_SIZE, requestedSize));
    }

    public static <T> Page<T> paginate(List<T> items, int page, int size) {
        int safePage = Math.max(page, 0);
        int start = Math.min(safePage * size, items.size());
        int end = Math.min(start + size, items.size());
        return new PageImpl<>(items.subList(start, end), PageRequest.of(safePage, size), items.size());
    }

    // 관리자 목록 화면의 행별 액션(블라인드/삭제/복구 등)을 처리한 뒤, 처리 전에 보고 있던 검색어/
    // 필터/페이지 상태를 잃지 않고 같은 목록으로 돌아가기 위한 리다이렉트 URL 빌더. extraParams는
    // status/keyword/type처럼 컨트롤러마다 다른 필터 파라미터를 담는다(비어있거나 null이면 생략).
    // 이게 없으면 액션 후 항상 목록 맨 처음(검색어 없음, 1페이지)으로 돌아가서 "검색해서 찾은 글을
    // 조치하면 검색 조건이 날아간다"는 문제가 생긴다.
    public static String buildListRedirect(String basePath, Map<String, String> extraParams, int page, int size) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath(basePath);
        for (Map.Entry<String, String> entry : extraParams.entrySet()) {
            if (entry.getValue() != null && !entry.getValue().isBlank()) {
                builder.queryParam(entry.getKey(), entry.getValue());
            }
        }
        builder.queryParam("page", page).queryParam("size", size);
        return "redirect:" + builder.build().encode().toUriString();
    }

    public static Map<String, String> params(String... keyValuePairs) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < keyValuePairs.length; i += 2) {
            map.put(keyValuePairs[i], keyValuePairs[i + 1]);
        }
        return map;
    }
}
