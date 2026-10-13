package com.ecsite.module.payment.application;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.UUID;

/**
 * 決済の設定。環境変数で上書きする（例：ECSITE_PAYMENT_GATEWAY）。
 *
 * @param gateway   決済サービス
 * @param deadline  支払の期限（注文日時からの時間）
 * @param returnUrl 支払の画面から戻る店舗の注文の画面の URL。{@code {orderId}} に注文 ID が入る
 */
@Validated
@ConfigurationProperties("ecsite.payment")
public record PaymentProperties(
        @DefaultValue("mock") @NotNull Gateway gateway,
        @DefaultValue("30m") @NotNull Duration deadline,
        @NotBlank String returnUrl) {

    /**
     * 注文の戻る先の URL を作る。
     *
     * @param orderId 注文 ID
     * @return 戻る先の URL
     */
    public String returnUrlOf(UUID orderId) {
        return returnUrl.replace("{orderId}", orderId.toString());
    }

    /**
     * 決済サービス
     */
    public enum Gateway {

        /**
         * 内部の mock（自動テスト、CI、既定のローカル）
         */
        MOCK,

        /**
         * Stripe のテストモード
         */
        STRIPE,
    }
}
