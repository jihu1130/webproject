package com.webschool.webschool.post.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "post_images")
@Getter @Setter
@NoArgsConstructor
public class PostImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    @Column(nullable = false, length = 255)
    private String storedPath; // app.upload.dir 기준 상대 경로 (예: posts/3/uuid.png)

    // 목록/검색 결과 썸네일 전용 축소본 - PostImageService.createThumbnail() 참고. ImageIO가
    // 못 읽는 형식(webp)이거나 리사이즈에 실패하면 null로 남고, 그 경우 getThumbnailUrls()가
    // storedPath(원본)로 그대로 폴백한다 - 그래서 nullable이고 기존 행(마이그레이션 이전에
    // 업로드된 이미지)도 별도 백필 없이 자동으로 원본 폴백 경로를 탄다.
    @Column(length = 255)
    private String thumbnailPath;

    @Column(nullable = false, length = 255)
    private String originalFilename;

    @Column(nullable = false)
    private int sortOrder;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
