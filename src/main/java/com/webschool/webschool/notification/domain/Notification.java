package com.webschool.webschool.notification.domain;

import com.webschool.webschool.user.domain.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "notifications")
@Getter @Setter
@NoArgsConstructor
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipient_id", nullable = false)
    private User recipient;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Type type;

    @Column(nullable = false, length = 200)
    private String message;

    @Column(length = 300)
    private String link; // 클릭 시 이동할 경로 - 없으면 알림 목록에 그대로 머무른다

    // 컬럼명을 "read"로 그대로 쓰면 MySQL 예약어와 충돌해서 Hibernate가 만드는 CREATE TABLE 구문이
    // 조용히 실패한다(테이블 자체가 안 만들어짐 - ddl-auto=update가 개별 DDL 실패를 무시하고 넘어가서
    // 앱 기동은 되지만 이후 이 테이블을 쓰는 모든 쿼리가 깨진다). 자바 필드/getter/setter는 read를
    // 그대로 쓰고 실제 컬럼명만 is_read로 바꿔서 피했다.
    @Column(name = "is_read", nullable = false, columnDefinition = "boolean default false")
    private boolean read;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        this.read = false;
    }

    public enum Type {
        COMMENT("댓글"), LIKE("좋아요"), REPORT_ACTION("신고 처리"), ACCOUNT("계정"), ANNOUNCEMENT("공지사항");

        private final String label;

        Type(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }
}
