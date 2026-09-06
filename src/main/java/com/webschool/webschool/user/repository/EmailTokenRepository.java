package com.webschool.webschool.user.repository;

import com.webschool.webschool.user.domain.EmailToken;
import com.webschool.webschool.user.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface EmailTokenRepository extends JpaRepository<EmailToken, Long> {
    Optional<EmailToken> findByToken(String token);
    void deleteByUserAndPurpose(User user, EmailToken.Purpose purpose);

    // 탈퇴 계정 하드 삭제(AccountHardDeleteService) - 인증 토큰은 개인 활동 흔적이라 함께 지운다.
    @Modifying
    @Query("DELETE FROM EmailToken t WHERE t.user.id = :userId")
    void deleteAllByUserId(@Param("userId") Long userId);
}
