package com.ecsite.module.payment.api;

import com.ecsite.IntegrationTest;
import com.ecsite.MutableClock;
import com.ecsite.PaymentTestData;
import com.ecsite.TestClockConfig;
import com.ecsite.module.payment.domain.PaymentErrorCode;
import com.ecsite.module.payment.infrastructure.mock.TestPaymentGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.ecsite.BusinessErrorAssertions.assertBusinessError;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 与信期限の統合テスト（T9110、AC-186、187、188、BR-20）。<br />
 * 与信の有効期間は 7 日、期限が近いとする長さは 2 日（既定）。支払は保存されて他のテストと共有されるので、
 * 注文 ID の一覧は、自分の注文を含むか・含まないかで確認する。
 */
@IntegrationTest
@Import(TestClockConfig.class)
class PaymentAuthorizationDeadlineTest {

    /**
     * 与信の時刻（注文と同時に支払う）
     */
    private static final Instant AUTHORIZED_AT = Instant.parse("2026-10-07T15:30:00Z");

    /**
     * 与信期限（与信の時刻 ＋ 7 日）
     */
    private static final Instant DEADLINE = AUTHORIZED_AT.plus(Duration.ofDays(7));

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
        clock.setInstant(AUTHORIZED_AT);
        gateway.reset();
    }

    @Test
    @DisplayName("BR-20 与信を受けた注文の与信期限は、与信の時刻の 7 日後。与信を受けていない注文は含まない")
    void returnsDeadlineOfAuthorizedOrders() {
        var authorized = payments.authorized(AUTHORIZED_AT);
        var unpaid = payments.created(AUTHORIZED_AT);

        var deadlines = paymentApi.findAuthorizationDeadlines(List.of(authorized, unpaid, UUID.randomUUID()));

        assertThat(deadlines).containsExactly(new AuthorizationDeadline(authorized, DEADLINE, false));
    }

    @Test
    @DisplayName("AC-186 与信期限の 2 日前（5 日後）から、期限が近い。その前は近くない")
    void expiringFromTwoDaysBefore() {
        var orderId = payments.authorized(AUTHORIZED_AT);

        clock.setInstant(DEADLINE.minus(Duration.ofDays(2)).minusSeconds(1));
        assertThat(expiringOf(orderId)).isFalse();
        assertThat(paymentApi.findExpiringOrderIds()).doesNotContain(orderId);

        clock.setInstant(DEADLINE.minus(Duration.ofDays(2)));
        assertThat(expiringOf(orderId)).isTrue();
        assertThat(paymentApi.findExpiringOrderIds()).contains(orderId);
    }

    @Test
    @DisplayName("AC-186、187、188 与信期限を過ぎた注文も、期限が近いに含み、一覧にも出る")
    void includesPastDeadline() {
        var orderId = payments.authorized(AUTHORIZED_AT);

        clock.setInstant(DEADLINE.plus(Duration.ofDays(3)));

        assertThat(expiringOf(orderId)).isTrue();
        assertThat(paymentApi.findExpiringOrderIds()).contains(orderId);
    }

    @Test
    @DisplayName("AC-188 売上確定の与信期限切れで与信期限切れになった注文も、一覧に出る")
    void includesAuthorizationExpired() {
        var orderId = payments.authorized(AUTHORIZED_AT);
        gateway.expireAuthorization(gateway.authorizationRefOf(payments.pageIdOf(orderId)));
        clock.setInstant(DEADLINE.plusSeconds(1));
        assertBusinessError(() -> paymentApi.capture(orderId), PaymentErrorCode.AUTHORIZATION_EXPIRED);

        assertThat(expiringOf(orderId)).isTrue();
        assertThat(paymentApi.findExpiringOrderIds()).contains(orderId);
    }

    @Test
    @DisplayName("BR-20 売上確定済みの注文は、期限を過ぎても、期限が近いに含めず、一覧にも出さない")
    void excludesCaptured() {
        var orderId = payments.authorized(AUTHORIZED_AT);
        paymentApi.capture(orderId);

        clock.setInstant(DEADLINE.plus(Duration.ofDays(3)));

        assertThat(expiringOf(orderId)).isFalse();
        assertThat(paymentApi.findExpiringOrderIds()).doesNotContain(orderId);
    }

    // ============================== 補助 ==============================

    private boolean expiringOf(UUID orderId) {
        return paymentApi.findAuthorizationDeadlines(List.of(orderId)).getFirst().expiring();
    }
}
