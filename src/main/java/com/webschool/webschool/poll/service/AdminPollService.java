package com.webschool.webschool.poll.service;

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
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

// 관리자 전용 설문 결과 열람(todo.md "설문 후속" - 설문 결과를 관리자 화면에서 모아보는 기능).
// Poll에는 삭제/블라인드 개념이 없어(poll 패키지에 소프트 딜리트 미적용) 조회 전용으로만 제공한다
// (AdminProfileController와 동일한 성격의 읽기 전용 관리자 화면).
@Service
@RequiredArgsConstructor
public class AdminPollService {

    private static final DateTimeFormatter DISPLAY_FORMAT = DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm");

    private final PollRepository pollRepository;
    private final PollOptionRepository pollOptionRepository;
    private final PollVoteRepository pollVoteRepository;
    private final PollService pollService;

    // 다른 관리자 목록 화면들(NoticeService.getHistory() 등)과 동일하게 메모리에서 keyword로
    // 필터링한다 - 설문 개수도 학교 커뮤니티 규모라 크지 않다는 같은 전제.
    public List<PollAdminSummaryDto> getAllPolls(String keyword) {
        String normalized = keyword == null ? "" : keyword.trim().toLowerCase();
        return pollRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt")).stream()
                .filter(p -> normalized.isEmpty()
                        || p.getQuestion().toLowerCase().contains(normalized)
                        || p.getCreator().getNickname().toLowerCase().contains(normalized))
                .map(this::toSummaryDto)
                .collect(Collectors.toList());
    }

    // 관리자는 공개범위(반/학년/링크)와 무관하게 항상 열람 가능 - PollService.canAccess()가 이미
    // viewer.isAdmin()이면 무조건 통과시키므로(관리자 예외), 투표 집계는 PollService.getResult()를
    // 그대로 재사용한다. 익명 설문의 투표자 리댁션도 학생 화면과 동일하게 적용된다 - 관리자라고
    // 예외를 두지 않는다(CLAUDE.md "익명성 보호가 여러 곳에 걸쳐 일관되게 적용됨" 원칙).
    public PollAdminDetailDto getDetail(Long id, String adminUsername) {
        Poll poll = pollRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("설문을 찾을 수 없습니다."));
        PollResultDto result = pollService.getResult(id, adminUsername);

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
                .build();
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
