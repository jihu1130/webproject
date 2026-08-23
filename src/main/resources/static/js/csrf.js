// CSRF 재활성화 대응 공용 헬퍼 - modal.js/notification.js와 동일한 "작은 전역 헬퍼" 컨벤션.
// 이 스크립트를 로드하는 페이지는 <head>에 아래 두 메타 태그가 있어야 한다:
//   <meta name="_csrf" th:content="${_csrf.token}">
//   <meta name="_csrf_header" th:content="${_csrf.headerName}">
// (Spring Security가 CSRF 활성화 시 `_csrf` 요청 속성을 자동으로 채워주므로 컨트롤러 쪽
// 추가 코드 없이 템플릿에서 바로 읽을 수 있다.)
var WebSchoolCsrf = {
    token: function () {
        var meta = document.querySelector('meta[name="_csrf"]');
        return meta ? meta.content : null;
    },
    headerName: function () {
        var meta = document.querySelector('meta[name="_csrf_header"]');
        return meta ? meta.content : null;
    },
    // fetch() 호출의 headers 객체에 그대로 합쳐 쓰기 위한 헬퍼
    headers: function () {
        var result = {};
        var name = this.headerName();
        var token = this.token();
        if (name && token) result[name] = token;
        return result;
    }
};
