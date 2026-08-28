package com.webschool.webschool.global.upload;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

// FileStorageService 구현 중 어느 쪽을 쓸지 app.upload.s3.bucket 설정 유무로 고른다.
// 두 @Bean을 반드시 이 순서(S3 먼저)로 같은 @Configuration 클래스 안에 둬야
// @ConditionalOnMissingBean이 신뢰성 있게 동작한다(서로 다른 파일에 흩어진 @Service들에
// @ConditionalOnMissingBean을 걸면 컴포넌트 스캔 순서에 따라 결과가 달라질 수 있음).
@Configuration
public class FileStorageConfig {

    @Bean
    @ConditionalOnProperty(prefix = "app.upload.s3", name = "bucket")
    public S3Client s3Client(@Value("${app.upload.s3.region:ap-northeast-2}") String region) {
        return S3Client.builder().region(Region.of(region)).build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "app.upload.s3", name = "bucket")
    public FileStorageService s3FileStorageService(
            S3Client s3Client,
            @Value("${app.upload.s3.bucket}") String bucket,
            @Value("${app.upload.s3.region:ap-northeast-2}") String region,
            @Value("${app.upload.s3.base-url:}") String baseUrl) {
        return new S3FileStorageService(s3Client, bucket, region, baseUrl);
    }

    @Bean
    @ConditionalOnMissingBean(FileStorageService.class)
    public FileStorageService localFileStorageService(@Value("${app.upload.dir}") String uploadDir) {
        return new LocalFileStorageService(uploadDir);
    }
}
