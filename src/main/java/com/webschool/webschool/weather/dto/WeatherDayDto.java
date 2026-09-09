package com.webschool.webschool.weather.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class WeatherDayDto {
    private String date;       // yyyy-MM-dd
    private boolean hasData;   // false면 나머지 필드는 의미 없음(위젯이 "정보 없음"으로 표시)
    private Integer pop;
    private String ptyLabel;   // "없음"/"비"/"비/눈"/"눈"/"소나기"
    private String skyLabel;   // "맑음"/"구름많음"/"흐림"
    private Integer tmx;
    private Integer tmn;
}
