package com.webschool.webschool.poll.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

// 게시글/한마디 작성 폼에 첨부하는 설문 입력값 - PostFormDto/한마디 작성 폼과 달리 th:object로
// 바인딩하지 않고(이미지 첨부 파라미터와 같은 방식) 컨트롤러가 개별 @RequestParam으로 받아 조립한다.
@Getter @Setter
public class PollCreateRequest {
    private String question;
    private List<String> options;
    private boolean allowMultiple;
    private boolean allowCustomOption;
    private boolean anonymous;
    private String visibilityScope; // Poll.VisibilityScope enum name (비어있으면 SAME_CLASS)
    private boolean sameSchoolOnly = true;
}
