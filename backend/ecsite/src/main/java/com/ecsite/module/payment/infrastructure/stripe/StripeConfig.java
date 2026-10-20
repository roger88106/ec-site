package com.ecsite.module.payment.infrastructure.stripe;

import com.ecsite.module.payment.application.port.PaymentGateway;
import com.ecsite.module.payment.application.port.PaymentNotificationReceiver;
import com.stripe.StripeClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Stripe の決済サービスの設定。決済の mock フラグ（{@code ecsite.payment.mock.enabled}）を立てないときに有効にする。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnBooleanProperty(name = "ecsite.payment.mock.enabled", havingValue = false, matchIfMissing = true)
@EnableConfigurationProperties(StripeProperties.class)
class StripeConfig {

    /**
     * 通信の失敗を自動でやり直す回数。やり直しには同じ冪等の鍵が付き、Stripe で二重に作られない
     */
    private static final int MAX_NETWORK_RETRIES = 2;

    /**
     * @param properties Stripe の設定
     * @return Stripe の API
     */
    @Bean
    StripeClient stripeClient(StripeProperties properties) {
        return clientBuilder(properties).build();
    }

    /**
     * Stripe の API のビルダー（テストが、設定の渡り方を確かめるため）。
     *
     * @param properties Stripe の設定
     * @return Stripe の API のビルダー
     */
    static StripeClient.StripeClientBuilder clientBuilder(StripeProperties properties) {
        // SDK の既定（応答 80 秒）だと、支払画面を開く要求が、やり直しを含めて数分待たせうる
        return StripeClient.builder()
                .setApiKey(properties.secretKey())
                .setMaxNetworkRetries(MAX_NETWORK_RETRIES)
                .setConnectTimeout(Math.toIntExact(properties.connectTimeout().toMillis()))
                .setReadTimeout(Math.toIntExact(properties.readTimeout().toMillis()));
    }

    /**
     * @param client Stripe の API
     * @param clock  時計
     * @return Stripe の決済サービス
     */
    @Bean
    PaymentGateway stripePaymentGateway(StripeClient client, Clock clock) {
        return new StripePaymentGateway(client, clock);
    }

    /**
     * @param receiver   通知の受け口
     * @param properties Stripe の設定
     * @param clock      時計
     * @return Stripe の通知の受け取り
     */
    @Bean
    StripeNotificationHandler stripeNotificationHandler(PaymentNotificationReceiver receiver, StripeProperties properties,
                                                        Clock clock) {
        return new StripeNotificationHandler(receiver, properties.webhookSecret(), clock);
    }
}
