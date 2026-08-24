package com.webschool.webschool.post.service;

import com.webschool.webschool.post.domain.PostView;
import com.webschool.webschool.post.repository.PostRepository;
import com.webschool.webschool.post.repository.PostViewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

// 조회수 어뷰징 방지 2단계 중 IP 기반 판단만 담당(세션 기반 판단은 PostController가 그대로 유지) -
// 수정사항.md 지적: 세션만으로는 새 브라우저/시크릿창/세션 만료로 쉽게 우회된다. 같은 IP가 최근
// VIEW_WINDOW 시간 안에 이미 조회한 글이면 세션이 달라도 조회수를 다시 올리지 않는다. 학교
// 공용 네트워크(같은 IP를 여러 학생이 공유)에서는 오탐(진짜 다른 사람의 조회를 못 세는 경우)이
// 생길 수 있지만, 어뷰징 방지 목적상 감수하는 트레이드오프(todo.md에 명시된 개선 방향과 동일).
@Service
@RequiredArgsConstructor
public class PostViewService {

    private static final long VIEW_WINDOW_HOURS = 24;

    private final PostViewRepository postViewRepository;
    private final PostRepository postRepository;

    public boolean recentlyViewedByIp(Long postId, String ip) {
        if (ip == null || ip.isBlank()) {
            return false; // IP를 못 얻으면(테스트 환경 등) 어뷰징 방지 없이 기존 세션 기반 판단만 따른다
        }
        return postViewRepository.existsByPost_IdAndIpAndViewedAtAfter(
                postId, ip, LocalDateTime.now().minusHours(VIEW_WINDOW_HOURS));
    }

    @Transactional
    public void recordView(Long postId, String ip) {
        if (ip == null || ip.isBlank()) {
            return;
        }
        PostView view = new PostView();
        view.setPost(postRepository.getReferenceById(postId)); // DB 조회 없이 프록시만 참조(FK만 필요)
        view.setIp(ip);
        postViewRepository.save(view);
    }
}
