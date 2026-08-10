package com.webschool.webschool.school.repository;

import com.webschool.webschool.school.domain.School; // School 엔티티 import
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

// ❌ 기존 (에러 원인): public interface SchoolRepository extends JpaRepository<Event, Long>
// ✅ 수정 후: JpaRepository<School, Long> 로 변경
public interface SchoolRepository extends JpaRepository<School, Long> {
    Optional<School> findBySdSchulCode(String sdSchulCode);
    Optional<School> findBySchoolName(String schoolName);
}