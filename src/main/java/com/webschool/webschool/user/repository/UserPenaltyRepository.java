package com.webschool.webschool.user.repository;

import com.webschool.webschool.user.domain.UserPenalty;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
