package com.ecsite.module.payment.infrastructure.stripe;

import com.ecsite.module.payment.application.PaymentProperties;
import com.ecsite.module.payment.application.port.PaymentGateway;
import com.ecsite.module.payment.application.port.PaymentNotificationReceiver;
import com.ecsite.module.payment.infrastructure.ConditionalOnPaymentStripe;
import com.stripe.StripeClient;
import com.stripe.net.HttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.Duration;

/**
 * Stripe の決済サービスの設定
 *
 * <p>決済の mock フラグ（{@code ecsite.payment.mock.enabled}）を立てないときに有効にし、Stripe の API のクライアントを作る。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnPaymentStripe
class StripeConfig {

    /**
     * 通信の失敗を自動で再試行する回数
     *
     * <p>stripe-java が POST の呼び出しごとに冪等キーを作り、その呼び出しの再試行には同じキーを付けるので、Stripe で二重に作られない。
     */
    private static final int MAX_NETWORK_RETRIES = 2;

    /**
     * 照合を始めるまでの時間を、売上確定の 1 回の呼び出しの最長の時間に加える余裕
     *
     * <p>複数のインスタンスの時計のずれと、最長の時間の見積もりに入れていない待ち（接続の確立や名前解決など）のため。
     */
    private static final Duration CAPTURE_RECONCILE_MARGIN = Duration.ofMinutes(1);

    /**
     * Stripe の API を作る。
     *
     * <p>作る前に、照合を始めるまでの時間が、売上確定の呼び出しの最長の時間より長いことを検証する。
     *
     * @param properties        Stripe の設定
     * @param paymentProperties 決済の設定
     * @return Stripe の API
     */
    @Bean
    StripeClient stripeClient(StripeProperties properties, PaymentProperties paymentProperties) {
        validateCaptureReconcileDelay(paymentProperties, properties);
        return clientBuilder(properties).build();
    }

    /**
     * 照合を始めるまでの時間が、売上確定の 1 回の呼び出しの最長の時間と余裕の合計より長いことを検証する。
     *
     * <p>最長の時間は、(接続の上限 + 応答の上限) × (自動の再試行の回数 + 1) に、再試行の間の待ちの最大
     * （Stripe の SDK の {@link HttpClient#maxNetworkRetriesDelay} × 再試行の回数）を加えた時間。
     * 短いと、売上確定の最中の支払を照合し、まだ確定していない状態を読んで、売上確定を求めた時刻を削除しうる。
     *
     * @param paymentProperties 決済の設定
     * @param properties        Stripe の設定
     * @throws IllegalStateException 照合を始めるまでの時間が足りないとき
     */
    static void validateCaptureReconcileDelay(PaymentProperties paymentProperties, StripeProperties properties) {
        var attempts = MAX_NETWORK_RETRIES + 1;
        var required = properties.connectTimeout().plus(properties.readTimeout()).multipliedBy(attempts)
                .plus(HttpClient.maxNetworkRetriesDelay.multipliedBy(MAX_NETWORK_RETRIES))
                .plus(CAPTURE_RECONCILE_MARGIN);
        if (paymentProperties.captureReconcileDelay().compareTo(required) <= 0) {
            throw new IllegalStateException(("ecsite.payment.capture-reconcile-delay（%s）は、売上確定の呼び出しの最長の時間と余裕の合計（%s）より長くする。"
                    + "合計は、ecsite.payment.stripe.connect-timeout（%s）と ecsite.payment.stripe.read-timeout（%s）から決まる")
                    .formatted(paymentProperties.captureReconcileDelay(), required, properties.connectTimeout(),
                            properties.readTimeout()));
        }
    }

    /**
     * Stripe の API のビルダーを、設定の値で作る。
     *
     * @param properties Stripe の設定
     * @return Stripe の API のビルダー
     */
    static StripeClient.StripeClientBuilder clientBuilder(StripeProperties properties) {
        // SDK の既定（応答 80 秒）だと、支払画面を開くリクエストが、再試行を含めて数分待たせうる
        return StripeClient.builder()
                .setApiKey(properties.secretKey())
                .setMaxNetworkRetries(MAX_NETWORK_RETRIES)
                .setConnectTimeout(Math.toIntExact(properties.connectTimeout().toMillis()))
                .setReadTimeout(Math.toIntExact(properties.readTimeout().toMillis()));
    }

    /**
     * Stripe の決済サービスを作る。
     *
     * @param client Stripe の API
     * @param clock  時計
     * @return Stripe の決済サービス
     */
    @Bean
    PaymentGateway stripePaymentGateway(StripeClient client, Clock clock) {
        return new StripePaymentGateway(client, clock);
    }

    /**
     * Stripe の通知の受け取りを作る。
     *
     * @param receiver   通知の port
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
