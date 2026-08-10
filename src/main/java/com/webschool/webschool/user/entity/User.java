package com.webschool.webschool.user.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter @Setter
@NoArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String username; // 아이디

    @Column(nullable = false)
    private String password; // 암호화된 비밀번호

    @Column(nullable = false, length = 50)
    private String nickname; // 사이트 내 활동 별명 (미입력 시 아이디로 대체)

    private String schoolName; // 관심 학교 이름 (예: 모산중학교)
    private String schoolCode; // 표준학교코드 (NEIS SD_SCHUL_CODE)
    private String atptCode;   // 시도교육청코드 (NEIS ATPT_OFCDC_SC_CODE)
    private String schoolKind; // 학교종류명 (초등학교/중학교/고등학교 등 - 학년 범위 판단용)
    private String grade;      // 학년
    private String classNum;   // 반

    @Column(length = 150)
    private String bio; // 남이 보는 내 프로필(/users/{id})에 표시되는 짧은 소개글 (선택 입력)

    @Enumerated(EnumType.STRING)
    private Role role; // ROLE_USER, ROLE_ADMIN(부관리자), ROLE_SUPER_ADMIN(총관리자 - username="admin" 계정 전용)

    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean deleted; // 소프트 딜리트 - 계정 탈퇴 시 true (물리적으로는 남아있음, 로그인 차단)

    private LocalDateTime deletedAt; // 탈퇴한 경우에만 값이 채워짐

    // 관리자 계정 정지(비활성화) - 탈퇴(deleted)와는 별개 개념. 탈퇴는 본인/관리자가 계정을 완전히
    // 정리하는 것(닉네임이 "탈퇴한 사용자"로 치환됨)이고, 비활성화는 총관리자가 계정을 그대로 둔 채
    // 로그인만 일시적으로 막는 정지 조치다. 둘 다 로그인 차단 효과는 있지만 별도 플래그로 관리한다.
    @Column(nullable = false, columnDefinition = "boolean default true")
    private boolean active = true;

    // 부관리자(ROLE_ADMIN)에게 총관리자가 개별로 부여하는 관리 권한. ROLE_SUPER_ADMIN 계정은 이
    // 값과 무관하게 항상 모든 권한을 가진 것으로 취급한다(AdminAccessInterceptor 참고).
    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean canManageReports;

    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean canManagePosts;

    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean canManageScheduleComments;

    public boolean isSuperAdmin() {
        return role == Role.ROLE_SUPER_ADMIN;
    }

    public boolean isAdmin() {
        return role == Role.ROLE_ADMIN || role == Role.ROLE_SUPER_ADMIN;
    }

    public enum Role {
        ROLE_USER, ROLE_ADMIN, ROLE_SUPER_ADMIN
    }
}