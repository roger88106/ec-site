package com.ecsite.module.payment.infrastructure.mock;

import com.ecsite.MutableClock;
import com.ecsite.common.error.CommonErrorCode;
import com.ecsite.module.payment.application.port.PaymentGateway;
import com.ecsite.module.payment.application.port.PaymentGateway.CaptureRequest;
import com.ecsite.module.payment.application.port.PaymentGateway.PageRequest;
import com.ecsite.module.payment.application.port.PaymentGateway.RefundRequest;
import com.ecsite.module.payment.application.port.PaymentNotificationReceiver;
import com.ecsite.module.payment.infrastructure.PaymentGatewayContractTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static com.ecsite.BusinessErrorAssertions.assertSystemError;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * 本番の mock（{@link MockPaymentGateway}）が、port の振る舞い（{@link PaymentGatewayContractTest}）を守ることの検証
 *
 * <p>状態は、本番の mock が持つ支払画面と port の操作で用意する。テスト用の実装（{@code TestPaymentGateway}）は使わない。
 * 本番の mock は与信の期限切れを持たないので、与信の期限切れの振る舞いは確認しない（Stripe の実装だけの契約）。
 */
@DisplayName("mock の決済サービス")
class MockPaymentGatewayContractTest extends PaymentGatewayContractTest {

    // ============================== 定数 ==============================

    /**
     * 支払を済ませた時刻
     */
    private static final Instant PAID_AT = Instant.parse("2026-11-06T01:00:00Z");

    /**
     * 支払画面から戻る店舗の注文の画面
     */
    private static final String RETURN_URL = "http://localhost:3000/orders";

    // ============================== 対象 ==============================

    /**
     * mock が支払を済ませた時刻を読む時計
     */
    private final MutableClock clock = new MutableClock(PAID_AT);

    /**
     * 確認する本番の mock
     */
    private final MockPaymentGateway gateway = new MockPaymentGateway(
            mock(PaymentNotificationReceiver.class), new MockPaymentProperties("http://localhost:8080"), clock);

    // ============================== テスト ==============================

    @Nested
    @DisplayName("mock だけの与信の取得")
    class MockFetchAuthorization {

        @Test
        @DisplayName("AC-409-2 与信の時刻は、支払を済ませた時刻")
        void returnsPaidTimeAsAuthorizedAt() {
            // 準備：支払の済んだ画面
            var pageId = paidPage();

            // 操作：与信を取得する
            var authorization = gateway.fetchAuthorization(pageId);

            // 確認：与信の時刻が支払を済ませた時刻で、参照はこの画面の与信
            assertThat(authorization.authorizedAt()).isEqualTo(PAID_AT);
            assertThat(authorization.authorizationRef()).isEqualTo(gateway.authorizationRefOf(pageId));
        }

        @Test
        @DisplayName("AC-409-2 重ねて支払っても、与信の時刻は最初に支払を済ませた時刻のまま")
        void keepsFirstPaidTime() {
            // 準備：支払を済ませ、時刻を進めて重ねて支払う
            var pageId = paidPage();
            clock.setInstant(PAID_AT.plusSeconds(600));
            gateway.markPaid(pageId);

            // 操作：与信を取得する
            var authorization = gateway.fetchAuthorization(pageId);

            // 確認：最初の時刻のまま
            assertThat(authorization.authorizedAt()).isEqualTo(PAID_AT);
        }

        @Test
        @DisplayName("存在しない支払画面の与信の取得は、予期しないエラー")
        void rejectsUnknownPage() {
            // 操作と確認：予期しないエラー
            assertSystemError(() -> gateway.fetchAuthorization("mock_page_unknown"), CommonErrorCode.SYSTEM_ERROR);
        }
    }

    // ============================== 状態の用意 ==============================

    @Override
    protected PaymentGateway gateway() {
        return gateway;
    }

    @Override
    protected String authorized() {
        return gateway.authorizationRefOf(paidPage());
    }

    @Override
    protected void givenCaptured(String authorizationRef) {
        gateway.capture(new CaptureRequest(ORDER_ID, authorizationRef, AMOUNT));
    }

    @Override
    protected void givenVoided(String authorizationRef) {
        gateway.voidAuthorization(authorizationRef);
    }

    @Override
    protected void givenRefunded(String authorizationRef) {
        gateway.refund(new RefundRequest(ORDER_ID, authorizationRef, AMOUNT));
    }

    @Override
    protected String paidPage() {
        var pageId = openPage();
        gateway.markPaid(pageId);
        return pageId;
    }

    @Override
    protected String closedPage() {
        var pageId = openPage();
        gateway.closePage(pageId);
        return pageId;
    }

    /**
     * 支払画面を開く。
     *
     * @return 支払画面 ID
     */
    private String openPage() {
        return gateway.openPage(new PageRequest(UUID.randomUUID(), ORDER_ID, AMOUNT, RETURN_URL)).id();
    }
}
