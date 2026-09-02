package com.webschool.webschool.user.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.DynamicUpdate;

import java.time.LocalDateTime;
import java.util.UUID;

// @DynamicUpdate - Post.java/PostComment.java와 동일한 이유. points를 UserRepository
// .addPoints()로 원자적 벌크 UPDATE하는데, 이 어노테이션이 없으면 같은 계정을 다루는 다른
// 트랜잭션(예: 관리자 계정 관리 화면에서 role 변경)이 자기 메모리에 들고 있던 오래된 points
// 값까지 포함해서 UPDATE를 날려 방금 적립된 포인트를 조용히 덮어쓸 수 있다.
@Entity
@Table(name = "users", uniqueConstraints = @UniqueConstraint(columnNames = {"provider", "provider_id"}))
@DynamicUpdate
@Getter @Setter
@NoArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // 내부 PK - FK/관리자 화면에서만 쓰고 공개 URL에는 노출하지 않음(Post.id와 동일 패턴)

    // nullable인 이유는 Post.uuid와 동일 - 기존 행이 있는 테이블에 NOT NULL로 추가하면
    // ddl-auto=update가 실패한다. 신규 가입자는 prePersist()가 채우고, 기존 계정은 배포 후
    // 1회 SQL로 백필한다.
    @Column(unique = true, length = 36)
    private String uuid; // 공개 프로필 URL(/users/{uuid})에 쓰는 값 - 순번 id를 외부에 노출하지 않기 위함

    @Column(nullable = false, unique = true, length = 50)
    private String username; // 아이디

    @Column(nullable = false)
    private String password; // 암호화된 비밀번호 - 소셜 로그인(GOOGLE) 계정은 본인도 모르는 임의 값(랜덤 UUID를
    // BCrypt 인코딩)이 들어간다. NOT NULL 제약을 유지하면서 폼 로그인으로는 사실상 뚫을 수 없게 하기 위함
    // (컬럼 자체를 nullable로 바꾸는 대신 이 방식을 택함 - CustomUserDetailsService가 항상 password를
    // 그대로 읽어 UserDetails를 만들기 때문에 null이면 다른 예외 처리가 더 필요해짐).

    @Column(nullable = false, length = 50)
    private String nickname; // 사이트 내 활동 별명 (미입력 시 아이디로 대체)

    // 로컬 계정(아이디/비번 직접 가입)과 소셜 로그인 계정을 완전히 별개로 취급한다(사용자 확정 정책,
    // 2026-08-13) - 이메일이 같아도 자동 연동하지 않음. provider+providerId 조합이 유니크(위 @Table
    // 참고) - LOCAL 계정은 providerId가 항상 null이라 유니크 제약에 안 걸린다(MySQL은 NULL끼리
    // 서로 다른 값으로 취급).
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, columnDefinition = "varchar(20) default 'LOCAL'")
    private Provider provider = Provider.LOCAL;

    @Column(name = "provider_id", length = 100)
    private String providerId; // 소셜 로그인 제공자가 발급한 고유 ID(구글 "sub" 클레임) - LOCAL 계정은 null

    private String schoolName; // 관심 학교 이름 (예: 모산중학교)
    private String schoolCode; // 표준학교코드 (NEIS SD_SCHUL_CODE)
    private String atptCode;   // 시도교육청코드 (NEIS ATPT_OFCDC_SC_CODE)
    private String schoolKind; // 학교종류명 (초등학교/중학교/고등학교 등 - 학년 범위 판단용)
    private String grade;      // 학년
    private String classNum;   // 반

    @Column(length = 150)
    private String bio; // 남이 보는 내 프로필(/users/{id})에 표시되는 짧은 소개글 (선택 입력)

    @Column(unique = true, length = 100)
    private String email; // 이메일 인증/비밀번호 찾기용 - 이 필드가 생기기 전 계정은 null(EmailSetupInterceptor가
    // 다음 로그인 시 입력을 강제한다). 구글 계정은 가입 시점에 구글이 준 값으로 자동 채워진다.

    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean emailVerified; // 인증 여부는 강제 게이트가 아니라 마이페이지 배지 표시 용도로만 쓴다
    // (사용자 확정 정책) - 비밀번호 찾기는 이 값과 무관하게 이메일만 등록돼 있으면 항상 동작한다.

    @Enumerated(EnumType.STRING)
    private Role role; // ROLE_USER, ROLE_ADMIN(부관리자), ROLE_SUPER_ADMIN(총관리자 - username="admin" 계정 전용)

    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean deleted; // 소프트 딜리트 - 계정 탈퇴 시 true (물리적으로는 남아있음, 로그인 차단)

    private LocalDateTime deletedAt; // 탈퇴한 경우에만 값이 채워짐

    // 이 삭제가 본인 탈퇴(UserService.deleteAccount())가 아니라 관리자 강제 탈퇴
    // (AdminUserService.deleteUser())인지 구분하는 플래그(2026-09-02 추가). deleted 하나만으로는
    // 둘을 구분할 수 없어서, CustomOAuth2UserService의 "탈퇴했던 구글 계정 재로그인 시 자동 복구"
    // 기능이 본인 탈퇴와 관리자 강제 탈퇴를 구분하지 못하고 관리자가 강제 탈퇴시킨 계정까지
    // 재로그인 한 번으로 되살려버리는 문제가 있었다(실사용자 지적) - 관리자가 직접 복구
    // (AdminUserService.restoreUser())하기 전까지는 이 값이 true인 동안 자동 복구 대상에서 제외된다.
    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean deletedByAdmin;

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

    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean canManageNotices;

    // 수정사항.md #12 지적 - 계정 관리/관리자 권한 부여/감사 로그 3개 화면은 예전엔 위임 슬롯
    // 자체가 없이 AdminAccessInterceptor에 총관리자 전용으로 하드코딩돼 있었다. 이제 다른 4개
    // 권한과 동일하게 개별 플래그로 위임 가능하다. canManageAdminPermissions는 특히 민감한
    // 권한이라(다른 계정의 권한/역할을 바꿀 수 있음) AdminUserService.updatePermissions()에 자기
    // 자신은 수정 못 하게 하는 별도 가드가 있다 - 안 그러면 이 권한을 받은 부관리자가 자기 자신에게
    // 계정 관리 권한까지 추가로 켜버리는 권한 상승이 가능해진다(문서에 명시된 우려 사례).
    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean canManageUsers;

    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean canManageAdminPermissions;

    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean canViewAuditLog;

    // 상점 카탈로그(칭호/색상) 관리 - todo.md "포인트 소비" 항목, 다른 6개 canManage*와 동일한
    // 위임 패턴(AdminAccessInterceptor/admin-permissions.html 참고).
    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean canManageShop;

    // 설문 결과 관리자 화면(todo.md "설문 후속" 항목) 열람 권한 - 게시글/한마디 관리 권한과 별개로
    // 둔다(설문은 게시글/한마디 양쪽에 붙을 수 있어 어느 한쪽 권한에 종속시키지 않기로 판단, poll
    // 패키지가 post/school 어느 쪽에도 속하지 않고 최상위로 분리된 것과 같은 이유).
    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean canManagePolls;

    // 콘테스트 마감 임박 알림(todo.md "설문 후속" 항목) 수신 여부 - 사용자가 마이페이지에서 직접
    // 켜고 끄는 개인 알림 설정(관리자 위임 권한과는 성격이 다름). 기본값은 꺼짐(옵트인) - 사용자
    // 확정 요구사항("알림 키고 끌 수 있게 해서 킨 사람만"). PostContestService.sendDeadlineReminder()
    // 참고.
    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean contestDeadlineAlertEnabled;

    // 댓글/좋아요/답글 알림 개별 on-off(todo.md "고도화 후보" 항목) - contestDeadlineAlertEnabled와
    // 반대로 이 셋은 기존에 이미 항상 켜져 있던 알림을 사용자가 끌 수 있게 여는 것이라 기본값을
    // true로 둔다(옵트아웃 - active 필드와 동일한 이유로 Java 필드 초기값을 직접 줘야 신규
    // 가입 흐름에서도 true로 시작함, User.points 주석 참고). NotificationService.notify()에서
    // Notification.Type별로 이 플래그들을 확인해서 꺼져 있으면 알림 자체를 생성하지 않는다.
    @Column(nullable = false, columnDefinition = "boolean default true")
    private boolean commentAlertEnabled = true;

    @Column(nullable = false, columnDefinition = "boolean default true")
    private boolean likeAlertEnabled = true;

    @Column(nullable = false, columnDefinition = "boolean default true")
    private boolean replyAlertEnabled = true;

    // 로그인 브루트포스 방지(todo.md "고도화 후보" 항목) - UserPenalty와 달리 이력이 아니라
    // 계정당 현재 상태 하나만 필요해서(감사 로그 목적이 아님) User.points처럼 단순 컬럼으로 둔다.
    // LoginAttemptService.MAX_ATTEMPTS(5회) 연속 실패 시 LOCKOUT_MINUTES(15분) 동안 lockedUntil이
    // 채워지고, 그 시각이 지나면 isLocked()가 자동으로 false를 반환한다(UserPenalty.isCurrentlyActive()/
    // SchoolService.isCacheExpired()와 동일한 "만료 처리 없이 읽는 시점에 계산" 패턴 - 별도
    // 잠금 해제 배치가 필요 없다).
    @Column(nullable = false, columnDefinition = "int default 0")
    private int failedLoginAttempts = 0;

    private LocalDateTime lockedUntil; // null이면 잠기지 않은 상태

    public boolean isLocked() {
        return lockedUntil != null && lockedUntil.isAfter(LocalDateTime.now());
    }

    // 포인트/티어 시스템(todo.md 요구사항) - 게시글/댓글 작성, 좋아요 받음, QnA 답변 채택 등
    // 활동에 따라 UserPointService가 적립한다(일일 획득 한도 있음, 어뷰징 방지). 소비형(화폐)
    // 개념으로 설계했지만 소비 기능은 아직 미구현(사용자 확정) - 지금은 오르기만 한다. 신규
    // 가입 시 0점이 아니라 기본 30점에서 시작한다(사용자 요청 - "얼마나 성실한지"가 숫자로
    // 드러나야 하니 텅 빈 0보다 낮은 시작점을 주지 않기 위함). active 필드와 동일한 이유로
    // Java 필드 초기값을 직접 준다 - int 기본값(0)이 JPA INSERT에 그대로 실려서 컬럼의
    // DB 레벨 default는 신규 가입 흐름에서 적용되지 않는다(User는 UserService.register()/
    // CustomOAuth2UserService 등 여러 경로에서 생성되므로 매 생성 지점마다 값을 세팅하는 대신
    // 필드 초기값 하나로 전부 커버).
    @Column(nullable = false, columnDefinition = "int default 30")
    private int points = 30;

    // 프로필 사진 - 업로드한 적 없으면 null(화면에서 static/images/default-avatar.svg로 대체
    // 표시). FileUploadService.storeProfileImage()로 저장하며, 경로만 이 컬럼에 남긴다
    // (게시글 이미지와 동일한 "실제 파일은 app.upload.dir, DB엔 경로만" 패턴).
    private String profileImageUrl;

    // 포인트 소비 상점(todo.md 요구사항) - ShopService.equip()/unequip()이 구매한 ShopItem.value를
    // 여기에 저장하고, 마이페이지/공개 프로필/관리자 프로필 3개 화면이 닉네임 옆 칭호 배지 ·
    // 아바타 테두리 색으로 렌더링한다.
    private String equippedTitle;       // 현재 장착 중인 칭호 문구
    private String equippedAvatarColor; // 현재 장착 중인 아바타 테두리/배지 색상 (CSS 색상값)
    private String equippedEffect;      // 현재 장착한 아바타 색상 상품의 장식 효과(ShopItem.Effect.name()) - NONE/null이면 효과 없음

    @PrePersist
    public void prePersist() {
        this.uuid = UUID.randomUUID().toString();
    }

    public PointTier getTier() {
        return PointTier.forPoints(points);
    }

    // 구글 소셜 로그인으로 처음 가입하면 학교/학년/반이 빈 채로 계정이 만들어진다
    // (로컬 회원가입은 이 정보가 항상 필수라 이 상태가 나오지 않는다) - SchoolSetupInterceptor가
    // 이 값을 보고 학교 설정 화면 강제 이동 여부를 판단한다.
    public boolean needsSchoolSetup() {
        return schoolCode == null || schoolCode.isBlank()
                || grade == null || grade.isBlank()
                || classNum == null || classNum.isBlank();
    }

    public boolean needsEmailSetup() {
        return email == null || email.isBlank();
    }

    public boolean isSuperAdmin() {
        return role == Role.ROLE_SUPER_ADMIN;
    }

    public boolean isAdmin() {
        return role == Role.ROLE_ADMIN || role == Role.ROLE_SUPER_ADMIN;
    }

    // 수정사항.md 지적 - "관리자" 네비 링크가 ROLE_ADMIN이기만 하면 실제 권한(canManage*)이
    // 하나도 없어도 노출돼서, 눌러봐야 "접근 권한 없음" 화면만 마주치는 부관리자가 있었다.
    // 총관리자는 모든 권한을 암묵적으로 갖고, 부관리자는 canManage* 중 하나라도 켜져 있어야 한다.
    public boolean hasAnyAdminAccess() {
        return isSuperAdmin()
                || canManageReports || canManagePosts || canManageScheduleComments || canManageNotices
                || canManageUsers || canManageAdminPermissions || canViewAuditLog || canManageShop
                || canManagePolls;
    }

    public enum Role {
        ROLE_USER, ROLE_ADMIN, ROLE_SUPER_ADMIN
    }

    public enum Provider {
        LOCAL, GOOGLE
    }
}