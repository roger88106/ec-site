package com.ecsite.module.payment.application;

import com.ecsite.module.payment.domain.payment.AuthorizationTerm;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.hibernate.validator.constraints.time.DurationMax;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.UUID;

/**
 * 決済の設定。環境変数で上書きする（例：ECSITE_PAYMENT_DEADLINE）。<br />
 * 決済サービスは mock フラグ（{@code ecsite.payment.mock.enabled}）で選び、ここには持たない。
 *
 * @param deadline                     支払の期限（注文日時からの時間）。上限は 30 分。支払画面は注文の後に作り、その期限（作ってから 31 分）が
 *                                     支払の期限より前に来ないようにするため
 * @param expiryInterval               支払の期限切れのジョブの間隔
 * @param awaitingAuthorizationWarning 与信の通知を待ち始めてから、どれだけ過ぎたら長く待つ支払として warn のログに出すか
 * @param authorizationValidity        与信の有効期間（与信の時刻からの時間）。Stripe のカード決済の与信が切れる 7 日
 * @param authorizationWarning         与信期限のどれだけ前から「期限が近い」とするか
 * @param returnUrl                    支払画面から戻る店舗の注文の画面の URL。{@code {orderId}} に注文 ID が入る
 */
@Validated
@ConfigurationProperties("ecsite.payment")
public record PaymentProperties(
        @DefaultValue("30m") @NotNull @DurationMax(minutes = 30) Duration deadline,
        @DefaultValue("1m") @NotNull Duration expiryInterval,
        @DefaultValue("1h") @NotNull Duration awaitingAuthorizationWarning,
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
}
