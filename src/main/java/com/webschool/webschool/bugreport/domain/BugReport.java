package com.webschool.webschool.bugreport.domain;

import com.webschool.webschool.user.domain.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

// 버그 리포트 - 비로그인 사용자도 제출 가능(사용자 확정 정책)해서 reporter가 nullable이다.
// 로그인 상태로 제출하면 계정과 연결하고, 비로그인이면 reporterNickname/contactEmail로만 식별한다.
// 관리 화면은 총관리자 전용으로 고정(AdminAccessInterceptor 참고) - 위임 권한 플래그를 따로 두지 않았다.
@Entity
@Table(name = "bug_reports")
@Getter @Setter
@NoArgsConstructor
public class BugReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 기존 "버그리포트"를 "문의"로 확장(todo.md 요구사항) - 기존 데이터 호환을 위해 기본값 BUG.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, columnDefinition = "varchar(20) default 'BUG'")
    private Category category = Category.BUG;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false, length = 2000)
    private String content;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reporter_id")
    private User reporter; // 비로그인 제출이면 null

    @Column(length = 30)
    private String reporterNickname; // 비로그인 제출 시 표시용(선택 입력)

    @Column(length = 100)
    private String contactEmail; // 선택 입력 - 관리자가 후속 연락할 때 사용

    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean resolved;

    private LocalDateTime resolvedAt;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

    public enum Category {
        BUG("버그"), SUGGESTION("건의"), ACCOUNT("계정 문의"), OTHER("기타");

        private final String label;

        Category(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }
}
