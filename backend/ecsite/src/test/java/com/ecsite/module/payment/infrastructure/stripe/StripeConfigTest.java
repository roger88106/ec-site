package com.ecsite.module.payment.infrastructure.stripe;

import com.ecsite.support.TestSettings;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

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
}
