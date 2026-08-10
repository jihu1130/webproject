package com.webschool.webschool.school.repository;

import com.webschool.webschool.school.domain.ScheduleComment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;

public interface ScheduleCommentRepository extends JpaRepository<ScheduleComment, Long> {
    // 일반 사용자용: 삭제되지 않은 한마디만 조회
    List<ScheduleComment> findBySchool_IdAndTargetDateAndGradeAndClassNmAndDeletedFalseOrderByCreatedAtAsc(
            Long schoolId, LocalDate targetDate, String grade, String classNm);

    // 관리자용: 삭제되지 않은 전체 한마디 목록(신고 여부 무관) - "전체" 탭
    List<ScheduleComment> findAllByDeletedFalseOrderByCreatedAtDesc();

    // 관리자용: 소프트 삭제된 한마디 목록 (최근 삭제순) - "삭제됨" 탭
    List<ScheduleComment> findAllByDeletedTrueOrderByDeletedAtDesc();

    // 관리자용: 블라인드 처리됐거나 신고가 누적된 한마디 (삭제된 것은 제외) - "신고 관리" 탭
    @Query("SELECT c FROM ScheduleComment c WHERE c.deleted = false AND (c.blind = true OR c.reportCount > 0) "
            + "ORDER BY c.blind DESC, c.reportCount DESC, c.createdAt DESC")
    List<ScheduleComment> findReportedOrBlindComments();
}
