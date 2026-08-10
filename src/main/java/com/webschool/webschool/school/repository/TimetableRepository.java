package com.webschool.webschool.school.repository;

import com.webschool.webschool.school.domain.Timetable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;

public interface TimetableRepository extends JpaRepository<Timetable, Long> {
    List<Timetable> findBySchoolIdAndGradeAndClassNmAndClassDate(
            Long schoolId, Integer grade, String classNm, LocalDate classDate
    );
}