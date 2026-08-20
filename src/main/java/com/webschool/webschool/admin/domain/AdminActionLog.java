package com.webschool.webschool.admin.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

// 관리자 조치(블라인드/해제/삭제/복구/승격/강등/정지/해제 등) 감사 로그 - "누가 언제 무엇을 했는지"
// 기록만 남기는 append-only 성격이라 대상 엔티티를 직접 참조(FK)하지 않는다(대상이 나중에 실제로
// 삭제되더라도 로그 자체는 그대로 남아있어야 함 - FK로 묶으면 대상 삭제 시 문제가 생길 수 있음).
// targetType/targetId는 문자열/숫자로만 보관하고, 화면에서 필요하면 별도로 조회한다.
@Entity
@Table(name = "admin_action_logs")
@Getter @Setter
@NoArgsConstructor
public class AdminActionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String adminUsername; // 조치를 실행한 관리자 계정

    @Column(nullable = false, length = 30)
    private String targetType; // "POST" / "COMMENT" / "SCHEDULE_COMMENT" / "USER"

    @Column(nullable = false)
    private Long targetId;

    @Column(nullable = false, length = 30)
    private String action; // "BLIND" / "UNBLIND" / "REPORT_CLEAR" / "REPORT_UNCLEAR" / "DELETE" / "RESTORE" /
                            // "PROMOTE" / "DEMOTE" / "PERMISSIONS" / "DEACTIVATE" / "ACTIVATE"

    @Column(length = 300)
    private String detail; // 선택 - "ROLE_ADMIN으로 승격" 등 사람이 읽을 부가 설명

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
