package com.ecsite.module.payment.infrastructure.mock;

import com.ecsite.MutableClock;
import com.ecsite.common.error.exception.SystemException;
import com.ecsite.module.payment.application.port.PaymentGateway;
import com.ecsite.module.payment.application.port.PaymentGateway.CaptureRequest;
import com.ecsite.module.payment.application.port.PaymentGateway.PageRequest;
import com.ecsite.module.payment.application.port.PaymentGateway.RefundRequest;
import com.ecsite.module.payment.application.port.PaymentNotificationReceiver;
import com.ecsite.module.payment.infrastructure.PaymentGatewayContractTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * mock の決済サービスが、port の振る舞い（{@link PaymentGatewayContractTest}）を守ることの検証
 *
 * <p>状態は、mock の支払画面の操作と、mock を包むテスト用の実装（{@link TestPaymentGateway}。与信を切れたことにする）で用意する。
 */
@DisplayName("mock の決済サービス")
class MockPaymentGatewayContractTest extends PaymentGatewayContractTest {

    private static final Instant PAID_AT = Instant.parse("2026-11-06T01:00:00Z");

    private final MutableClock clock = new MutableClock(PAID_AT);

    private final TestPaymentGateway gateway = new TestPaymentGateway(new MockPaymentGateway(
            mock(PaymentNotificationReceiver.class), new MockPaymentProperties("http://localhost:8080"), clock));

    @Test
    @DisplayName("AC-409-2 mock は、支払を済ませた時刻を与信の時刻として返し、重ねて支払っても最初の時刻のまま")
    void returnsFirstPaidTimeAsAuthorizedAt() {
        var pageId = gateway.openPage(new PageRequest(UUID.randomUUID(), ORDER_ID, AMOUNT, "http://localhost:3000/orders")).id();
        gateway.markPaid(pageId);
        clock.setInstant(PAID_AT.plusSeconds(600));
        gateway.markPaid(pageId);

        var authorization = gateway.fetchAuthorization(pageId);

        assertThat(authorization.authorizedAt()).isEqualTo(PAID_AT);
        assertThat(authorization.authorizationRef()).isEqualTo(gateway.authorizationRefOf(pageId));
    }

    @Test
    @DisplayName("存在しない支払画面の与信の取得は、予期しないエラー")
    void rejectsUnknownPage() {
        assertThatThrownBy(() -> gateway.fetchAuthorization("mock_page_unknown")).isInstanceOf(SystemException.class);
    }

    @Override
    protected PaymentGateway gateway() {
        return gateway;
    }

    @Override
    protected String authorized() {
        var pageId = gateway.openPage(new PageRequest(UUID.randomUUID(), ORDER_ID, AMOUNT, "http://localhost:3000/orders")).id();
        gateway.markPaid(pageId);
        return gateway.authorizationRefOf(pageId);
    }

    @Override
    protected void givenCaptured(String authorizationRef) {
        gateway.capture(new CaptureRequest(ORDER_ID, authorizationRef, AMOUNT));
    }

    @Override
    protected void givenAuthorizationExpired(String authorizationRef) {
        gateway.expireAuthorization(authorizationRef);
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
        var pageId = gateway.openPage(new PageRequest(UUID.randomUUID(), ORDER_ID, AMOUNT, "http://localhost:3000/orders")).id();
        gateway.markPaid(pageId);
        return pageId;
    }

    @Override
    protected String paidPageWithExpiredAuthorization() {
        var pageId = paidPage();
        gateway.expireAuthorization(gateway.authorizationRefOf(pageId));
        return pageId;
    }

    @Override
    protected String closedPage() {
        var pageId = gateway.openPage(new PageRequest(UUID.randomUUID(), ORDER_ID, AMOUNT, "http://localhost:3000/orders")).id();
        gateway.closePage(pageId);
        return pageId;
    }
}
