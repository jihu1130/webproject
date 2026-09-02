package com.webschool.webschool.post.service;

import com.webschool.webschool.global.upload.FileStorageService;
import com.webschool.webschool.post.domain.Post;
import com.webschool.webschool.post.domain.PostImage;
import com.webschool.webschool.post.dto.PostImageDto;
import com.webschool.webschool.post.repository.PostImageRepository;
import com.webschool.webschool.post.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

// 게시글 첨부 이미지 업로드/삭제. 실제 파일 저장은 FileStorageService(로컬 디스크/S3)에 위임하고
// DB에는 경로/URL만 저장한다.
@Slf4j
@Service
@RequiredArgsConstructor
public class PostImageService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "gif", "webp");
    private static final long MAX_FILE_SIZE = 5L * 1024 * 1024; // 장당 5MB

    // 목록/검색 결과 카드는 44px 정사각형으로만 보여주지만(post.css .post-list-thumbnail),
    // 레티나 디스플레이·향후 더 큰 썸네일 레이아웃을 감안해 여유 있게 잡은 값.
    private static final int THUMBNAIL_MAX_DIMENSION = 320;

    private final PostImageRepository postImageRepository;
    private final PostRepository postRepository;
    private final FileStorageService fileStorageService;

    public List<PostImageDto> getImages(Long postId) {
        return postImageRepository.findByPost_IdOrderBySortOrderAsc(postId).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    // 게시글 목록/검색 결과 카드에 쓰는 "대표 이미지" - 별도 필드 없이 게시글의 첫 번째(sortOrder
    // 최소) 첨부 이미지를 그대로 대표 이미지로 쓴다(post/form.html의 "대표 이미지" 업로드 위젯이
    // 저장하는 곳이 본문 삽입 이미지와 동일한 PostImage 목록이라, 별도 스키마 없이 이 규칙만으로
    // "목록·검색 결과에 썸네일로 보여요"라는 기존 안내 문구를 실제로 만족시킬 수 있다).
    // 예전엔 원본 파일(최대 5MB)을 44px 썸네일 하나 그리려고 그대로 내려줬는데, 실제 사진처럼
    // 용량이 크면 목록 로딩이 눈에 띄게 버벅였다(사용자 지적) - thumbnailPath가 있으면(=
    // createThumbnail()이 성공한 경우) 그 축소본을, 없으면(webp처럼 ImageIO가 못 읽는 형식이거나
    // 마이그레이션 이전 행) 기존처럼 원본을 그대로 쓴다.
    public Map<Long, String> getThumbnailUrls(Collection<Long> postIds) {
        if (postIds == null || postIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> result = new LinkedHashMap<>();
        for (PostImage image : postImageRepository.findByPost_IdInOrderByPost_IdAscSortOrderAsc(postIds)) {
            String path = image.getThumbnailPath() != null ? image.getThumbnailPath() : image.getStoredPath();
            result.putIfAbsent(image.getPost().getId(), toServableUrl(path));
        }
        return result;
    }

    // 실제 저장 전에 형식/용량을 먼저 검증해서, 검증 실패 시 게시물 생성/수정 자체가 일어나지 않도록 컨트롤러에서 먼저 호출한다.
    public void validate(List<MultipartFile> files) {
        if (files == null) {
            return;
        }
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                continue;
            }
            validateOne(file);
        }
    }

    @Transactional
    public void saveImages(Long postId, String username, List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            return;
        }

        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("게시물을 찾을 수 없습니다."));
        if (!post.getAuthor().getUsername().equals(username)) {
            throw new IllegalArgumentException("본인이 작성한 게시물에만 이미지를 추가할 수 있습니다.");
        }

        int nextOrder = postImageRepository.countByPost_Id(postId);

        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                continue;
            }
            validateOne(file);

            String ext = extensionOf(file.getOriginalFilename());
            String key = "posts/" + postId + "/" + UUID.randomUUID() + "." + ext;

            String url;
            try {
                url = fileStorageService.store(file, key);
            } catch (IOException e) {
                throw new IllegalArgumentException("이미지 저장에 실패했습니다.");
            }

            PostImage image = new PostImage();
            image.setPost(post);
            image.setStoredPath(url);
            image.setThumbnailPath(createThumbnail(file, postId));
            image.setOriginalFilename(file.getOriginalFilename());
            image.setSortOrder(nextOrder++);
            postImageRepository.save(image);
        }
    }

    @Transactional
    public void deleteImages(Long postId, String username, List<Long> imageIds) {
        if (imageIds == null || imageIds.isEmpty()) {
            return;
        }

        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("게시물을 찾을 수 없습니다."));
        if (!post.getAuthor().getUsername().equals(username)) {
            throw new IllegalArgumentException("본인이 작성한 게시물의 이미지만 삭제할 수 있습니다.");
        }

        List<PostImage> images = postImageRepository.findAllById(imageIds).stream()
                .filter(image -> image.getPost().getId().equals(postId))
                .collect(Collectors.toList());

        images.forEach(image -> {
            deletePhysicalFile(image.getStoredPath());
            if (image.getThumbnailPath() != null) {
                deletePhysicalFile(image.getThumbnailPath());
            }
        });
        postImageRepository.deleteAll(images);
    }

    // ImageIO는 jpg/png/gif는 JDK 내장으로 바로 읽지만 webp는 못 읽는다(별도 플러그인 필요) -
    // 새 의존성을 추가하는 대신, 못 읽는 형식이면 조용히 null을 반환해 getThumbnailUrls()가
    // 원본으로 폴백하게 한다(webp 업로드 자체는 계속 정상 동작 - 목록 썸네일만 원본 그대로).
    // 리사이즈 실패가 업로드 자체를 막으면 안 되므로 예외를 던지지 않고 로그만 남긴다.
    private String createThumbnail(MultipartFile file, Long postId) {
        try {
            BufferedImage original = ImageIO.read(file.getInputStream());
            if (original == null) {
                return null;
            }

            int width = original.getWidth();
            int height = original.getHeight();
            double scale = Math.min(1.0, (double) THUMBNAIL_MAX_DIMENSION / Math.max(width, height));
            int targetWidth = Math.max(1, (int) Math.round(width * scale));
            int targetHeight = Math.max(1, (int) Math.round(height * scale));

            BufferedImage resized = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = resized.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.drawImage(original, 0, 0, targetWidth, targetHeight, null);
            g.dispose();

            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            ImageIO.write(resized, "png", buffer);

            String key = "posts/" + postId + "/thumb/" + UUID.randomUUID() + ".png";
            return fileStorageService.store(buffer.toByteArray(), "image/png", key);
        } catch (IOException | RuntimeException e) {
            log.warn("게시글 {} 첨부 이미지 썸네일 생성 실패 - 원본으로 폴백합니다.", postId, e);
            return null;
        }
    }

    private void validateOne(MultipartFile file) {
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("이미지 용량은 장당 5MB 이하만 업로드할 수 있습니다.");
        }
        String ext = extensionOf(file.getOriginalFilename());
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new IllegalArgumentException("jpg, jpeg, png, gif, webp 형식의 이미지만 업로드할 수 있습니다.");
        }
    }

    private String extensionOf(String filename) {
        if (filename == null) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "";
        }
        return filename.substring(dot + 1).toLowerCase();
    }

    private void deletePhysicalFile(String storedPath) {
        // storedPath는 이제 store()가 반환한 완전한 URL(로컬 "/uploads/..." 또는 S3 전체 URL)이지만,
        // S3 전환 이전에 저장된 기존 행은 접두사 없는 상대경로("posts/123/uuid.jpg")다 -
        // FileStorageService.delete()가 이해하지 못하는 형식이므로 그 경우만 "/uploads/"를 붙여준다.
        String url = toServableUrl(storedPath);
        fileStorageService.delete(url);
    }

    private PostImageDto toDto(PostImage image) {
        return PostImageDto.builder()
                .id(image.getId())
                .url(toServableUrl(image.getStoredPath()))
                .originalFilename(image.getOriginalFilename())
                .sortOrder(image.getSortOrder())
                .build();
    }

    // S3 전환(2026-08-28) 이전 행은 DB에 상대경로만 있고, 이후 행은 store()가 반환한 완전한 URL이
    // 그대로 들어있다 - 마이그레이션 스크립트 없이 두 형식을 한 번에 지원하기 위한 분기.
    private String toServableUrl(String storedPath) {
        return (storedPath.startsWith("http") || storedPath.startsWith("/"))
                ? storedPath
                : "/uploads/" + storedPath;
    }
}
