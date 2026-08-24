package com.webschool.webschool.bugreport.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class BugReportAttachmentDto {
    private String url;
    private String originalFilename;
    private String kind; // image | video
}
