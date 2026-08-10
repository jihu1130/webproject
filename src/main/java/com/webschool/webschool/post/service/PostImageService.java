package com.webschool.webschool.post.service;

import com.webschool.webschool.post.domain.Post;
import com.webschool.webschool.post.domain.PostImage;
import com.webschool.webschool.post.dto.PostImageDto;
import com.webschool.webschool.post.repository.PostImageRepository;
import com.webschool.webschool.post.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

// 게시글 첨부 이미지 업로드/삭제. 파일은 프로젝트 외부(app.upload.dir)에 저장하고 DB에는 경로만 저장한다.
@Service
@RequiredArgsConstructor
public class PostImageService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "gif", "webp");
    private static final long MAX_FILE_SIZE = 5L * 1024 * 1024; // 장당 5MB

    private final PostImageRepository postImageRepository;
    private final PostRepository postRepository;

    @Value("${app.upload.dir}")
    private String uploadDir;

    public List<PostImageDto> getImages(Long postId) {
        return postImageRepository.findByPost_IdOrderBySortOrderAsc(postId).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
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
        Path postDir = resolvePostDir(postId);

        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                continue;
            }
            validateOne(file);

            String ext = extensionOf(file.getOriginalFilename());
            String storedFilename = UUID.randomUUID() + "." + ext;

            try {
                Files.createDirectories(postDir);
                Files.copy(file.getInputStream(), postDir.resolve(storedFilename), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                throw new IllegalArgumentException("이미지 저장에 실패했습니다.");
            }

            PostImage image = new PostImage();
            image.setPost(post);
            image.setStoredPath("posts/" + postId + "/" + storedFilename);
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

        images.forEach(image -> deletePhysicalFile(image.getStoredPath()));
        postImageRepository.deleteAll(images);
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

    private Path resolvePostDir(Long postId) {
        return baseDir().resolve("posts").resolve(String.valueOf(postId));
    }

    private Path baseDir() {
        return Paths.get(uploadDir).toAbsolutePath().normalize();
    }

    private void deletePhysicalFile(String storedPath) {
        try {
            Files.deleteIfExists(baseDir().resolve(storedPath));
        } catch (IOException ignored) {
            // 원본 파일이 이미 없어도 DB 정리는 계속 진행한다.
        }
    }

    private PostImageDto toDto(PostImage image) {
        return PostImageDto.builder()
                .id(image.getId())
                .url("/uploads/" + image.getStoredPath())
                .originalFilename(image.getOriginalFilename())
                .sortOrder(image.getSortOrder())
                .build();
    }
}
