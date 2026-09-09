package com.webschool.webschool.weather.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class WeatherWeekDto {
    private List<WeatherDayDto> days; // 이번 주 일~토 7개, 데이터 없는 날은 hasData=false
}
