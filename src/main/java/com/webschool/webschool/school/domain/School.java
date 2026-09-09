package com.webschool.webschool.school.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "schools")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class School {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String sdSchulCode; // 표준학교코드

    private String atptOfcdcScCode; // 시도교육청코드

    private String schoolName;      // schulNm -> schoolName으로 변경!

    private String lctnScNm;        // 소재지
    private String fondYmd;         // 설립일자

    // 날씨 위젯용 도로명주소(NEIS ORG_RDNMA) - 학교당 1회만 조회해 영구 캐싱한다(SchoolService.
    // resolveSchoolAddress() 참고, 주소는 사실상 안 바뀌므로 TTL 불필요, Timetable/Meal 캐시와
    // 다른 점).
    private String address;
}