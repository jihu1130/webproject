package com.webschool.webschool.school.repository;

import com.webschool.webschool.school.domain.ScheduleCommentLike;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ScheduleCommentLikeRepository extends JpaRepository<ScheduleCommentLike, Long> {
    Optional<ScheduleCommentLike> findByComment_IdAndUser_Id(Long commentId, Long userId);

    boolean existsByComment_IdAndUser_Username(Long commentId, String username);
}
