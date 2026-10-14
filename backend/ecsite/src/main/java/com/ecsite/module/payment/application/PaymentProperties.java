package com.ecsite.module.payment.application;

import com.ecsite.module.payment.domain.payment.AuthorizationTerm;
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
 * @param gateway               決済サービス
 * @param deadline              支払の期限（注文日時からの時間）
 * @param authorizationValidity 与信の有効期間（与信の時刻からの時間）。Stripe のカード決済の与信が切れる 7 日
 * @param authorizationWarning  与信期限のどれだけ前から「期限が近い」とするか
 * @param returnUrl             支払の画面から戻る店舗の注文の画面の URL。{@code {orderId}} に注文 ID が入る
 */
@Validated
@ConfigurationProperties("ecsite.payment")
public record PaymentProperties(
        @DefaultValue("mock") @NotNull Gateway gateway,
        @DefaultValue("30m") @NotNull Duration deadline,
        @DefaultValue("7d") @NotNull Duration authorizationValidity,
        @DefaultValue("2d") @NotNull Duration authorizationWarning,
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
     * 与信の有効期間と、期限が近いとする長さを返す。
     *
     * @return 与信の有効期間
     */
    public AuthorizationTerm authorizationTerm() {
        return new AuthorizationTerm(authorizationValidity, authorizationWarning);
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
