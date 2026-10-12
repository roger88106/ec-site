package com.ecsite.module.media.infrastructure.storage;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * ファイルの置き場所（S3 API）のクライアントの設定。
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(MediaStorageProperties.class)
class MediaStorageConfig {

    /**
     * S3 のクライアント。<br />
     * チェックサムは、必須の操作でだけ計算・検証する。S3 互換のストレージ（エミュレーター、GCS）が、
     * SDK の既定で付くチェックサムのヘッダーに対応していないことがあるため。
     *
     * @param properties ファイルの置き場所の設定
     * @return S3 のクライアント
     */
    @Bean(destroyMethod = "close")
    S3Client mediaS3Client(MediaStorageProperties properties) {
        var builder = S3Client.builder()
                .region(Region.of(properties.region()))
                .credentialsProvider(credentials(properties))
                .forcePathStyle(properties.pathStyleAccess())
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED);
        if (properties.endpoint() != null) {
            builder.endpointOverride(properties.endpoint());
        }
        return builder.build();
    }

    /**
     * 署名付き URL の発行に使うクライアント
     *
     * @param properties ファイルの置き場所の設定
     * @return 署名付き URL の発行に使うクライアント
     */
    @Bean(destroyMethod = "close")
    S3Presigner mediaS3Presigner(MediaStorageProperties properties) {
        var builder = S3Presigner.builder()
                .region(Region.of(properties.region()))
                .credentialsProvider(credentials(properties))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(properties.pathStyleAccess()).build());
        if (properties.endpoint() != null) {
            builder.endpointOverride(properties.endpoint());
        }
        return builder.build();
    }

    /**
     * 資格情報。設定で与えていればそれを使い（ローカル）、なければ既定の連鎖（環境変数、IAM ロールなど）を使う。
     */
    private static AwsCredentialsProvider credentials(MediaStorageProperties properties) {
        if (properties.hasStaticCredentials()) {
            return StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(properties.accessKeyId(), properties.secretAccessKey()));
        }
        return DefaultCredentialsProvider.builder().build();
    }
}
