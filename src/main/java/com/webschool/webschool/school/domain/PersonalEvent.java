package com.webschool.webschool.school.domain;

import com.webschool.webschool.user.domain.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

// 캘린더에 본인만 볼 수 있게 추가하는 개인 일정 - 학교/학년/반과 무관하게 순수하게
// 사용자 소유 데이터라는 점에서 ScheduleComment(같은 반끼리 공유)와 다르고,
// ScheduleCommentBookmark와 동일한 "소유 전용" 패턴을 따른다(하루에 여러 개 등록 가능,
// 유니크 제약 없음 - ScheduleComment도 하루에 여러 개 허용하는 것과 동일).
@Entity
@Table(name = "personal_events")
@Getter @Setter
@NoArgsConstructor
public class PersonalEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private LocalDate eventDate;

    @Column(nullable = false, length = 100)
    private String title;

    // 본인만 보는 메모라 리치 에디터/HtmlSanitizer를 쓰지 않는다 - 순수 텍스트로만 저장하고
    // 프론트에서도 항상 textContent로만 렌더링한다(innerHTML 사용 금지).
    @Column(length = 1000)
    private String memo;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt; // 수정된 경우에만 값이 채워짐

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
