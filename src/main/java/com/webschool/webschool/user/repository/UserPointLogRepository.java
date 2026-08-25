package com.webschool.webschool.user.repository;

import com.webschool.webschool.user.domain.UserPointLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface UserPointLogRepository extends JpaRepository<UserPointLog, Long> {
    // 일일 획득 한도(어뷰징 방지) 계산용 - 오늘 자정 이후 이 사용자가 이미 적립받은 합계.
    @Query("SELECT COALESCE(SUM(l.points), 0) FROM UserPointLog l WHERE l.user.id = :userId AND l.createdAt >= :since")
    int sumPointsSince(@Param("userId") Long userId, @Param("since") LocalDateTime since);
}
