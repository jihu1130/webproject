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

    @Enumerated(EnumType.STRING)
    private Role role; // ROLE_USER, ROLE_ADMIN

    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean deleted; // 소프트 딜리트 - 계정 탈퇴 시 true (물리적으로는 남아있음, 로그인 차단)

    private LocalDateTime deletedAt; // 탈퇴한 경우에만 값이 채워짐

    public enum Role {
        ROLE_USER, ROLE_ADMIN
    }
}