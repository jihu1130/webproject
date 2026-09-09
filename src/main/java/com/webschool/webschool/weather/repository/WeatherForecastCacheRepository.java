package com.webschool.webschool.weather.repository;

import com.webschool.webschool.weather.domain.WeatherForecastCache;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface WeatherForecastCacheRepository extends JpaRepository<WeatherForecastCache, Long> {

    Optional<WeatherForecastCache> findByNxAndNyAndForecastDate(Integer nx, Integer ny, LocalDate forecastDate);

    List<WeatherForecastCache> findByNxAndNyAndForecastDateBetweenOrderByForecastDateAsc(
            Integer nx, Integer ny, LocalDate start, LocalDate end);
}
