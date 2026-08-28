package com.webschool.webschool.post.service;

import com.webschool.webschool.global.upload.FileStorageService;
import com.webschool.webschool.post.domain.Post;
import com.webschool.webschool.post.domain.PostImage;
import com.webschool.webschool.post.dto.PostImageDto;
import com.webschool.webschool.post.repository.PostImageRepository;
import com.webschool.webschool.post.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

// 게시글 첨부 이미지 업로드/삭제. 실제 파일 저장은 FileStorageService(로컬 디스크/S3)에 위임하고
// DB에는 경로/URL만 저장한다.
@Service
@RequiredArgsConstructor
public class PostImageService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "gif", "webp");
    private static final long MAX_FILE_SIZE = 5L * 1024 * 1024; // 장당 5MB

    private final PostImageRepository postImageRepository;
    private final PostRepository postRepository;
    private final FileStorageService fileStorageService;

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
