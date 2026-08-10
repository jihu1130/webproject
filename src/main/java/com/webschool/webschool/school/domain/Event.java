package com.webschool.webschool.school.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "events")
@Getter @Setter
@NoArgsConstructor
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String schoolCode; // 학교 행정표준코드

    @Column(nullable = false)
    private String eventDate;  // 행사일자 (YYYYMMDD)

    private String eventName;  // 행사명 (예: 재량휴업일, 기말고사, 여름방학 등)

    public Event(String schoolCode, String eventDate, String eventName) {
        this.schoolCode = schoolCode;
        this.eventDate = eventDate;
        this.eventName = eventName;
    }
}