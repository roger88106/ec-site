package com.ecsite.module.payment.application;

import com.ecsite.module.payment.domain.payment.AuthorizationTerm;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.hibernate.validator.constraints.time.DurationMax;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.UUID;

/**
 * 決済の設定
 *
 * <p>環境変数で上書きする（例：ECSITE_PAYMENT_DEADLINE）。決済サービスは mock フラグ（{@code ecsite.payment.mock.enabled}）で選び、
 * ここには持たない。
 *
 * @param deadline                     支払の期限（注文日時からの時間）。上限は、注文の後に作る支払画面の期限
 *                                     （Stripe の最短の 30 分に余裕を加えた時間）が支払の期限より前に来ないように決める
 * @param expiryInterval               支払の期限切れのジョブの間隔
 * @param awaitingAuthorizationWarning 与信の反映を待ち始めてから、どれだけ過ぎたら長く待つ支払として warn のログに出すか
 * @param authorizationValidity        与信の有効期間（与信の時刻からの時間）。Stripe のカード決済の与信が切れるまでの期間に合わせる
 * @param authorizationWarning         与信期限のどれだけ前から「期限が近い」とするか
 * @param captureReconcileDelay        売上確定を求めてから、売上確定の結果が分からない支払の照合を始めるまでの時間。
 *                                     下限は 2 分（mock でも適用する）。Stripe で動かすときは、照合を始めるまでの時間が、Stripe の設定（接続と応答の上限、
 *                                     再試行の回数と待ち）から決まる売上確定の 1 回の呼び出しの最長の時間に、余裕を加えた時間より長いことを、
 *                                     起動のときに検証する。
 *                                     短いと、売上確定の最中の支払を照合し、まだ確定していない状態を読んで、
 *                                     売上確定を求めた時刻を削除しうる。その後に応答が届かないと、支払は時刻のない与信済みのまま残り、二度と照合されない
 * @param returnUrl                    支払画面から戻る店舗の注文の画面の URL。{@code {orderId}} に注文 ID が入る
 */
@Validated
@ConfigurationProperties("ecsite.payment")
public record PaymentProperties(
        @DefaultValue("30m") @NotNull @DurationMin(millis = 1) @DurationMax(minutes = 30) Duration deadline,
        @DefaultValue("1m") @NotNull @DurationMin(millis = 1) Duration expiryInterval,
        @DefaultValue("1h") @NotNull @DurationMin(millis = 1) Duration awaitingAuthorizationWarning,
        @DefaultValue("7d") @NotNull @DurationMin(millis = 1) Duration authorizationValidity,
        @DefaultValue("2d") @NotNull @DurationMin(nanos = 0) Duration authorizationWarning,
        @DefaultValue("10m") @NotNull @DurationMin(minutes = 2) Duration captureReconcileDelay,
        @NotBlank String returnUrl) {

    /**
     * 与信の期限が近いとする期間が、与信の有効期間より短いかを返す。
     *
     * <p>値の検証で検査し、短くなければ起動に失敗させる（与信を受けた時点から期限が近いことになるため）。
     * どちらかが null か、項目の下限を満たさないときは、項目の検証が失敗にするので、true を返す。
     *
     * @return 短いか、項目の検証で失敗するなら true
     */
    @AssertTrue(message = "authorization-warning は authorization-validity より短くする")
    boolean isAuthorizationWarningShorterThanValidity() {
        if (authorizationValidity == null || authorizationWarning == null
                || !authorizationValidity.isPositive() || authorizationWarning.isNegative()) {
            return true;
        }
        return authorizationWarning.compareTo(authorizationValidity) < 0;
    }

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
