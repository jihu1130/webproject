package com.webschool.webschool.user.mypage.dto;

import java.time.Instant;

// MyPageService.updateProfile() 결과. usernameChanged면 다시 로그인해야 하고(토큰의 주인 이름이 바뀜),
// tokensRevokedAt이 있으면 비밀번호가 바뀌어 기존 토큰이 전부 무효화된 것이라 컨트롤러가 그 시각으로
// 새 토큰을 내줘야 지금 브라우저의 로그인이 유지된다(null이면 비밀번호는 안 바뀜).
public record ProfileUpdateResult(boolean usernameChanged, Instant tokensRevokedAt) {
}
