package com.webschool.webschool.school.repository;

import com.webschool.webschool.school.domain.Meal;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;

public interface MealRepository extends JpaRepository<Meal, Long> {
    List<Meal> findBySchoolIdAndMealDate(Long schoolId, LocalDate mealDate);
}