package com.webschool.webschool.weather.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

// (nx, ny, forecastDate) 한 행 = 그 날짜의 날씨 "기록". 한 번 저장되면 절대 덮어쓰지 않는다(사용자
// 확정, 2026-09-09) - WeatherService.getWeekForecast()가 이미 있는 행은 재조회하지 않고 그대로
// 반환하므로, forecastDate가 처음 캐시된 시점에 예보됐던 값이 그 날짜가 지나도 고정된 채 남는다.
// 그래서 fetchedAt 이후 이 행의 다른 필드를 갱신하는 코드는 절대 추가하지 말 것.
@Entity
@Table(name = "weather_forecast_cache",
        uniqueConstraints = @UniqueConstraint(columnNames = {"nx", "ny", "forecast_date"}))
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WeatherForecastCache {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Integer nx;

    @Column(nullable = false)
    private Integer ny;

    @Column(name = "forecast_date", nullable = false)
    private LocalDate forecastDate;

    private Integer pop;   // 강수확률(%)
    private String pty;    // 강수형태 코드(0=없음,1=비,2=비/눈,3=눈,4=소나기)
    private String skyCode; // 하늘상태 코드(1=맑음,3=구름많음,4=흐림)
    private Integer tmx;   // 최고기온
    private Integer tmn;   // 최저기온

    private String baseDate; // 이 값을 산출한 기상청 발표일자(yyyyMMdd)
    private String baseTime; // 발표시각(HHmm)

    @Column(nullable = false)
    private LocalDateTime fetchedAt;

    @PrePersist
    public void prePersist() {
        this.fetchedAt = LocalDateTime.now();
    }
}
