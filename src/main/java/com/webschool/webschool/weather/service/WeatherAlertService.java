package com.webschool.webschool.weather.service;

import com.webschool.webschool.notification.domain.Notification;
import com.webschool.webschool.notification.service.NotificationService;
import com.webschool.webschool.user.domain.User;
import com.webschool.webschool.user.repository.UserRepository;
import com.webschool.webschool.weather.dto.WeatherDayDto;
import com.webschool.webschool.weather.dto.WeatherWeekDto;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

// 관심학교(=내 학교) 오늘 날씨가 임계치를 넘으면 등교 전 알림. AccountHardDeleteService와 동일한
// "대상자별 try/catch, 한 명 실패가 나머지를 막지 않음" 배치 패턴 - 바깥 메서드는 @Transactional이
// 아니고, 실제 저장이 필요한 부분만 notify()(자체적으로 @Transactional) 호출로 처리한다.
@Service
@RequiredArgsConstructor
public class WeatherAlertService {

    private static final Logger log = LoggerFactory.getLogger(WeatherAlertService.class);

    private static final int RAIN_SNOW_POP_THRESHOLD = 60; // 강수확률 60% 이상
    private static final int HOT_THRESHOLD_CELSIUS = 33;   // 최고 33도 이상
    private static final int COLD_THRESHOLD_CELSIUS = -12; // 최저 -12도 이하

    private final UserRepository userRepository;
    private final WeatherService weatherService;
    private final NotificationService notificationService;

    // 등교 전(05시 발표분이 반영되는 시각 이후) - PostContestService.sendDeadlineReminder()와
    // 동일하게 옵트인한 사용자(탈퇴 제외)만 스트림 필터로 골라낸다.
    @Scheduled(cron = "0 30 6 * * *")
    public void sendDailyWeatherAlerts() {
        List<User> targets = userRepository.findAllByOrderByIdAsc().stream()
                .filter(u -> !u.isDeleted() && u.isWeatherAlertEnabled())
                .filter(u -> u.getAtptCode() != null && !u.getAtptCode().isBlank()
                        && u.getSchoolCode() != null && !u.getSchoolCode().isBlank())
                .collect(Collectors.toList());
        if (targets.isEmpty()) {
            return;
        }

        int sent = 0;
        for (User user : targets) {
            try {
                if (sendIfNeeded(user)) {
                    sent++;
                }
            } catch (Exception e) {
                log.error("날씨 알림 발송 실패 - userId={}", user.getId(), e);
            }
        }
        log.info("날씨 알림 배치 완료 - 대상 {}명 중 {}명 발송", targets.size(), sent);
    }

    private boolean sendIfNeeded(User user) {
        WeatherWeekDto week = weatherService.getWeekWidgetForSchool(user.getAtptCode(), user.getSchoolCode());
        if (week == null) {
            return false; // 주소/격자 매칭 실패 - 이 학교는 조용히 건너뜀
        }

        String today = LocalDate.now().toString();
        Optional<WeatherDayDto> todayForecast = week.getDays().stream()
                .filter(d -> today.equals(d.getDate()) && d.isHasData())
                .findFirst();
        if (todayForecast.isEmpty()) {
            return false;
        }

        String message = buildAlertMessage(todayForecast.get());
        if (message == null) {
            return false; // 임계치 미달 - 알릴 내용 없음
        }

        notify(user, message);
        return true;
    }

    @Transactional
    void notify(User user, String message) {
        notificationService.notify(user, Notification.Type.WEATHER_ALERT, message, "/school/calendar");
    }

    private String buildAlertMessage(WeatherDayDto day) {
        boolean rainOrSnow = day.getPop() != null && day.getPop() >= RAIN_SNOW_POP_THRESHOLD
                || (day.getPtyLabel() != null && !"없음".equals(day.getPtyLabel()) && !"정보 없음".equals(day.getPtyLabel()));
        boolean hot = day.getTmx() != null && day.getTmx() >= HOT_THRESHOLD_CELSIUS;
        boolean cold = day.getTmn() != null && day.getTmn() <= COLD_THRESHOLD_CELSIUS;

        if (!rainOrSnow && !hot && !cold) {
            return null;
        }

        StringBuilder sb = new StringBuilder("오늘 날씨 알림 - ");
        if (rainOrSnow) {
            String kind = (day.getPtyLabel() != null && !"없음".equals(day.getPtyLabel())) ? day.getPtyLabel() : "비";
            sb.append(kind).append(" 소식이 있어요(강수확률 ")
                    .append(day.getPop() == null ? "-" : day.getPop()).append("%). ");
        }
        if (hot) {
            sb.append("최고기온 ").append(day.getTmx()).append("도로 많이 더워요. ");
        }
        if (cold) {
            sb.append("최저기온 ").append(day.getTmn()).append("도로 많이 추워요. ");
        }
        sb.append("등교 준비 시 참고하세요!");
        return sb.toString();
    }
}
