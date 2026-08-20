package com.webschool.webschool.school.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SchoolSearchResultDto {
    private String schoolName;   // 학교명 (SCHUL_NM)
    private String schoolCode;   // 표준학교코드 (SD_SCHUL_CODE)
    private String officeCode;   // 시도교육청코드 (ATPT_OFCDC_SC_CODE)
    private String schoolKind;   // 학교종류명 (SCHUL_KND_SC_NM)
    private String address;      // 도로명주소 (ORG_RDNMA)
}
