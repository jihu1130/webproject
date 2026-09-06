package com.webschool.webschool.notice.repository;

import com.webschool.webschool.notice.domain.Notice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface NoticeRepository extends JpaRepository<Notice, Long> {
    Optional<Notice> findByActiveTrueAndDeletedFalse();

    // 관리자 이력 조회 + 공개 "공지" 탭에서 공용으로 쓰는 전체 목록(최신순) - 다른 관리자 목록 화면들과
    // 동일하게 메모리에서 페이지네이션한다(PageUtils.paginate() 참고, 공지 개수 규모가 작다고 가정).
    // 삭제된 공지는 목록/활성 공지 어느 쪽에도 나타나지 않는다(소프트 삭제라 DB에는 남아있음).
    List<Notice> findAllByDeletedFalseOrderByCreatedAtDesc();

    // 탈퇴 계정 하드 삭제(AccountHardDeleteService) - 공지는 남기고 작성자 참조만 끊는다.
    @Modifying
    @Query("UPDATE Notice n SET n.author = null WHERE n.author.id = :userId")
    void detachAuthor(@Param("userId") Long userId);
}
