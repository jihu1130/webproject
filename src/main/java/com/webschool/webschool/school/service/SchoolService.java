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
            timetableDtos = neisApiService.fetchTimetableFromNeis(atptCode, schoolCode, dateStr, grade, classNm);
            for (TimetableDto dto : timetableDtos) {
                int periodInt = Integer.parseInt(dto.getPerio().replace("교시", "").trim());
                timetableRepository.save(Timetable.builder()
                        .school(school)
                        .grade(grade)
                        .classNm(classNm)
                        .classDate(date)
                        .period(periodInt)
                        .subject(dto.getSubject())
                        .build());
            }
        }

        // 3. DB에서 급식 조회 (DB 캐싱)
        List<Meal> cachedMeals = mealRepository.findBySchoolIdAndMealDate(school.getId(), date);
        String mealMenu;

        if (!cachedMeals.isEmpty()) {
            mealMenu = cachedMeals.get(0).getMenu();
        } else {
            mealMenu = neisApiService.fetchMealFromNeis(atptCode, schoolCode, dateStr);
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
                .meal(mealMenu)
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

        if (!todayVacation.isEmpty()) {
            CalendarEventDto resume = findVacationEnd(atptCode, schoolCode, today, ymd);
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
    private CalendarEventDto findVacationEnd(String atptCode, String schoolCode, LocalDate today, DateTimeFormatter ymd) {
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
        if (probe.equals(searchStart)) {
            // 내일부터 곧바로 매칭이 끊기면(=내일이 이미 등교일) 실제 종료일을 못 찾은
            // 것이므로 null을 반환해 상위에서 "정보 없음"으로 처리하게 한다.
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