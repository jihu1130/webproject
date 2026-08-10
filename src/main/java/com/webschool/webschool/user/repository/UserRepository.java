package com.webschool.webschool.user.repository;

import com.webschool.webschool.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    boolean existsByUsername(String username); // 아이디 중복 체크
    List<User> findAllByOrderByIdAsc(); // 관리자용 전체 계정 목록
    int countByRoleAndDeletedFalse(User.Role role); // 마지막 남은 관리자 보호용
}