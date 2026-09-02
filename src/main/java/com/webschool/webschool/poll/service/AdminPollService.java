package com.webschool.webschool.poll.service;

import com.webschool.webschool.admin.service.AdminActionLogService;
import com.webschool.webschool.poll.domain.Poll;
import com.webschool.webschool.poll.dto.PollAdminDetailDto;
import com.webschool.webschool.poll.dto.PollAdminOptionDto;
import com.webschool.webschool.poll.dto.PollAdminSummaryDto;
import com.webschool.webschool.poll.dto.PollOptionResultDto;
import com.webschool.webschool.poll.dto.PollResultDto;
import com.webschool.webschool.poll.repository.PollOptionRepository;
import com.webschool.webschool.poll.repository.PollRepository;
import com.webschool.webschool.poll.repository.PollVoteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

// 관리자 전용 설문 결과 열람 + 소프트 삭제(todo.md "고도화 후보" - 스팸/악성 설문 대응). 삭제는
// AdminPostService.deletePost()/restorePost()와 동일한 소프트 딜리트 패턴을 그대로 따른다.
@Service
@RequiredArgsConstructor
public class AdminPollService {

    private static final DateTimeFormatter DISPLAY_FORMAT = DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm");

    private final PollRepository pollRepository;
    private final PollOptionRepository pollOptionRepository;
    private final PollVoteRepository pollVoteRepository;
    private final PollService pollService;
    private final AdminActionLogService adminActionLogService;

    // 다른 관리자 목록 화면들(NoticeService.getHistory() 등)과 동일하게 메모리에서 keyword로
    // 필터링한다 - 설문 개수도 학교 커뮤니티 규모라 크지 않다는 같은 전제.
    public List<PollAdminSummaryDto> getAllPolls(String keyword) {
        return pollRepository.findAllByDeletedFalseOrderByCreatedAtDesc().stream()
                .map(this::toSummaryDto)
                .filter(dto -> matches(keyword, dto))
                .collect(Collectors.toList());
    }

    // "삭제됨" 탭 - AdminPostService.getDeletedPosts()와 동일 패턴
    public List<PollAdminSummaryDto> getDeletedPolls(String keyword) {
        return pollRepository.findAllByDeletedTrueOrderByDeletedAtDesc().stream()
                .map(this::toSummaryDto)
                .filter(dto -> matches(keyword, dto))
                .collect(Collectors.toList());
    }

    private boolean matches(String keyword, PollAdminSummaryDto dto) {
        if (keyword == null || keyword.isBlank()) {
            return true;
        }
        String normalized = keyword.trim().toLowerCase();
        return dto.getQuestion().toLowerCase().contains(normalized)
                || dto.getCreatorNickname().toLowerCase().contains(normalized);
    }

    // 관리자는 공개범위(반/학년/링크)와 무관하게 항상 열람 가능 - PollService.canAccess()가 이미
    // viewer.isAdmin()이면 무조건 통과시키므로(관리자 예외), 투표 집계는 PollService.getResultForAdmin()을
    // 그대로 재사용한다(공개용 getResult()와 달리 삭제된 설문도 조회 가능 - 삭제/복구 판단에 필요).
    // 익명 설문의 투표자 리댁션도 학생 화면과 동일하게 적용된다 - 관리자라고 예외를 두지 않는다
    // (CLAUDE.md "익명성 보호가 여러 곳에 걸쳐 일관되게 적용됨" 원칙).
    public PollAdminDetailDto getDetail(Long id, String adminUsername) {
        Poll poll = pollRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("설문을 찾을 수 없습니다."));
        PollResultDto result = pollService.getResultForAdmin(id, adminUsername);

        int totalVotes = result.getOptions().stream().mapToInt(PollOptionResultDto::getVoteCount).sum();
        List<PollAdminOptionDto> options = result.getOptions().stream()
                .map(o -> PollAdminOptionDto.builder()
                        .label(o.getLabel())
                        .voteCount(o.getVoteCount())
                        .percent(totalVotes > 0 ? Math.round((o.getVoteCount() * 100.0f) / totalVotes) : 0)
                        .voterNicknames(o.getVoterNicknames())
                        .build())
                .collect(Collectors.toList());

        return PollAdminDetailDto.builder()
                .id(poll.getId())
                .question(poll.getQuestion())
                .creatorNickname(poll.getCreator().getNickname())
                .targetType(targetType(poll))
                .targetLabel(targetLabel(poll))
                .visibilityScopeLabel(scopeLabel(poll))
                .anonymous(poll.isAnonymous())
                .allowMultiple(poll.isAllowMultiple())
                .allowCustomOption(poll.isAllowCustomOption())
                .createdAt(poll.getCreatedAt().format(DISPLAY_FORMAT))
                .totalVoters(result.getTotalVoters())
                .totalVotes(totalVotes)
                .options(options)
                .deleted(poll.isDeleted())
                .deletedAt(poll.getDeletedAt() != null ? poll.getDeletedAt().format(DISPLAY_FORMAT) : null)
                .build();
    }

    // 관리자 강제 삭제 - AdminPostService.deletePost()와 동일한 소프트 딜리트 패턴. 삭제되면
    // PollRepository의 findBy..._DeletedFalse 계열 조회에서 빠져 게시글/한마디 화면의 설문 위젯
    // 자체가 더 이상 노출되지 않는다(투표 기록은 그대로 보존).
    @Transactional
    public void deletePoll(Long id) {
        Poll poll = pollRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("설문을 찾을 수 없습니다."));
        poll.setDeleted(true);
        poll.setDeletedAt(LocalDateTime.now());
        adminActionLogService.log("POLL", id, "DELETE", truncate(poll.getQuestion()));
    }

    @Transactional
    public void restorePoll(Long id) {
        Poll poll = pollRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("설문을 찾을 수 없습니다."));
        poll.setDeleted(false);
        poll.setDeletedAt(null);
        adminActionLogService.log("POLL", id, "RESTORE", truncate(poll.getQuestion()));
    }

    private String truncate(String text) {
        int limit = 40;
        return text.length() > limit ? text.substring(0, limit) + "..." : text;
    }

    private PollAdminSummaryDto toSummaryDto(Poll poll) {
        return PollAdminSummaryDto.builder()
                .id(poll.getId())
                .question(poll.getQuestion())
                .creatorNickname(poll.getCreator().getNickname())
                .targetType(targetType(poll))
                .targetLabel(targetLabel(poll))
                .visibilityScopeLabel(scopeLabel(poll))
                .anonymous(poll.isAnonymous())
                .allowMultiple(poll.isAllowMultiple())
                .optionCount(pollOptionRepository.findByPoll_IdOrderByIdAsc(poll.getId()).size())
                .totalVoters(pollVoteRepository.countDistinctVotersByPollId(poll.getId()))
                .createdAt(poll.getCreatedAt().format(DISPLAY_FORMAT))
                .deleted(poll.isDeleted())
                .deletedAt(poll.getDeletedAt() != null ? poll.getDeletedAt().format(DISPLAY_FORMAT) : null)
                .build();
    }

    private String targetType(Poll poll) {
        return poll.getPost() != null ? "게시글" : "한마디";
    }

    private String targetLabel(Poll poll) {
        if (poll.getPost() != null) {
            return poll.getPost().getTitle();
        }
        if (poll.getScheduleComment() != null) {
            return poll.getScheduleComment().getTargetDate() + " 한마디";
        }
        return "-";
    }

    private String scopeLabel(Poll poll) {
        return switch (poll.getVisibilityScope()) {
            case SAME_CLASS -> "같은 반";
            case SAME_GRADE -> poll.isSameSchoolOnly() ? "같은 학년(같은 학교)" : "같은 학년(학교 무관)";
            case PUBLIC_LINK -> "전체공개";
        };
    }
}
