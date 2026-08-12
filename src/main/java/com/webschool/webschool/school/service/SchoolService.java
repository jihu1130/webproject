package com.webschool.webschool.school.service;

import com.webschool.webschool.school.domain.Meal;
import com.webschool.webschool.school.domain.School;
import com.webschool.webschool.school.domain.Timetable;
import com.webschool.webschool.school.dto.CalendarEventDto;
import com.webschool.webschool.school.dto.SchoolCalendarDto;
import com.webschool.webschool.school.dto.TimetableDto;
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
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SchoolService {

    private final NeisApiService neisApiService;
    private final SchoolRepository schoolRepository;
    private final TimetableRepository timetableRepository;
    private final MealRepository mealRepository;

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

        List<TimetableDto> timetableDtos = new ArrayList<>();

        if (!cachedTimetables.isEmpty()) {
            // DB에 캐시된 데이터 반환
            timetableDtos = cachedTimetables.stream()
                    .map(t -> TimetableDto.builder()
                            .perio(t.getPeriod() + "교시")
                            .subject(t.getSubject())
                            .build())
                    .collect(Collectors.toList());
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
        String mealMenu = "";

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