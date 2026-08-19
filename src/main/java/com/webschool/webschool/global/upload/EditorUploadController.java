package com.webschool.webschool.global.upload;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

// 리치 에디터(게시글/오늘의 한마디 작성 화면)에서 사진·동영상·파일을 본문 중간에 삽입할 때 쓰는
// 공용 업로드 엔드포인트 - 로그인만 하면 게시글/한마디 어느 화면에서든 재사용한다
// (SecurityConfig의 anyRequest().authenticated()로 이미 인증이 걸려있음).
@RestController
@RequestMapping("/api/uploads")
@RequiredArgsConstructor
public class EditorUploadController {

    private final FileUploadService fileUploadService;

    @PostMapping("/editor")
    @ResponseBody
    public ResponseEntity<?> uploadForEditor(@RequestParam("file") MultipartFile file) {
        try {
            UploadedFileDto dto = fileUploadService.store(file);
            return ResponseEntity.ok(dto);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        }
    }
}
