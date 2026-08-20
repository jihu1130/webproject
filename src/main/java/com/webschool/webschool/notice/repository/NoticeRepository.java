package com.webschool.webschool.notice.repository;

import com.webschool.webschool.notice.domain.Notice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NoticeRepository extends JpaRepository<Notice, Long> {
    Optional<Notice> findByActiveTrue();

    // 관리자 이력 조회 + 공개 "공지" 탭에서 공용으로 쓰는 전체 목록(최신순) - 다른 관리자 목록 화면들과
    // 동일하게 메모리에서 페이지네이션한다(PageUtils.paginate() 참고, 공지 개수 규모가 작다고 가정).
    List<Notice> findAllByOrderByCreatedAtDesc();
}
