package com.webschool.webschool.school.service;

import com.webschool.webschool.school.domain.PersonalEvent;
import com.webschool.webschool.school.dto.PersonalEventDto;
import com.webschool.webschool.school.repository.PersonalEventRepository;
import com.webschool.webschool.user.domain.User;
import com.webschool.webschool.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

// 캘린더에 본인만 볼 수 있게 추가하는 개인 일정 - ScheduleCommentService와 동일한 소유권 검증
// 패턴(IllegalArgumentException, SchoolController의 공용 @ExceptionHandler가 그대로 처리).
@Service
@RequiredArgsConstructor
public class PersonalEventService {

    private static final int MAX_TITLE_LENGTH = 100;
    private static final int MAX_MEMO_LENGTH = 1000;
    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ISO_LOCAL_DATE;

    private final PersonalEventRepository personalEventRepository;
    private final UserRepository userRepository;

    public List<PersonalEventDto> getEventsForDate(String username, LocalDate date) {
        User user = getUser(username);
        return personalEventRepository.findByUser_IdAndEventDateOrderByCreatedAtAsc(user.getId(), date)
                .stream().map(this::toDto).collect(Collectors.toList());
    }

    // 월 그리드에 점으로 표시할 날짜 목록 - SchoolService.getMonthlyEvents()와 동일한 "보이는
    // 6주(42칸) 범위 전체"를 계산해서 한 번에 조회한다(N+1 방지).
    public List<String> getEventDatesInRange(String username, int year, int month) {
        User user = getUser(username);
        LocalDate firstOfMonth = LocalDate.of(year, month, 1);
        int daysBeforeSunday = firstOfMonth.getDayOfWeek().getValue() % 7;
        LocalDate rangeStart = firstOfMonth.minusDays(daysBeforeSunday);
        LocalDate rangeEnd = rangeStart.plusDays(41);

        return personalEventRepository.findDistinctDatesByUserIdAndEventDateBetween(user.getId(), rangeStart, rangeEnd)
                .stream().map(d -> d.format(ISO_DATE)).collect(Collectors.toList());
    }

    @Transactional
    public PersonalEventDto createEvent(String username, LocalDate date, String title, String memo) {
        User user = getUser(username);
        String trimmedTitle = validateTitle(title);
        String trimmedMemo = validateMemo(memo);

        PersonalEvent event = new PersonalEvent();
        event.setUser(user);
        event.setEventDate(date);
        event.setTitle(trimmedTitle);
        event.setMemo(trimmedMemo);
        personalEventRepository.save(event);
        return toDto(event);
    }

    @Transactional
    public PersonalEventDto updateEvent(Long id, String username, String title, String memo) {
        User user = getUser(username);
        PersonalEvent event = personalEventRepository.findByIdAndUser_Id(id, user.getId())
                .orElseThrow(() -> new IllegalArgumentException("본인이 등록한 일정만 수정할 수 있습니다."));

        event.setTitle(validateTitle(title));
        event.setMemo(validateMemo(memo));
        event.setUpdatedAt(LocalDateTime.now());
        return toDto(event);
    }

    @Transactional
    public void deleteEvent(Long id, String username) {
        User user = getUser(username);
        PersonalEvent event = personalEventRepository.findByIdAndUser_Id(id, user.getId())
                .orElseThrow(() -> new IllegalArgumentException("본인이 등록한 일정만 삭제할 수 있습니다."));
        personalEventRepository.delete(event);
    }

    private User getUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("사용자 정보를 찾을 수 없습니다."));
    }

    private String validateTitle(String title) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("일정 제목을 입력해주세요.");
        }
        String trimmed = title.trim();
        if (trimmed.length() > MAX_TITLE_LENGTH) {
            throw new IllegalArgumentException("제목은 " + MAX_TITLE_LENGTH + "자 이내로 입력해주세요.");
        }
        return trimmed;
    }

    private String validateMemo(String memo) {
        if (memo == null || memo.isBlank()) {
            return null;
        }
        String trimmed = memo.trim();
        if (trimmed.length() > MAX_MEMO_LENGTH) {
            throw new IllegalArgumentException("메모는 " + MAX_MEMO_LENGTH + "자 이내로 입력해주세요.");
        }
        return trimmed;
    }

    private PersonalEventDto toDto(PersonalEvent event) {
        return PersonalEventDto.builder()
                .id(event.getId())
                .date(event.getEventDate().format(ISO_DATE))
                .title(event.getTitle())
                .memo(event.getMemo())
                .edited(event.getUpdatedAt() != null)
                .build();
    }
}
