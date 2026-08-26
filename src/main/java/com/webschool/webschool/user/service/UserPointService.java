package com.webschool.webschool.user.service;

import com.webschool.webschool.user.domain.User;
import com.webschool.webschool.user.domain.UserPointLog;
import com.webschool.webschool.user.repository.UserPointLogRepository;
import com.webschool.webschool.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

// 포인트/티어 시스템(todo.md 요구사항) - 활동에 따라 포인트를 적립하는 유일한 창구. 게시글/댓글
// 서비스는 이 서비스의 award()만 호출하면 되고, 적립량/일일 한도 로직은 전부 여기 모아둔다.
// 어뷰징 방지(사용자 요청)로 하루 획득량에 상한을 둔다 - 좋아요 주고받기를 반복하거나 의미 없는
// 짧은 댓글을 도배해서 포인트를 무한정 쌓는 것을 막는다.
@Service
@RequiredArgsConstructor
public class UserPointService {

    // 활동별 적립 포인트 - 노력/영향력이 큰 활동일수록 크게(QnA 답변 채택이 가장 큼, 좋아요
    // 받기가 가장 작음). 사용자에게 "얼마나 활동했는지"가 눈에 보이도록 0에 가까운 값들만
    // 나열하지 않고 활동 종류별로 확실히 구분되게 정함.
    public static final int POST_CREATE = 5;
    public static final int COMMENT_CREATE = 2;
    public static final int LIKE_RECEIVED = 1;
    public static final int ANSWER_ACCEPTED = 15;

    private static final int DAILY_CAP = 30;

    private final UserRepository userRepository;
    private final UserPointLogRepository userPointLogRepository;

    // 오늘 이미 한도만큼 적립했으면 조용히 0점을 주고 끝낸다(에러를 던지지 않음 - 글쓰기/댓글
    // 자체는 한도와 무관하게 항상 성공해야 하고, 포인트를 못 받는 것만으로 충분한 제약이다).
    // 한도에 걸쳐 있으면(예: 오늘 28점 적립 + 이번 활동 5점) 남은 만큼만(2점) 잘라서 준다.
    @Transactional
    public void award(User user, int points, String reason) {
        LocalDate today = LocalDate.now();
        int earnedToday = userPointLogRepository.sumPointsSince(user.getId(), today.atStartOfDay());
        int remaining = DAILY_CAP - earnedToday;
        if (remaining <= 0) {
            return;
        }

        int actual = Math.min(points, remaining);
        userRepository.addPoints(user.getId(), actual);

        UserPointLog log = new UserPointLog();
        log.setUser(user);
        log.setPoints(actual);
        log.setReason(reason);
        userPointLogRepository.save(log);
    }

    // 인기 게시글 주간 콘테스트(todo.md 4번 항목) 전용 - award()와 달리 일일 획득 한도(DAILY_CAP)를
    // 건너뛴다. 콘테스트 보상은 그 주의 특별한 성과에 대한 큰 보너스(1위 30점 등)라, 그날 이미 다른
    // 활동으로 한도를 채웠다는 이유로 조용히 깎이면 "우승했는데 왜 포인트가 안 오르지"라는 혼란만
    // 남긴다. UserPointLog는 award()와 동일하게 남겨서 마이페이지 "포인트 내역"에 그대로 잡힌다.
    @Transactional
    public void awardBonus(User user, int points, String reason) {
        userRepository.addPoints(user.getId(), points);

        UserPointLog log = new UserPointLog();
        log.setUser(user);
        log.setPoints(points);
        log.setReason(reason);
        userPointLogRepository.save(log);
    }
}
