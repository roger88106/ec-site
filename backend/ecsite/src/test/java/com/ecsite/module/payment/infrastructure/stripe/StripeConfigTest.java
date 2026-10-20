package com.ecsite.module.payment.infrastructure.stripe;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Stripe の API の設定の単体テスト（T017-03-07、AC-301）。<br />
 * 設定は Spring を起動せずに、設定の型へ値を当てはめて確かめる。
 */
class StripeConfigTest {

    /**
     * Stripe の設定の接頭辞
     */
    private static final String PREFIX = "ecsite.payment.stripe";

    @Test
    @DisplayName("AC-301 Stripe の API は、既定で接続 5 秒・応答 20 秒の上限で呼ぶ")
    void defaultTimeouts() {
        var builder = StripeConfig.clientBuilder(bind(Map.of()));

        assertThat(builder.getConnectTimeout()).isEqualTo(5_000);
        assertThat(builder.getReadTimeout()).isEqualTo(20_000);
    }

    @Test
    @DisplayName("AC-301 接続と応答の上限は、設定で変えられる")
    void overridesTimeouts() {
        var builder = StripeConfig.clientBuilder(bind(Map.of("connect-timeout", "2s", "read-timeout", "45s")));

        assertThat(builder.getConnectTimeout()).isEqualTo(2_000);
        assertThat(builder.getReadTimeout()).isEqualTo(45_000);
    }

    /**
     * 接頭辞の下の値を、Stripe の設定に当てはめる（鍵は試験用の値）。
     */
    private static StripeProperties bind(Map<String, String> values) {
        var all = new HashMap<>(values);
        all.put("secret-key", "sk_test_unit");
        all.put("webhook-secret", "whsec_unit");
        var source = new MapConfigurationPropertySource();
        all.forEach((key, value) -> source.put(PREFIX + "." + key, value));
        return new Binder(source).bindOrCreate(PREFIX, StripeProperties.class);
    }
}
