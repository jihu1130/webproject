package com.webschool.webschool.post.domain;

import com.webschool.webschool.user.domain.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.DynamicUpdate;

import java.time.LocalDateTime;
import java.util.UUID;

// @DynamicUpdate - likeCount/viewCount/reportCount는 원자적 벌크 UPDATE(PostRepository.increment*())로
// 갱신하고 그 값을 엔티티 필드에는 다시 반영하지 않는데(PostRepository 참고 - 반영하면 다음 dirty
// flush 때 stale 값으로 덮어써서 race가 재발함), 같은 트랜잭션에서 blind/reportCleared 등 다른
// 필드를 건드리면 Hibernate 기본 동작(전체 컬럼 UPDATE)이 그 stale 카운트까지 같이 덮어쓴다.
// @DynamicUpdate를 켜면 실제로 바뀐 컬럼만 UPDATE에 포함시켜서 이 문제를 근본적으로 막는다.
@Entity
@Table(name = "posts")
@DynamicUpdate
@Getter @Setter
@NoArgsConstructor
public class Post {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // 내부 PK - FK/관리자 화면에서만 쓰고 공개 URL에는 노출하지 않음

    // nullable로 둔 이유: 기존 행이 있는 테이블에 NOT NULL 컬럼을 추가하면 ddl-auto=update가 실패한다
    // (MySQL이 기본값 없는 NOT NULL 추가를 거부함). 신규 게시물은 prePersist()가 항상 값을 채우고,
    // 기존 행은 배포 후 1회 SQL로 백필했다(7번 항목 실행 메모 참고). NULL 허용이어도 unique 제약은
    // MySQL에서 NULL끼리는 충돌하지 않으므로 안전하게 유지된다.
    @Column(unique = true, length = 36)
    private String uuid; // 공개 URL(/posts/{uuid})에 쓰는 값 - 순차적인 숫자 id를 외부에 노출하지 않기 위함

    @Column(nullable = false, length = 100)
    private String title;

    // 리치 에디터(Quill) 산출물 - 저장 전 PostService가 HtmlSanitizer로 정제한 안전한 HTML만 들어간다.
    // 본문 중간에 이미지/동영상/파일 링크가 섞여 들어갈 수 있어 길이가 커질 수 있으므로 MEDIUMTEXT 사용.
    @Column(nullable = false, columnDefinition = "MEDIUMTEXT")
    private String content;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Category category = Category.FREE;

    // 게시글 제한적 공개(링크 전용) - todo.md/캘린더_사이트전반_개선_프롬프트.md 요구사항. 새 토큰
    // 컬럼은 만들지 않는다 - uuid가 이미 추측 불가능한 값이고 상세 페이지(GET /posts/{uuid})는
    // 목록 필터와 무관하게 uuid로 직접 조회되므로, UNLISTED는 "목록/검색엔 안 뜨지만 링크로는
    // 누구나 열람 가능"이 그대로 성립한다(PostRepository.search() 참고).
    // columnDefinition에 default 지정 - 기존 posts 테이블에 이미 있는 행들 때문에(NOT NULL 컬럼을
    // 기본값 없이 추가하면 ddl-auto=update가 실패한다, CLAUDE.md 알려진 함정 참고).
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, columnDefinition = "varchar(20) default 'PUBLIC'")
    private Visibility visibility = Visibility.PUBLIC;

    @Column(nullable = false)
    private int viewCount;

    @Column(nullable = false)
    private int reportCount;

    @Column(nullable = false, columnDefinition = "int default 0")
    private int likeCount; // 좋아요 수 (PostLike 테이블에 비정규화 - 토글할 때마다 +1/-1)

    @Column(nullable = false)
    private boolean blind; // 신고 누적으로 자동 블라인드 처리된 게시물인지 여부

    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean reportCleared; // 관리자가 "문제없음"으로 판결했는지 여부 - true면 재신고해도 카운트 안 오르고 안내만 나감. 게시물이 수정되면 자동으로 false로 리셋됨

    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean deleted; // 소프트 딜리트 - 작성자 본인 삭제 또는 관리자 강제 삭제 시 true (물리적으로는 남아있음)

    private LocalDateTime deletedAt; // 삭제된 경우에만 값이 채워짐

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt; // 수정된 경우에만 값이 채워짐

    @PrePersist
    public void prePersist() {
        this.uuid = UUID.randomUUID().toString();
        this.createdAt = LocalDateTime.now();
        this.viewCount = 0;
        this.reportCount = 0;
        this.likeCount = 0;
        this.blind = false;
        this.reportCleared = false;
        this.deleted = false;
    }

    public enum Category {
        FREE("자유"), ANONYMOUS("익명"), QNA("질의응답");

        private final String label;

        Category(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    public enum Visibility {
        PUBLIC, UNLISTED
    }
}
