package com.ecsite.module.payment.infrastructure.stripe;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Stripe の設定。決済サービスが stripe のときだけ使う。<br />
 * 秘密の値は Git に入れず、環境変数 {@code STRIPE_SECRET_KEY}、{@code STRIPE_WEBHOOK_SECRET} で渡す（application.yml）。
 *
 * @param secretKey     API の秘密の鍵（テストモードは {@code sk_test_} で始まる）
 * @param webhookSecret 通知の署名の鍵（{@code whsec_} で始まる）
 */
@Validated
@ConfigurationProperties("ecsite.payment.stripe")
public record StripeProperties(@NotBlank String secretKey, @NotBlank String webhookSecret) {

    @Override
    public String toString() {
        // 秘密の値をログに出さない
        return "StripeProperties[secretKey=***, webhookSecret=***]";
    }
}
