package com.ecsite.module.shipping.infrastructure.mock;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * mock の物流サービスの設定
 *
 * <p>mock の物流サービスが通知のエンドポイントを呼ぶ HTTP クライアントを作る。物流の mock のフラグを立てたときだけ有効にする。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnBooleanProperty(name = "ecsite.shipping.mock.enabled")
class MockCarrierConfig {

    /**
     * 通知のエンドポイントを呼ぶ HTTP クライアントの Bean の名前
     */
    static final String REST_CLIENT = "mockCarrierRestClient";

    /**
     * エンドポイントの応答を待つ時間の上限
     */
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    /**
     * 通知のエンドポイントを呼ぶ HTTP クライアントを作る。
     *
     * @param restClientBuilder HTTP クライアントのビルダー（トレースとメトリクスが付く）
     * @return 時間の上限を設定した HTTP クライアント
     */
    @Bean(REST_CLIENT)
    RestClient mockCarrierRestClient(RestClient.Builder restClientBuilder) {
        return restClient(restClientBuilder);
    }

    /**
     * 通知のエンドポイントを呼ぶ HTTP クライアントを、時間の上限を設定して作る。
     *
     * @param restClientBuilder HTTP クライアントのビルダー
     * @return 時間の上限を設定した HTTP クライアント
     */
    static RestClient restClient(RestClient.Builder restClientBuilder) {
        var requestFactory = new JdkClientHttpRequestFactory();
        requestFactory.setReadTimeout(TIMEOUT);
        return restClientBuilder.requestFactory(requestFactory).build();
    }
}
