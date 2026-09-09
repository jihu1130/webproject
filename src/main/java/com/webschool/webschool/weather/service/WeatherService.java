package com.webschool.webschool.weather.service;

import com.webschool.webschool.school.service.SchoolService;
import com.webschool.webschool.weather.domain.WeatherForecastCache;
import com.webschool.webschool.weather.dto.WeatherDayDto;
import com.webschool.webschool.weather.dto.WeatherWeekDto;
import com.webschool.webschool.weather.repository.WeatherForecastCacheRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// 이번 주(일~토) 날씨 위젯 데이터 조립. 사용자 확정 규칙(2026-09-09) - 한 번 캐시된 날짜는
// 절대 재조회하지 않고 그대로 고정 반환한다("과거는 과거날씨 토대로... 그냥 DB에 적어서 고정").
// 그래서 이 서비스를 호출할 때마다 매번 API를 부르는 게 아니라, "이번 주 안에서 아직 캐시에 없는
// 날짜가 있을 때만" 딱 한 번 기상청을 호출해 응답에 담긴 날짜만큼 채운다. 기상청 단기예보의 실제
// 예보 범위(D+2~D+3 정도)를 벗어나 아직 캐시할 수 없는 미래 날짜, 그리고 이 기능이 생기기 전이라
// 애초에 캐시가 없는 과거 날짜는 둘 다 hasData=false로 내려가 위젯에서 "정보 없음"으로 표시된다.
@Service
@RequiredArgsConstructor
public class WeatherService {

    private final SchoolService schoolService;
    private final WeatherGridResolver gridResolver;
    private final WeatherApiService weatherApiService;
    private final WeatherForecastCacheRepository forecastCacheRepository;

    // 학교 기준 이번 주 날씨 위젯 데이터. 주소를 못 구하거나(NEIS 조회 실패) 격자 매칭이
    // 안 되면(kma-grid.csv 없음/매칭 실패) null - 컨트롤러가 404로 응답해 위젯 자체를 숨기게 한다.
    public WeatherWeekDto getWeekWidgetForSchool(String atptCode, String schoolCode) {
        String address = schoolService.resolveSchoolAddress(atptCode, schoolCode);
        if (address == null || address.isBlank()) {
            return null;
        }
        int[] grid = gridResolver.resolve(address);
        if (grid == null) {
            return null;
        }
        return getWeekForecast(grid[0], grid[1]);
    }

    @Transactional
    public WeatherWeekDto getWeekForecast(int nx, int ny) {
        LocalDate today = LocalDate.now();
        LocalDate weekStart = today.minusDays(today.getDayOfWeek().getValue() % 7); // 일요일
        LocalDate weekEnd = weekStart.plusDays(6); // 토요일

        List<WeatherForecastCache> cached = forecastCacheRepository
                .findByNxAndNyAndForecastDateBetweenOrderByForecastDateAsc(nx, ny, weekStart, weekEnd);
        Map<LocalDate, WeatherForecastCache> cacheByDate = new HashMap<>();
        for (WeatherForecastCache c : cached) {
            cacheByDate.put(c.getForecastDate(), c);
        }

        boolean needsFetch = false;
        for (LocalDate d = weekStart; !d.isAfter(weekEnd); d = d.plusDays(1)) {
            // 과거 날짜는 이미 캐시가 없으면 애초에 채울 방법이 없으므로(단기예보는 미래만
            // 예보) 재조회 대상에서 제외 - 오늘/미래 날짜만 새로 채운다.
            if (!cacheByDate.containsKey(d) && !d.isBefore(today)) {
                needsFetch = true;
                break;
            }
        }

        if (needsFetch) {
            Map<LocalDate, WeatherApiService.RawDayForecast> fetched = weatherApiService.fetchForecast(nx, ny);
            for (Map.Entry<LocalDate, WeatherApiService.RawDayForecast> entry : fetched.entrySet()) {
                LocalDate d = entry.getKey();
                if (d.isBefore(weekStart) || d.isAfter(weekEnd) || cacheByDate.containsKey(d)) {
                    continue; // 이번 주 범위 밖이거나 이미 고정된 값이 있으면 덮어쓰지 않음
                }
                WeatherApiService.RawDayForecast raw = entry.getValue();
                WeatherForecastCache toSave = WeatherForecastCache.builder()
                        .nx(nx).ny(ny).forecastDate(d)
                        .pop(raw.pop).pty(raw.pty).skyCode(raw.skyCode).tmx(raw.tmx).tmn(raw.tmn)
                        .baseDate(raw.baseDate).baseTime(raw.baseTime)
                        .build();
                try {
                    cacheByDate.put(d, forecastCacheRepository.save(toSave));
                } catch (DataIntegrityViolationException raceLoss) {
                    // 동시 요청이 먼저 저장했으면(유니크 제약 위반) 그 값을 그대로 읽어와 쓴다 -
                    // 여기서도 절대 덮어쓰지 않는다("고정" 규칙 유지).
                    forecastCacheRepository.findByNxAndNyAndForecastDate(nx, ny, d)
                            .ifPresent(existing -> cacheByDate.put(d, existing));
                }
            }
        }

        List<WeatherDayDto> days = new ArrayList<>();
        for (LocalDate d = weekStart; !d.isAfter(weekEnd); d = d.plusDays(1)) {
            WeatherForecastCache c = cacheByDate.get(d);
            days.add(c == null ? emptyDay(d) : toDto(c));
        }
        return WeatherWeekDto.builder().days(days).build();
    }

    private WeatherDayDto emptyDay(LocalDate date) {
        return WeatherDayDto.builder().date(date.toString()).hasData(false).build();
    }

    private WeatherDayDto toDto(WeatherForecastCache c) {
        return WeatherDayDto.builder()
                .date(c.getForecastDate().toString())
                .hasData(true)
                .pop(c.getPop())
                .ptyLabel(ptyLabel(c.getPty()))
                .skyLabel(skyLabel(c.getSkyCode()))
                .tmx(c.getTmx())
                .tmn(c.getTmn())
                .build();
    }

    private String ptyLabel(String pty) {
        if (pty == null) return null;
        return switch (pty) {
            case "0" -> "없음";
            case "1" -> "비";
            case "2" -> "비/눈";
            case "3" -> "눈";
            case "4" -> "소나기";
            default -> "정보 없음";
        };
    }

    private String skyLabel(String sky) {
        if (sky == null) return null;
        return switch (sky) {
            case "1" -> "맑음";
            case "3" -> "구름많음";
            case "4" -> "흐림";
            default -> "정보 없음";
        };
    }
}
