package com.ecsite.module.media.infrastructure.converter;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * 画像の変換の設定
 *
 * <p>画像の変換（imgproxy）の設定を有効にし、imgproxy を呼ぶ HTTP クライアントを作る。
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ImgproxyProperties.class)
class ImgproxyConfig {

    /**
     * imgproxy を呼ぶ HTTP クライアントの Bean の名前
     */
    static final String REST_CLIENT = "imgproxyRestClient";

    /**
     * imgproxy への接続を待つ時間の上限（変換を待つ時間とは別）
     */
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);

    /**
     * imgproxy を呼ぶ HTTP クライアント。
     *
     * @param restClientBuilder HTTP クライアントのビルダー（トレースとメトリクスが付く）
     * @param properties        画像の変換の設定
     * @return 基準 URL と時間の上限を設定した HTTP クライアント
     */
    @Bean(REST_CLIENT)
    RestClient imgproxyRestClient(RestClient.Builder restClientBuilder, ImgproxyProperties properties) {
        return restClient(restClientBuilder, properties);
    }

    /**
     * imgproxy を呼ぶ HTTP クライアントを、基準 URL と時間の上限を設定して作る。
     *
     * @param restClientBuilder HTTP クライアントのビルダー
     * @param properties        画像の変換の設定
     * @return 基準 URL と時間の上限を設定した HTTP クライアント
     */
    static RestClient restClient(RestClient.Builder restClientBuilder, ImgproxyProperties properties) {
        var requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build());
        requestFactory.setReadTimeout(properties.timeout());
        return restClientBuilder.baseUrl(properties.baseUrl().toString()).requestFactory(requestFactory).build();
    }
}
