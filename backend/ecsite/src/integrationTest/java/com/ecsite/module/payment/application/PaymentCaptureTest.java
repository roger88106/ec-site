package com.ecsite.module.payment.application;

import com.ecsite.IntegrationTest;
import com.ecsite.MutableClock;
import com.ecsite.PaymentTestData;
import com.ecsite.TestClockConfig;
import com.ecsite.common.error.CommonErrorCode;
import com.ecsite.common.error.exception.SystemException;
import com.ecsite.module.payment.api.PaymentApi;
import com.ecsite.module.payment.api.PaymentSummary;
import com.ecsite.module.payment.domain.PaymentErrorCode;
import com.ecsite.module.payment.infrastructure.mock.TestPaymentGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.util.UUID;

import static com.ecsite.BusinessErrorAssertions.assertBusinessError;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 売上確定の統合テスト（T9106、AC-168、169、172、BR-21、NFR-10）。<br />
 * 決済サービスは mock。支払はトランザクションを確定させて保存するので、テストごとに別の注文 ID を使う。
 */
@IntegrationTest
@Import(TestClockConfig.class)
class PaymentCaptureTest {

    /**
     * 注文日時
     */
    private static final Instant ORDERED_AT = Instant.parse("2026-10-07T15:30:00Z");

    @Autowired
    PaymentApi paymentApi;

    @Autowired
    TestPaymentGateway gateway;

    @Autowired
    MutableClock clock;

    PaymentTestData payments;

    @BeforeEach
    void setUp() {
        payments = new PaymentTestData(paymentApi, gateway);
        clock.setInstant(ORDERED_AT);
        gateway.reset();
    }

    @Test
    @DisplayName("AC-168、BR-21 与信済みの支払を確定すると、決済サービスで売上が確定し、売上確定済みになる")
    void capturesAuthorizedPayment() {
        var orderId = payments.authorized(ORDERED_AT);

        paymentApi.capture(orderId);

        assertThat(payments.statusOf(orderId)).isEqualTo(PaymentSummary.Status.CAPTURED);
        assertThat(gateway.captured()).containsExactly(orderId);
    }

    @Test
    @DisplayName("AC-172 売上確定済みの支払を重ねて確定しても、決済サービスを呼ばずに成功する")
    void capturesOnlyOnce() {
        var orderId = payments.authorized(ORDERED_AT);
        paymentApi.capture(orderId);

        paymentApi.capture(orderId);

        assertThat(payments.statusOf(orderId)).isEqualTo(PaymentSummary.Status.CAPTURED);
        assertThat(gateway.captureCalls()).isEqualTo(1);
    }

    @Test
    @DisplayName("AC-169 決済サービスが与信期限切れで断ったら、与信期限切れになり、業務のエラーを返す")
    void expiresAuthorizationWhenGatewayRejects() {
        var orderId = payments.authorized(ORDERED_AT);
        gateway.expireAuthorization(gateway.authorizationRefOf(payments.pageIdOf(orderId)));

        assertBusinessError(() -> paymentApi.capture(orderId), PaymentErrorCode.AUTHORIZATION_EXPIRED);

        assertThat(payments.statusOf(orderId)).isEqualTo(PaymentSummary.Status.AUTHORIZATION_EXPIRED);
        assertThat(gateway.captured()).isEmpty();
    }

    @Test
    @DisplayName("AC-169 与信期限切れの支払を重ねて確定しても、決済サービスを呼ばずに、同じ業務のエラーを返す")
    void rejectsCaptureOfAuthorizationExpired() {
        var orderId = payments.authorized(ORDERED_AT);
        gateway.expireAuthorization(gateway.authorizationRefOf(payments.pageIdOf(orderId)));
        assertBusinessError(() -> paymentApi.capture(orderId), PaymentErrorCode.AUTHORIZATION_EXPIRED);

        assertBusinessError(() -> paymentApi.capture(orderId), PaymentErrorCode.AUTHORIZATION_EXPIRED);

        assertThat(gateway.captureCalls()).isEqualTo(1);
    }

    @Test
    @DisplayName("AC-169、NFR-10 決済サービスに接続できなければ、与信済みのまま、システムのエラーを返す。つなぎ直せば確定できる")
    void keepsAuthorizedWhenGatewayUnreachable() {
        var orderId = payments.authorized(ORDERED_AT);
        gateway.failNextCaptures(1);

        assertThatThrownBy(() -> paymentApi.capture(orderId)).isInstanceOf(SystemException.class);

        assertThat(payments.statusOf(orderId)).isEqualTo(PaymentSummary.Status.AUTHORIZED);
        paymentApi.capture(orderId);
        assertThat(payments.statusOf(orderId)).isEqualTo(PaymentSummary.Status.CAPTURED);
        assertThat(gateway.captured()).containsExactly(orderId);
    }

    @Test
    @DisplayName("BR-21 与信済みでない支払（未払い）は確定できず、決済サービスも呼ばない")
    void rejectsCaptureOfUnpaid() {
        var orderId = payments.created(ORDERED_AT);

        assertBusinessError(() -> paymentApi.capture(orderId), PaymentErrorCode.PAYMENT_NOT_CAPTURABLE);

        assertThat(payments.statusOf(orderId)).isEqualTo(PaymentSummary.Status.UNPAID);
        assertThat(gateway.captureCalls()).isZero();
    }

    @Test
    @DisplayName("BR-21 支払のない注文は確定できない")
    void rejectsCaptureOfUnknownOrder() {
        assertBusinessError(() -> paymentApi.capture(UUID.randomUUID()), CommonErrorCode.NOT_FOUND);

        assertThat(gateway.captureCalls()).isZero();
    }
}
