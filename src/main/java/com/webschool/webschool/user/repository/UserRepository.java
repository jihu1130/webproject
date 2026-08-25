package com.webschool.webschool.user.repository;

import com.webschool.webschool.user.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    boolean existsByUsername(String username); // 아이디 중복 체크
    List<User> findAllByOrderByIdAsc(); // 관리자용 전체 계정 목록
    Optional<User> findByProviderAndProviderId(User.Provider provider, String providerId); // 소셜 로그인 계정 조회
    boolean existsByEmail(String email);
    Optional<User> findByEmail(String email);

    // 포인트 적립 - PostRepository.incrementLikeCount()와 동일한 이유(lost update 방지)로
    // 원자적 벌크 UPDATE. UserPointService.award()에서만 호출한다.
    @Modifying
    @Query("UPDATE User u SET u.points = u.points + :delta WHERE u.id = :id")
    void addPoints(@Param("id") Long id, @Param("delta") int delta);
}