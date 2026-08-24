package com.webschool.webschool.user.repository;

import com.webschool.webschool.user.domain.EmailToken;
import com.webschool.webschool.user.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EmailTokenRepository extends JpaRepository<EmailToken, Long> {
    Optional<EmailToken> findByToken(String token);
    void deleteByUserAndPurpose(User user, EmailToken.Purpose purpose);
}
