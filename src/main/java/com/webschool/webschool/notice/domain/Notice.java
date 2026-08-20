package com.webschool.webschool.notice.domain;

import com.webschool.webschool.user.domain.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

// 공지사항 - 일반 게시글(Post)과 완전히 분리된 별도 모델. "활성 공지는 항상 정확히 1개"라는 제약을
// DB 제약이 아니라 NoticeService.createNotice()가 새 공지를 만들 때마다 기존 활성 공지를 먼저
// 비활성화하는 방식으로 보장한다 - 다만 이 read-then-write 자체는 원자적이지 않아 동시에 여러
// 관리자가 작성하면 활성 공지가 2개가 될 수 있었던 버그가 있었고, createNotice()에 synchronized를
// 붙여 고쳤다(자세한 이유는 NoticeService의 createNotice() 주석 참고). 비활성(과거) 공지는 삭제
// (deleted=true)해도 물리적으로 지우지 않고 소프트 삭제로 보관한다(Post/PostComment와 동일 패턴).
@Entity
@Table(name = "notices")
@Getter @Setter
@NoArgsConstructor
public class Notice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false, length = 4000)
    private String content;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean active;

    // 버그수정 프롬포트 요청 - 예전엔 작성 후 오타가 있어도 고칠 방법이 없어서(수정 자체가 없었음)
    // 새 공지를 다시 올려야 했고, 그러면 전체 회원에게 알림이 또 발송됐다.
    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean deleted;

    private LocalDateTime deletedAt;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
