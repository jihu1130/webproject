package com.webschool.webschool.user.point.service;

import com.webschool.webschool.user.domain.User;
import com.webschool.webschool.user.point.domain.UserPointLog;
import com.webschool.webschool.user.point.repository.UserPointLogRepository;
import com.webschool.webschool.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// 포인트 적립/차감 규칙 회귀 테스트. 포인트는 상점에서 쓰는 화폐라 "하루 한도를 넘겨 쌓이지 않는다",
// "잔액이 음수가 되지 않는다" 두 가지가 깨지면 바로 어뷰징으로 이어진다. 진입점마다 한도·잔액 부족 처리가
// 일부러 다르게 설계돼 있어서(아래 각 테스트) 한 곳을 고치다 다른 곳 규칙을 섞기 쉽다.
@ExtendWith(MockitoExtension.class)
class UserPointServiceTest {

    private static final int DAILY_CAP = 30;

    @Mock private UserRepository userRepository;
    @Mock private UserPointLogRepository userPointLogRepository;

    @InjectMocks private UserPointService userPointService;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(1L);
        user.setUsername("test1");
        user.setPoints(20);
    }

    private void earnedToday(int points) {
        when(userPointLogRepository.sumPointsSince(eq(1L), any())).thenReturn(points);
    }

    private UserPointLog savedLog() {
        ArgumentCaptor<UserPointLog> captor = ArgumentCaptor.forClass(UserPointLog.class);
        verify(userPointLogRepository).save(captor.capture());
        return captor.getValue();
    }

    // ---- award(): 일일 한도 적용 ----

    @Test
    void award_underCap_givesFullAmount() {
        earnedToday(0);

        userPointService.award(user, UserPointService.POST_CREATE, "게시글 작성");

        verify(userRepository).addPoints(1L, UserPointService.POST_CREATE);
        assertEquals(UserPointService.POST_CREATE, savedLog().getPoints());
    }

    // 한도에 걸치면 남은 만큼만 준다 - 로그에도 요청량이 아니라 실제 지급량이 남아야 다음 합산이 맞는다.
    @Test
    void award_crossingCap_givesOnlyRemainder() {
        earnedToday(DAILY_CAP - 2);

        userPointService.award(user, UserPointService.POST_CREATE, "게시글 작성");

        verify(userRepository).addPoints(1L, 2);
        assertEquals(2, savedLog().getPoints());
    }

    // 한도를 채웠으면 에러 없이 0점 - 글쓰기/댓글 자체는 실패하면 안 된다.
    @Test
    void award_atCap_givesNothingAndDoesNotThrow() {
        earnedToday(DAILY_CAP);

        assertDoesNotThrow(() -> userPointService.award(user, UserPointService.COMMENT_CREATE, "댓글 작성"));

        verify(userRepository, never()).addPoints(anyLong(), anyInt());
        verify(userPointLogRepository, never()).save(any());
    }

    // 좋아요를 받은 글의 작성자가 이미 하드 삭제된 경우 - 500이 나면 좋아요 자체가 실패한다.
    @Test
    void award_toHardDeletedUser_isIgnored() {
        assertDoesNotThrow(() -> userPointService.award(null, UserPointService.LIKE_RECEIVED, "좋아요 받음"));

        verifyNoInteractions(userRepository, userPointLogRepository);
    }

    // ---- awardBonus(): 한도 무시 ----

    // 콘테스트 보상은 그날 한도를 이미 채웠어도 깎이지 않는다 - 한도 합계 자체를 조회하지 않는다.
    @Test
    void awardBonus_ignoresDailyCap() {
        userPointService.awardBonus(user, 30, "주간 콘테스트 1위");

        verify(userRepository).addPoints(1L, 30);
        verify(userPointLogRepository, never()).sumPointsSince(anyLong(), any());
        assertEquals(30, savedLog().getPoints());
    }

    // ---- 차감 3종: 잔액 부족 처리가 서로 다르다 ----

    // 사용자가 스스로 쓰는 소비 - 부족하면 실패(조용히 깎아주면 싸게 사는 셈이 된다).
    @Test
    void spend_moreThanBalance_failsWithoutTouchingPoints() {
        assertThrows(IllegalArgumentException.class, () -> userPointService.spend(user, 21, "상점 구매"));

        verify(userRepository, never()).addPoints(anyLong(), anyInt());
        verify(userPointLogRepository, never()).save(any());
    }

    @Test
    void spend_exactBalance_succeeds() {
        userPointService.spend(user, 20, "상점 구매");

        verify(userRepository).addPoints(1L, -20);
        assertEquals(-20, savedLog().getPoints());
    }

    // 제재 차감 - 부족해도 실패하지 않고 가진 만큼만 깎는다(0 밑으로 안 내려감).
    @Test
    void deductForPenalty_isClampedToBalance() {
        userPointService.deductForPenalty(user, 50, "제재: 경고");

        verify(userRepository).addPoints(1L, -20);
        assertEquals(-20, savedLog().getPoints());
    }

    @Test
    void deductForPenalty_withZeroBalance_doesNothing() {
        user.setPoints(0);

        userPointService.deductForPenalty(user, 50, "제재: 경고");

        verifyNoInteractions(userRepository, userPointLogRepository);
    }

    // 관리자 조정 - 차감은 잔액까지만, 지급은 한도 없이 그대로. 로그 사유에 접두어가 붙어 일반 내역과 구분된다.
    @Test
    void adjustByAdmin_negativeIsClampedToBalance() {
        userPointService.adjustByAdmin(user, -100, "오적립 정정");

        verify(userRepository).addPoints(1L, -20);
        UserPointLog log = savedLog();
        assertEquals(-20, log.getPoints());
        assertEquals("관리자 조정: 오적립 정정", log.getReason());
    }

    @Test
    void adjustByAdmin_positiveIgnoresDailyCap() {
        userPointService.adjustByAdmin(user, 500, "이벤트 보상");

        verify(userRepository).addPoints(1L, 500);
        verify(userPointLogRepository, never()).sumPointsSince(anyLong(), any());
        assertEquals(500, savedLog().getPoints());
    }
}
