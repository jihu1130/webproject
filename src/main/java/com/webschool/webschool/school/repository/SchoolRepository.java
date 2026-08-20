package com.webschool.webschool.school.repository;

import com.webschool.webschool.school.domain.School;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface SchoolRepository extends JpaRepository<School, Long> {
    Optional<School> findBySdSchulCode(String sdSchulCode);
    Optional<School> findBySchoolName(String schoolName);
}