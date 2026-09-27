package com.webschool.webschool.school.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SchoolServiceParseYmdTest {

    @Test
    void validDatesParse() {
        assertEquals(LocalDate.of(2026, 9, 27), SchoolService.parseYmd("20260927"));
        assertEquals(LocalDate.of(2028, 2, 29), SchoolService.parseYmd("20280229"));
    }

    @Test
    void impossibleCalendarDatesAreRejected() {
        // 기본 SMART 파싱이라면 2월 28일로 조용히 보정됐을 값 - STRICT라 거부돼야 한다.
        assertThrows(IllegalArgumentException.class, () -> SchoolService.parseYmd("20260230"));
        assertThrows(IllegalArgumentException.class, () -> SchoolService.parseYmd("20270229"));
        assertThrows(IllegalArgumentException.class, () -> SchoolService.parseYmd("20261332"));
    }

    @Test
    void malformedInputIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> SchoolService.parseYmd(null));
        assertThrows(IllegalArgumentException.class, () -> SchoolService.parseYmd(""));
        assertThrows(IllegalArgumentException.class, () -> SchoolService.parseYmd("2026-09-27"));
        assertThrows(IllegalArgumentException.class, () -> SchoolService.parseYmd("abc"));
    }
}
