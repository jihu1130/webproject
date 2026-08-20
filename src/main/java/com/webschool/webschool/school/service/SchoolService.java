package com.webschool.webschool.school.service;

import com.webschool.webschool.school.domain.Meal;
import com.webschool.webschool.school.domain.School;
import com.webschool.webschool.school.domain.Timetable;
import com.webschool.webschool.school.dto.CalendarEventDto;
import com.webschool.webschool.school.dto.SchoolCalendarDto;
import com.webschool.webschool.school.dto.TimetableDto;
import com.webschool.webschool.school.dto.VacationDdayDto;
import com.webschool.webschool.school.repository.MealRepository;
import com.webschool.webschool.school.repository.SchoolRepository;
import com.webschool.webschool.school.repository.TimetableRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SchoolService {

    private final NeisApiService neisApiService;
    private final SchoolRepository schoolRepository;
    private final TimetableRepository timetableRepository;
    private final MealRepository mealRepository;

    // "방학"이 포함된 학사일정 이름으로 방학 여부를 판단한다. 실제 NEIS 데이터로
    // 확인해보니 학교마다 등록 방식이 다른데(서울고: "방학식"/"개학" 하루짜리만 등록,
    // 아산배방중: "여름방학"처럼 기간 전체가 매일 등록 + "여름방학식"/"개학식" 하루짜리도
    // 별도 등록), 두 경우 다 이름에 "방학"이 들어가므로 keyword 하나로 함께 잡아낸다.
    private static final String VACATION_KEYWORD = "방학";
    // 방학이 끝나고 등교가 재개되는 일정("개학식"/"2학기 개학" 등)을 찾기 위한 키워드.
    // "개학"은 "방학"과 겹치지 않는 별도 문자열이라 두 키워드를 따로 검색해야 한다.
    private static final String RESUME_KEYWORD = "개학";
    private static final int VACATION_SEARCH_WINDOW_DAYS = 200;

    @Transactional
    public SchoolCalendarDto getCalendarDetails(String atptCode, String schoolCode, String dateStr, Integer grade, String classNm) {
        return getCalendarDetails(atptCode, schoolCode, dateStr, grade, classNm, null);
    }

    @Transactional
    public SchoolCalendarDto getCalendarDetails(String atptCode, String schoolCode, String dateStr, Integer grade, String classNm, String schoolKind) {
        LocalDate date = LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("yyyyMMdd"));

        // 1. 학교 엔티티 조회 (없으면 기본 생성 및 저장)
        School school = schoolRepository.findBySdSchulCode(schoolCode)
                .orElseGet(() -> schoolRepository.save(School.builder()
                        .atptOfcdcScCode(atptCode)
                        .sdSchulCode(schoolCode)
                        .schoolName("우리 학교")
                        .build()));

        // 2. DB에서 시간표 조회 (DB 캐싱)
        List<Timetable> cachedTimetables = timetableRepository
                .findBySchoolIdAndGradeAndClassNmAndClassDate(school.getId(), grade, classNm, date);

        List<TimetableDto> timetableDtos;

        if (!cachedTimetables.isEmpty()) {
            // DB에 캐시된 데이터 반환
            timetableDtos = cachedTimetables.stream()
                    .map(t -> TimetableDto.builder()
                            .perio(t.getPeriod() + "교시")
                            .subject(t.getSubject())
                            .build())
                    .collect(Collectors.toList());
        } else if (isVacationDate(atptCode, schoolCode, dateStr)) {
            // 방학 기간이면 애초에 시간표가 없으므로 나이스 API 호출 자체를 건너뛴다
            timetableDtos = new ArrayList<>();
        } else {
            // DB에 없으면 나이스 API 호출 후 DB에 저장
            timetableDtos = neisApiService.fetchTimetableFromNeis(atptCode, schoolCode, dateStr, grade, classNm, schoolKind);
            for (TimetableDto dto : timetableDtos) {
                int periodInt = Integer.parseInt(dto.getPerio().replace("교시", "").trim());
                try {
                    timetableRepository.save(Timetable.builder()
                            .school(school)
                            .grade(grade)
                            .classNm(classNm)
                            .classDate(date)
                            .period(periodInt)
                            .subject(dto.getSubject())
                            .build());
                } catch (org.springframework.dao.DataIntegrityViolationException e) {
                    // 버그 수정: 같은 반 학생 두 명이 아직 캐시 안 된 날짜를 거의 동시에 열람하면 둘 다
                    // 캐시 미스로 판단해 여기까지 왔다가 같은 교시를 중복 저장하려 들 수 있다 - Timetable에
                    // 추가한 unique 제약(school+날짜+학년+반+교시)이 두 번째 삽입을 막아주므로, 그 경우엔
                    // 이미 다른 요청이 저장한 데이터가 있다는 뜻이라 조용히 넘어간다(화면에는 어차피 이번
                    // 요청이 방금 응답받은 NEIS 데이터가 그대로 보여지므로 사용자 입장에서 문제 없음).
                }
            }
        }

        // 3. DB에서 급식 조회 (DB 캐싱)
        List<Meal> cachedMeals = mealRepository.findBySchoolIdAndMealDate(school.getId(), date);
        String mealMenu;

        if (!cachedMeals.isEmpty()) {
            mealMenu = cachedMeals.get(0).getMenu();
        } else {
            mealMenu = neisApiService.fetchMealFromNeis(atptCode, schoolCode, dateStr);
            // 버그 수정: mealMenu가 null이 아니라 "정보 없음" 문자열이었던 시절엔 이 조건이 항상
            // 참이 돼서 실패한 조회까지 그대로 캐싱해버렸다(한 번 캐싱되면 나중에 NEIS에 실제
            // 데이터가 생겨도 영원히 "정보 없음"만 보이는 버그). null일 때만 캐싱을 건너뛰므로
            // 다음 방문 때 다시 NEIS를 조회하게 된다.
            if (mealMenu != null && !mealMenu.isEmpty()) {
                mealRepository.save(Meal.builder()
                        .school(school)
                        .mealDate(date)
                        .mealType("중식")
                        .menu(mealMenu)
                        .build());
            }
        }

        // 4. 학사일정 조회
        String eventName = neisApiService.fetchEventFromNeis(atptCode, schoolCode, dateStr);

        return SchoolCalendarDto.builder()
                .date(dateStr)
                .timetable(timetableDtos)
                .meal(mealMenu != null ? mealMenu : "등록된 급식 정보가 없습니다.")
                .eventName(eventName)
                .build();
    }

    private boolean isVacationDate(String atptCode, String schoolCode, String dateStr) {
        return !neisApiService.fetchEventsInRange(atptCode, schoolCode, dateStr, dateStr, VACATION_KEYWORD).isEmpty();
    }

    // 방학 D-Day - 오늘이 이미 방학 기간이면 개학(등교 재개)까지 며칠 남았는지(D-N),
    // 아니면 다가올 방학(식)까지 며칠 남았는지(D-N)를 계산한다. 두 경우 모두 과거
    // 날짜는 보지 않고 항상 내일(today+1)부터 미래 방향으로만 검색한다. 캘린더
    // 페이지에서 학교 선택 시 배지로 보여주는 용도.
    public VacationDdayDto getVacationDday(String atptCode, String schoolCode) {
        LocalDate today = LocalDate.now();
        DateTimeFormatter ymd = DateTimeFormatter.ofPattern("yyyyMMdd");
        String todayYmd = today.format(ymd);

        List<CalendarEventDto> todayVacation = neisApiService.fetchEventsInRange(
                atptCode, schoolCode, todayYmd, todayYmd, VACATION_KEYWORD);

        // 버그 수정: "방학식"/"개학"처럼 하루짜리 이벤트만 등록하는 학교(서울고 등)는 방학식 당일만
        // 오늘 방학 이벤트가 있는 걸로 잡혀서, 이튿날부터는 실제로 방학인데도 "방학 아님"으로 처리돼
        // 엉뚱하게 다음 방학까지의 D-Day가 떴다. 오늘 태그가 없어도, 최근 방학 태그 이후 아직
        // "개학"(등교 재개) 태그가 없었다면 지금도 방학 중인 것으로 본다.
        boolean inVacationToday = !todayVacation.isEmpty();
        if (!inVacationToday) {
            LocalDate lookbackStart = today.minusDays(VACATION_SEARCH_WINDOW_DAYS);
            List<CalendarEventDto> recentVacationTags = neisApiService.fetchEventsInRange(
                    atptCode, schoolCode, lookbackStart.format(ymd), today.minusDays(1).format(ymd), VACATION_KEYWORD);
            LocalDate lastTag = recentVacationTags.stream()
                    .map(e -> LocalDate.parse(e.getDate()))
                    .max(Comparator.naturalOrder())
                    .orElse(null);
            if (lastTag != null) {
                List<CalendarEventDto> resumedSince = neisApiService.fetchEventsInRange(
                        atptCode, schoolCode, lastTag.plusDays(1).format(ymd), todayYmd, RESUME_KEYWORD);
                inVacationToday = resumedSince.isEmpty();
            }
        }

        if (inVacationToday) {
            CalendarEventDto resume = findVacationEnd(atptCode, schoolCode, today, ymd, !todayVacation.isEmpty());
            if (resume == null) {
                return null;
            }
            LocalDate resumeDate = LocalDate.parse(resume.getDate());
            return VacationDdayDto.builder()
                    .inVacation(true)
                    .label(resume.getEventName())
                    .targetDate(resume.getDate())
                    .dday((int) ChronoUnit.DAYS.between(today, resumeDate))
                    .build();
        }

        LocalDate rangeEnd = today.plusDays(VACATION_SEARCH_WINDOW_DAYS);
        List<CalendarEventDto> upcoming = neisApiService.fetchEventsInRange(
                atptCode, schoolCode, today.plusDays(1).format(ymd), rangeEnd.format(ymd), VACATION_KEYWORD);

        return upcoming.stream()
                .min(Comparator.comparing(CalendarEventDto::getDate))
                .map(nearest -> VacationDdayDto.builder()
                        .inVacation(false)
                        .label(nearest.getEventName())
                        .targetDate(nearest.getDate())
                        .dday((int) ChronoUnit.DAYS.between(today, LocalDate.parse(nearest.getDate())))
                        .build())
                .orElse(null);
    }

    // 지금 방학 중일 때 등교가 재개되는 날을 찾는다. 내일부터 미래 방향으로만 검색한다
    // (과거 날짜는 절대 보지 않음). 1순위로 "개학" 키워드가 붙은 명시적 일정(예: "2학기
    // 개학식")을 찾고, 그런 일정이 없는 학교라면 "방학" 키워드가 붙은 마지막 날짜의
    // 다음날을 등교 재개일로 간주한다(예: 매일 "여름방학"으로 등록되다가 특정 날짜부터
    // 더 이상 등록되지 않으면 그 다음날이 개학일).
    // todayTaggedVacation: 오늘 날짜 자체에 "방학" 태그가 직접 있었는지(getVacationDday()가 이미
    // 확인한 값을 그대로 넘겨받음) - 아래 2순위 추론이 유효한 전제인지 판단하는 데 필요하다.
    private CalendarEventDto findVacationEnd(String atptCode, String schoolCode, LocalDate today,
                                              DateTimeFormatter ymd, boolean todayTaggedVacation) {
        LocalDate searchStart = today.plusDays(1);
        LocalDate searchEnd = today.plusDays(VACATION_SEARCH_WINDOW_DAYS);

        List<CalendarEventDto> resumeEvents = neisApiService.fetchEventsInRange(
                atptCode, schoolCode, searchStart.format(ymd), searchEnd.format(ymd), RESUME_KEYWORD);
        CalendarEventDto nearestResume = resumeEvents.stream()
                .min(Comparator.comparing(CalendarEventDto::getDate))
                .orElse(null);
        if (nearestResume != null) {
            return nearestResume;
        }

        // "방학" 키워드 하나로 200일치를 검색하면 지금 진행 중인 방학뿐 아니라 그 뒤에
        // 오는 다른 방학(예: 겨울방학)까지 같이 걸릴 수 있어서, 전체 중 가장 늦은 날짜를
        // 그냥 집으면 엉뚱하게 먼 미래의 다른 방학 끝을 등교 재개일로 오인하게 된다.
        // 그래서 내일부터 하루씩 연속으로 매칭되는 날짜만 따라가다가, 처음으로 매칭이
        // 끊기는 날을 지금 방학의 종료(등교 재개)일로 판단한다.
        List<CalendarEventDto> vacationDays = neisApiService.fetchEventsInRange(
                atptCode, schoolCode, searchStart.format(ymd), searchEnd.format(ymd), VACATION_KEYWORD);
        Set<LocalDate> vacationDates = vacationDays.stream()
                .map(e -> LocalDate.parse(e.getDate()))
                .collect(Collectors.toSet());

        LocalDate probe = searchStart;
        while (vacationDates.contains(probe) && !probe.isAfter(searchEnd)) {
            probe = probe.plusDays(1);
        }
        // 버그 수정: probe가 searchStart(내일)에서 전혀 안 움직인 것 자체를 예전엔 무조건 "종료일을
        // 못 찾음"으로 보고 null을 반환했다 - 그런데 오늘 자체가 (매일 태그되는 방학의) 마지막 날로서
        // 이미 방학 태그가 확인된 상태(todayTaggedVacation=true)라면, 내일부터 태그가 끊기는 것 자체가
        // 정확히 "내일이 개학일"이라는 신호다. 방학 마지막 날 당일에 조회하면 매번 이 케이스에 걸려서
        // 계속 "정보 없음"만 떴던 게 이 버그였다. 반대로 오늘 자체엔 태그가 없었고(과거의 단발성
        // 방학식 태그만으로 getVacationDday()가 "방학 중"이라 판단한 경우) 내일부터도 아무 근거가
        // 없다면, 이 추론 방식 자체가 적용 안 되는 학교이므로 정직하게 null을 반환한다.
        if (probe.equals(searchStart) && !todayTaggedVacation) {
            return null;
        }
        return CalendarEventDto.builder()
                .date(probe.format(DateTimeFormatter.ISO_LOCAL_DATE))
                .eventName("개학")
                .build();
    }

    // 캘린더 월 그리드(6주=42칸)에 표시할 학사일정 조회 - keyword가 없으면(기본값)
    // 그 기간의 학사일정을 전부 반환한다. 그리드가 이전/다음 달 날짜로 앞뒤 빈 칸을
    // 채우므로(프론트 buildCalendarDays()와 동일한 계산), 실제로 화면에 보이는 42칸
    // 범위 전체를 조회해야 "OO주간"처럼 여러 날에 걸친 일정이 달 경계에 걸쳐 있어도
    // 빠짐없이 배지로 표시된다.
    public List<CalendarEventDto> getMonthlyEvents(String atptCode, String schoolCode,
                                                     int year, int month, String keyword) {
        LocalDate firstOfMonth = LocalDate.of(year, month, 1);
        // DayOfWeek.SUNDAY=7 -> 7%7=0(일요일이면 그대로), MONDAY=1 -> 1일 전 등
        int daysBeforeSunday = firstOfMonth.getDayOfWeek().getValue() % 7;
        LocalDate rangeStart = firstOfMonth.minusDays(daysBeforeSunday);
        LocalDate rangeEnd = rangeStart.plusDays(41); // 6주 x 7일 - 1

        DateTimeFormatter ymd = DateTimeFormatter.ofPattern("yyyyMMdd");
        return neisApiService.fetchEventsInRange(
                atptCode, schoolCode, rangeStart.format(ymd), rangeEnd.format(ymd), keyword);
    }

    // 학사일정은 매년 비슷한 이름으로 반복되기 때문에("기말고사"가 작년에도,
    // 올해도, 내년에도 있음) 단순 이름 검색은 어느 해의 일정인지 헷갈릴 수 있다.
    // 그래서 검색 결과를 전부 보여주는 대신, 오늘 날짜와 가장 가까운 단 하나만
    // 찾아서 그 날짜로 캘린더를 이동시키는 용도로 쓴다. 작년/올해/내년 1년치씩을
    // 각각 조회해서 합친 뒤(연도 하나로는 "올해 이미 지나간 일정"을 검색했을 때
    // 내년 것도, "작년에만 있었던" 것도 놓칠 수 있어서), 오늘과의 날짜 차이가
    // 가장 작은 것을 고른다 - 차이가 같으면(이론상 드묾) 이미 지난 것보다 앞으로
    // 다가올 일정을 우선한다.
    private static final int SEARCH_YEAR_RADIUS = 1;

    public CalendarEventDto findNearestEvent(String atptCode, String schoolCode, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }

        LocalDate today = LocalDate.now();
        DateTimeFormatter ymd = DateTimeFormatter.ofPattern("yyyyMMdd");
        List<CalendarEventDto> matches = new ArrayList<>();

        for (int offset = -SEARCH_YEAR_RADIUS; offset <= SEARCH_YEAR_RADIUS; offset++) {
            int year = today.getYear() + offset;
            LocalDate from = LocalDate.of(year, 1, 1);
            LocalDate to = LocalDate.of(year, 12, 31);
            matches.addAll(neisApiService.fetchEventsInRange(
                    atptCode, schoolCode, from.format(ymd), to.format(ymd), keyword));
        }

        return matches.stream()
                .min(Comparator
                        .<CalendarEventDto>comparingLong(e -> Math.abs(daysFromToday(e, today)))
                        .thenComparing(e -> daysFromToday(e, today) < 0)) // 같은 거리면 미래(false) 우선
                .orElse(null);
    }

    private long daysFromToday(CalendarEventDto event, LocalDate today) {
        return ChronoUnit.DAYS.between(today, LocalDate.parse(event.getDate()));
    }
}