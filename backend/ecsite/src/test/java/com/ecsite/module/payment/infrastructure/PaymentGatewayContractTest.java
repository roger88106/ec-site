package com.ecsite.module.payment.infrastructure;

import com.ecsite.common.error.CommonErrorCode;
import com.ecsite.common.model.value.Money;
import com.ecsite.module.payment.application.port.PaymentGateway;
import com.ecsite.module.payment.application.port.PaymentGateway.CaptureRequest;
import com.ecsite.module.payment.application.port.PaymentGateway.CaptureResult;
import com.ecsite.module.payment.application.port.PaymentGateway.CloseResult;
import com.ecsite.module.payment.application.port.PaymentGateway.RefundRequest;
import com.ecsite.module.payment.application.port.PaymentGateway.VoidResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static com.ecsite.support.BusinessErrorAssertions.assertSystemError;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * 決済サービスの port（{@link PaymentGateway}）の、実装によらない振る舞い
 *
 * <p>mock と Stripe の実装の子のクラスが、決済サービスの状態を用意する。支払の処理は、ここの振る舞いを前提に、
 * 重ねて届いたリクエストや、済んだ後のリクエストを成功として扱う。
 *
 * <p>与信の期限切れは、決済サービスの側で起きる状態で、本番の mock は持たない。そのため、期限切れの振る舞いは
 * Stripe の子のクラス（{@code StripePaymentGatewayContractTest}）だけが確認する。
 */
public abstract class PaymentGatewayContractTest {

    // ============================== 定数 ==============================

    /**
     * テストの注文 ID（冪等キーの元）
     */
    protected static final UUID ORDER_ID = UUID.fromString("0c4b7f7e-2f2c-4a59-9a39-8a0b6a0f0101");

    /**
     * テストの総額
     */
    protected static final Money AMOUNT = Money.of(6940);

    // ============================== 子のクラスが用意する状態 ==============================

    /**
     * 確認する実装を返す。
     *
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

    // ============================== テスト ==============================

    @Nested
    @DisplayName("売上確定")
    class Capture {

        @Test
        @DisplayName("確定済みの売上を重ねて確定しても、確定済み")
        void capturesAgain() {
            // 準備：売上を確定した与信
            var authorizationRef = authorized();
            givenCaptured(authorizationRef);

            // 操作：同じ与信で重ねて確定する
            var result = gateway().capture(new CaptureRequest(ORDER_ID, authorizationRef, AMOUNT));

            // 確認：確定済み
            assertThat(result).isEqualTo(CaptureResult.CAPTURED);
        }
    }

    @Nested
    @DisplayName("与信の取消")
    class VoidAuthorization {

        @Test
        @DisplayName("AC-166 取消済みの与信を重ねて取り消しても、結果は「取り消した」")
        void voidsAgain() {
            // 準備：取り消した与信
            var authorizationRef = authorized();
            givenVoided(authorizationRef);

            // 操作：重ねて取り消す
            var result = gateway().voidAuthorization(authorizationRef);

            // 確認：取り消した
            assertThat(result).isEqualTo(VoidResult.VOIDED);
        }

        @Test
        @DisplayName("売上を確定した与信の取消は、例外にせず、売上確定済みを返す（返金で戻す）")
        void voidCapturedReturnsAlreadyCaptured() {
            // 準備：売上を確定した与信
            var authorizationRef = authorized();
            givenCaptured(authorizationRef);

            // 操作：取り消す
            var result = gateway().voidAuthorization(authorizationRef);

            // 確認：売上確定済み
            assertThat(result).isEqualTo(VoidResult.ALREADY_CAPTURED);
        }
    }

    @Nested
    @DisplayName("返金")
    class Refund {

        @Test
        @DisplayName("返金済みの支払を重ねて返金しても、成功")
        void refundsAgain() {
            // 準備：返金した与信
            var authorizationRef = authorized();
            givenRefunded(authorizationRef);

            // 操作と確認：重ねて返金しても例外にならない
            assertThatCode(() -> gateway().refund(new RefundRequest(ORDER_ID, authorizationRef, AMOUNT)))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("支払画面を閉じる")
    class ClosePage {

        @Test
        @DisplayName("支払の済んだ画面を閉じると、支払済み")
        void closesPaidPage() {
            // 準備：支払の済んだ画面
            var pageId = paidPage();

            // 操作：閉じる
            var result = gateway().closePage(pageId);

            // 確認：支払済み
            assertThat(result).isEqualTo(CloseResult.ALREADY_PAID);
        }

        @Test
        @DisplayName("閉じた画面を重ねて閉じても、閉じた")
        void closesAgain() {
            // 準備：閉じた画面
            var pageId = closedPage();

            // 操作：重ねて閉じる
            var result = gateway().closePage(pageId);

            // 確認：閉じた
            assertThat(result).isEqualTo(CloseResult.CLOSED);
        }
    }

    @Nested
    @DisplayName("与信の取得")
    class FetchAuthorization {

        @Test
        @DisplayName("AC-409-1 支払の済んだ画面から、与信の参照と与信の時刻を取得できる")
        void fetchesAuthorizationOfPaidPage() {
            // 準備：支払の済んだ画面
            var pageId = paidPage();

            // 操作：与信を取得する
            var authorization = gateway().fetchAuthorization(pageId);

            // 確認：与信の参照と与信の時刻がある
            assertThat(authorization.authorizationRef()).isNotBlank();
            assertThat(authorization.authorizedAt()).isNotNull();
        }

        @Test
        @DisplayName("閉じた画面の与信の取得は、予期しないエラーの例外")
        void fetchesNothingFromClosedPage() {
            // 準備：閉じた画面
            var pageId = closedPage();

            // 操作と確認：予期しないエラー
            assertSystemError(() -> gateway().fetchAuthorization(pageId), CommonErrorCode.SYSTEM_ERROR);
        }
    }
}
