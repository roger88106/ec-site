package com.ecsite.module.payment.infrastructure.stripe;

import com.ecsite.module.payment.application.PaymentProperties;
import com.ecsite.support.TestSettings;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Stripe の API の設定の単体テスト
 *
 * <p>設定は Spring を起動せずに、設定の型へ値を当てはめて確認する。既定は、Stripe で動かすときの設定ファイル
 * （{@code application.yml} に local と stripe の profile を重ねたもの）の値を含めて確認する。
 */
class StripeConfigTest {

    // ============================== 定数 ==============================

    /**
     * Stripe の設定の接頭辞
     */
    private static final String PREFIX = "ecsite.payment.stripe";

    /**
     * 決済の設定の接頭辞
     */
    private static final String PAYMENT_PREFIX = "ecsite.payment";

    /**
     * Stripe で動かすときの profile
     */
    private static final String[] STRIPE_PROFILES = {"local", "stripe"};

    // ============================== テスト ==============================

    @Test
    @DisplayName("AC-301 Stripe の API は、既定で接続 5 秒・応答 20 秒の上限で呼ぶ")
    void defaultTimeouts() {
        // 準備：Stripe で動かすときの設定ファイルの値
        var properties = TestSettings.loadAndBind(PREFIX, StripeProperties.class, STRIPE_PROFILES);

        // 操作：API の呼び出しの設定を作る
        var builder = StripeConfig.clientBuilder(properties);

        // 確認：接続 5 秒、応答 20 秒
        assertThat(builder.getConnectTimeout()).isEqualTo(5_000);
        assertThat(builder.getReadTimeout()).isEqualTo(20_000);
    }

    @Test
    @DisplayName("AC-301 接続と応答の上限は、設定で変えられる")
    void overridesTimeouts() {
        // 準備：接続 2 秒、応答 45 秒の設定（鍵は試験用の値）
        var properties = TestSettings.bind(PREFIX, StripeProperties.class, Map.of(
                "secret-key", "sk_test_unit", "webhook-secret", "whsec_unit", "connect-timeout", "2s", "read-timeout", "45s"));

        // 操作：API の呼び出しの設定を作る
        var builder = StripeConfig.clientBuilder(properties);

        // 確認：設定の値
        assertThat(builder.getConnectTimeout()).isEqualTo(2_000);
        assertThat(builder.getReadTimeout()).isEqualTo(45_000);
    }

    @Test
    @DisplayName("照合を始めるまでの時間は、既定の値（接続 5 秒・応答 20 秒・再試行 2 回・照合 10 分）なら検証を通る")
    void acceptsDefaultCaptureReconcileDelay() {
        // 準備：Stripe で動かすときの設定ファイルの値
        var stripe = TestSettings.loadAndBind(PREFIX, StripeProperties.class, STRIPE_PROFILES);
        var payment = TestSettings.loadAndBind(PAYMENT_PREFIX, PaymentProperties.class, STRIPE_PROFILES);

        // 操作と確認：例外にならない
        assertThatCode(() -> StripeConfig.validateCaptureReconcileDelay(payment, stripe)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("照合を始めるまでの時間が、売上確定の最長の時間に余裕を加えた 145 秒より長ければ、検証を通る")
    void acceptsCaptureReconcileDelayJustOverRequired() {
        // 準備：最長の時間は (5 + 20) × 3 + 5 × 2（再試行の待ちの最大）= 85 秒、余裕 60 秒を加えて 145 秒。それより 1ms 長い値
        var stripe = stripe("5s", "20s");
        var payment = payment("145001ms");

        // 操作と確認：例外にならない
        assertThatCode(() -> StripeConfig.validateCaptureReconcileDelay(payment, stripe)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("照合を始めるまでの時間が、145 秒ちょうどなら、検証に失敗し、2 つの設定の名前と値がメッセージに含まれる")
    void rejectsCaptureReconcileDelayExactlyRequired() {
        // 準備：145 秒ちょうど（足りない）
        var stripe = stripe("5s", "20s");
        var payment = payment("145s");

        // 操作と確認：設定の名前と値が分かる
        assertThatThrownBy(() -> StripeConfig.validateCaptureReconcileDelay(payment, stripe))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ecsite.payment.capture-reconcile-delay")
                .hasMessageContaining("PT2M25S")
                .hasMessageContaining("ecsite.payment.stripe.connect-timeout")
                .hasMessageContaining("ecsite.payment.stripe.read-timeout")
                .hasMessageContaining("PT5S")
                .hasMessageContaining("PT20S");
    }

    @Test
    @DisplayName("応答の上限を長くして、照合を始めるまでの時間が足りなくなれば、検証に失敗する")
    void rejectsCaptureReconcileDelayWithLongTimeout() {
        // 準備：応答 200 秒。(5 + 200) × 3 + 10 + 60 = 685 秒が必要で、既定の 10 分（600 秒）では足りない
        var stripe = stripe("5s", "200s");
        var payment = payment("10m");

        // 操作と確認：失敗する
        assertThatThrownBy(() -> StripeConfig.validateCaptureReconcileDelay(payment, stripe))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("capture-reconcile-delay");
    }

    // ============================== 補助 ==============================

    /**
     * 接続と応答の上限を指定した Stripe の設定を作る（鍵は試験用の値）。
     */
    private static StripeProperties stripe(String connectTimeout, String readTimeout) {
        return TestSettings.bind(PREFIX, StripeProperties.class, Map.of(
                "secret-key", "sk_test_unit", "webhook-secret", "whsec_unit",
                "connect-timeout", connectTimeout, "read-timeout", readTimeout));
    }

    /**
     * 照合を始めるまでの時間を指定した決済の設定を作る。
     */
    private static PaymentProperties payment(String captureReconcileDelay) {
        return TestSettings.bind(PAYMENT_PREFIX, PaymentProperties.class, Map.of(
                "return-url", "http://store/orders/{orderId}", "capture-reconcile-delay", captureReconcileDelay));
    }
}
