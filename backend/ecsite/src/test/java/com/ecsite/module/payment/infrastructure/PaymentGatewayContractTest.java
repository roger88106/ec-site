package com.ecsite.module.payment.infrastructure;

import com.ecsite.common.error.exception.SystemException;
import com.ecsite.common.model.value.Money;
import com.ecsite.module.payment.application.port.PaymentGateway;
import com.ecsite.module.payment.application.port.PaymentGateway.CaptureRequest;
import com.ecsite.module.payment.application.port.PaymentGateway.CaptureResult;
import com.ecsite.module.payment.application.port.PaymentGateway.CloseResult;
import com.ecsite.module.payment.application.port.PaymentGateway.RefundRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 決済サービスの port（{@link PaymentGateway}）の、実装によらない振る舞い（テストの書き方 第 1 節「mock と実装の挙動の一致」）。<br />
 * mock と Stripe の実装の子のクラスが、決済サービスの状態を用意する。支払の処理は、ここの振る舞いを前提に、
 * 重ねて届いた要求や、済んだ後の要求を成功として扱う。
 */
public abstract class PaymentGatewayContractTest {

    /**
     * テストの注文 ID（冪等キーの元）
     */
    protected static final UUID ORDER_ID = UUID.fromString("0c4b7f7e-2f2c-4a59-9a39-8a0b6a0f0101");

    /**
     * テストの総額
     */
    protected static final Money AMOUNT = Money.of(6940);

    /**
     * @return 確認する実装
     */
    protected abstract PaymentGateway gateway();

    /**
     * 与信を取った状態にする。
     *
     * @return 与信の参照
     */
    protected abstract String authorized();

    /**
     * 与信の売上を確定した状態にする。
     *
     * @param authorizationRef 与信の参照
     */
    protected abstract void givenCaptured(String authorizationRef);

    /**
     * 与信の期限が切れた状態にする。
     *
     * @param authorizationRef 与信の参照
     */
    protected abstract void givenAuthorizationExpired(String authorizationRef);

    /**
     * 与信を取り消した状態にする。
     *
     * @param authorizationRef 与信の参照
     */
    protected abstract void givenVoided(String authorizationRef);

    /**
     * 返金した状態にする。
     *
     * @param authorizationRef 与信の参照
     */
    protected abstract void givenRefunded(String authorizationRef);

    /**
     * 支払の済んだ支払画面を用意する。
     *
     * @return 支払画面 ID
     */
    protected abstract String paidPage();

    /**
     * 閉じた支払画面を用意する。
     *
     * @return 支払画面 ID
     */
    protected abstract String closedPage();

    @Test
    @DisplayName("確定済みの売上を重ねて確定しても、確定済み")
    void capturesAgain() {
        var authorizationRef = authorized();
        givenCaptured(authorizationRef);

        assertThat(gateway().capture(new CaptureRequest(ORDER_ID, authorizationRef, AMOUNT))).isEqualTo(CaptureResult.CAPTURED);
    }

    @Test
    @DisplayName("期限の切れた与信の売上を確定すると、与信期限切れ")
    void capturesExpiredAuthorization() {
        var authorizationRef = authorized();
        givenAuthorizationExpired(authorizationRef);

        assertThat(gateway().capture(new CaptureRequest(ORDER_ID, authorizationRef, AMOUNT)))
                .isEqualTo(CaptureResult.AUTHORIZATION_EXPIRED);
    }

    @Test
    @DisplayName("取消済みの与信を重ねて取り消しても、成功")
    void voidsAgain() {
        var authorizationRef = authorized();
        givenVoided(authorizationRef);

        assertThatCode(() -> gateway().voidAuthorization(authorizationRef)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("売上を確定した与信は取り消せず、システムのエラー（返金で戻す）")
    void cannotVoidCaptured() {
        var authorizationRef = authorized();
        givenCaptured(authorizationRef);

        assertThatThrownBy(() -> gateway().voidAuthorization(authorizationRef)).isInstanceOf(SystemException.class);
    }

    @Test
    @DisplayName("返金済みの支払を重ねて返金しても、成功")
    void refundsAgain() {
        var authorizationRef = authorized();
        givenRefunded(authorizationRef);

        assertThatCode(() -> gateway().refund(new RefundRequest(ORDER_ID, authorizationRef, AMOUNT)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("支払の済んだ画面を閉じると、支払済み")
    void closesPaidPage() {
        assertThat(gateway().closePage(paidPage())).isEqualTo(CloseResult.ALREADY_PAID);
    }

    @Test
    @DisplayName("閉じた画面を重ねて閉じても、閉じた")
    void closesAgain() {
        assertThat(gateway().closePage(closedPage())).isEqualTo(CloseResult.CLOSED);
    }
}
