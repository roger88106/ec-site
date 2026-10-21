package com.ecsite.module.payment.api;

import com.ecsite.IntegrationTest;
import com.ecsite.MutableClock;
import com.ecsite.TestClockConfig;
import com.ecsite.common.model.value.Money;
import com.ecsite.module.payment.event.PaymentAuthorized;
import com.ecsite.module.payment.infrastructure.mock.TestPaymentGateway;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.ecsite.jooq.payment.Tables.PAYMENT_;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * 決済の API の統合テスト（T7207、AC-146、152、BR-15、16）。<br />
 * 決済サービスは mock。支払はトランザクションを確定させて保存するので、テストごとに別の注文 ID を使う。
 */
@IntegrationTest
@Import(TestClockConfig.class)
@RecordApplicationEvents
class PaymentApiTest {

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

    @Autowired
    DSLContext dsl;

    @Autowired
    ApplicationEvents events;

    @BeforeEach
    void setUp() {
        clock.setInstant(ORDERED_AT);
        gateway.reset();
    }

    @Test
    @DisplayName("BR-15、16 注文の支払は未払いで作られ、期限は注文日時の 30 分後。同じ注文で 2 回作っても 1 つ")
    void createsUnpaidPaymentOnce() {
        var orderId = UUID.randomUUID();

        paymentApi.create(orderId, Money.of(6940), ORDERED_AT);
        paymentApi.create(orderId, Money.of(6940), ORDERED_AT);

        assertThat(dsl.fetchCount(PAYMENT_, PAYMENT_.ORDER_ID.eq(orderId))).isEqualTo(1);
        assertThat(paymentApi.find(List.of(orderId))).containsExactly(
                new PaymentSummary(orderId, PaymentSummary.Status.UNPAID, ORDERED_AT.plus(Duration.ofMinutes(30))));
    }

    @Test
    @DisplayName("AC-152 支払画面の URL を何度求めても、開いている同じ画面を返し、決済サービスに再作成させない")
    void reusesOpenPage() {
        var orderId = created();

        var first = paymentApi.pageUrl(orderId);
        var second = paymentApi.pageUrl(orderId);

        assertThat(first).isPresent();
        assertThat(second).isEqualTo(first);
        assertThat(gateway.openedPages()).isEqualTo(1);
    }

    @Test
    @DisplayName("BR-16 支払の期限の後は、支払画面の URL を返さない")
    void noPageAfterDeadline() {
        var orderId = created();

        clock.advance(Duration.ofMinutes(30));

        assertThat(paymentApi.pageUrl(orderId)).isEmpty();
        assertThat(gateway.openedPages()).isZero();
    }

    @Test
    @DisplayName("支払のない注文には、支払画面の URL を返さない")
    void noPageForUnknownOrder() {
        assertThat(paymentApi.pageUrl(UUID.randomUUID())).isEmpty();
    }

    @Test
    @DisplayName("AC-146 与信の成功の通知で与信済みになり、与信の成功のイベントが出る")
    void authorizesOnNotification() {
        var orderId = created();
        var pageId = pageIdOf(paymentApi.pageUrl(orderId).orElseThrow());

        gateway.pay(pageId);

        assertThat(paymentApi.find(List.of(orderId)).getFirst().status()).isEqualTo(PaymentSummary.Status.AUTHORIZED);
        assertThat(events.stream(PaymentAuthorized.class)).singleElement()
                .satisfies(event -> assertThat(event.orderId()).isEqualTo(orderId));
    }

    @Test
    @DisplayName("BR-17 与信の成功の通知が重ねて届いても、イベントは 1 回だけ")
    void ignoresDuplicateNotification() {
        var orderId = created();
        var pageId = pageIdOf(paymentApi.pageUrl(orderId).orElseThrow());

        gateway.pay(pageId);
        gateway.notifyAuthorized(pageId);

        assertThat(events.stream(PaymentAuthorized.class)).hasSize(1);
    }

    @Test
    @DisplayName("支払の状態を、注文 ID でまとめて読める。支払のない注文は含まない")
    void findsSummaries() {
        var unpaid = created();
        var authorized = created();
        gateway.pay(pageIdOf(paymentApi.pageUrl(authorized).orElseThrow()));

        var summaries = paymentApi.find(List.of(unpaid, authorized, UUID.randomUUID()));

        assertThat(summaries).extracting(PaymentSummary::orderId, PaymentSummary::status).containsExactlyInAnyOrder(
                tuple(unpaid, PaymentSummary.Status.UNPAID),
                tuple(authorized, PaymentSummary.Status.AUTHORIZED));
    }

    // ============================== 補助 ==============================

    /**
     * 未払いの支払を作る。
     */
    private UUID created() {
        var orderId = UUID.randomUUID();
        paymentApi.create(orderId, Money.of(6940), ORDERED_AT);
        return orderId;
    }

    /**
     * mock の支払画面の URL から、画面の ID を取り出す。
     */
    private static String pageIdOf(String url) {
        return url.substring(url.lastIndexOf('/') + 1);
    }
}
