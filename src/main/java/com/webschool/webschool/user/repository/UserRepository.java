package com.webschool.webschool.user.repository;

import com.webschool.webschool.user.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    boolean existsByUsername(String username); // 아이디 중복 체크
    List<User> findAllByOrderByIdAsc(); // 관리자용 전체 계정 목록
    Optional<User> findByProviderAndProviderId(User.Provider provider, String providerId); // 소셜 로그인 계정 조회
}