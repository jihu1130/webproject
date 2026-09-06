package com.webschool.webschool.user.repository;

import com.webschool.webschool.user.domain.UserPenalty;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface UserPenaltyRepository extends JpaRepository<UserPenalty, Long> {

    List<UserPenalty> findByTarget_IdOrderByIssuedAtDesc(Long targetId);

    @Query("SELECT p FROM UserPenalty p WHERE p.target.id = :userId AND p.type = :type " +
           "AND p.revoked = false AND (p.expiresAt IS NULL OR p.expiresAt > :now) " +
           "ORDER BY p.issuedAt DESC")
    List<UserPenalty> findActive(@Param("userId") Long userId, @Param("type") UserPenalty.Type type,
                                  @Param("now") LocalDateTime now);

    // 탈퇴 계정 하드 삭제(AccountHardDeleteService) - 제재 이력은 남기고 대상/부여자 참조만 끊는다.
    // 한 사람이 대상이면서 다른 제재의 부여자(관리자)였을 수도 있어 두 방향 모두 처리해야 한다.
    @Modifying
    @Query("UPDATE UserPenalty p SET p.target = null WHERE p.target.id = :userId")
    void detachTarget(@Param("userId") Long userId);

    @Modifying
    @Query("UPDATE UserPenalty p SET p.issuedBy = null WHERE p.issuedBy.id = :userId")
    void detachIssuedBy(@Param("userId") Long userId);
}
