package com.webschool.webschool.user.repository;

import com.webschool.webschool.user.domain.UserPointLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface UserPointLogRepository extends JpaRepository<UserPointLog, Long> {
    // 일일 획득 한도(어뷰징 방지) 계산용 - 오늘 자정 이후 이 사용자가 이미 적립받은 합계.
    @Query("SELECT COALESCE(SUM(l.points), 0) FROM UserPointLog l WHERE l.user.id = :userId AND l.createdAt >= :since")
    int sumPointsSince(@Param("userId") Long userId, @Param("since") LocalDateTime since);

    // 포인트 내역 화면(todo.md 요구사항) - 최신순.
    List<UserPointLog> findByUser_IdOrderByCreatedAtDesc(Long userId);

    // 탈퇴 계정 하드 삭제(AccountHardDeleteService) - 포인트 내역은 개인 활동 흔적이라 함께 지운다.
    @Modifying
    @Query("DELETE FROM UserPointLog l WHERE l.user.id = :userId")
    void deleteAllByUserId(@Param("userId") Long userId);
}
