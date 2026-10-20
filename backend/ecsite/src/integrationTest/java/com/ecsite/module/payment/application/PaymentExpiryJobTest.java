package com.ecsite.module.payment.application;

import com.ecsite.IntegrationTest;
import com.ecsite.MutableClock;
import com.ecsite.TestClockConfig;
import com.ecsite.common.model.value.Money;
import com.ecsite.module.payment.api.PaymentApi;
import com.ecsite.module.payment.api.PaymentSummary;
import com.ecsite.module.payment.event.PaymentExpired;
import com.ecsite.module.payment.infrastructure.mock.MockPaymentGateway;
import com.ecsite.module.payment.infrastructure.mock.TestPaymentGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanExpressionContext;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 支払の期限切れのジョブの統合テスト（T7209、T7308、T017-06-03、AC-153、154、156、311、NFR-7）。<br />
 * ジョブはテストから呼ぶ。ほかのテストの支払も期限切れになるので、イベントは注文 ID で絞って確かめる。
 */
@IntegrationTest
@Import(TestClockConfig.class)
@RecordApplicationEvents
class PaymentExpiryJobTest {

    /**
     * 注文日時
     */
    private static final Instant ORDERED_AT = Instant.parse("2026-10-07T15:30:00Z");

    /**
     * 支払の期限（注文日時 ＋ 30 分）
     */
    private static final Instant DEADLINE = ORDERED_AT.plus(Duration.ofMinutes(30));

    @Autowired
    PaymentExpiryJob job;

    @Autowired
    PaymentApi paymentApi;

    @Autowired
    TestPaymentGateway gateway;

    @Autowired
    MutableClock clock;

    @Autowired
    ApplicationEvents events;

    @Autowired
    ConfigurableListableBeanFactory beanFactory;

    @BeforeEach
    void setUp() {
        clock.setInstant(ORDERED_AT);
        gateway.reset();
    }

    @Test
    @DisplayName("AC-153 期限を過ぎた未払いの支払は、支払の画面を閉じて支払なしになり、期限切れのイベントが出る")
    void expiresUnpaidPastDeadline() {
        var orderId = created();
        var pageId = openPage(orderId);

        clock.setInstant(DEADLINE);
        job.expire();

        assertThat(statusOf(orderId)).isEqualTo(PaymentSummary.Status.NOT_PAID);
        assertThat(gateway.page(pageId).orElseThrow().status()).isEqualTo(MockPaymentGateway.PageStatus.CLOSED);
        assertThat(expiredEvents(orderId)).isEqualTo(1);
    }

    @Test
    @DisplayName("AC-153 支払の画面を開かなかった支払も、期限を過ぎたら支払なしになる")
    void expiresWithoutPage() {
        var orderId = created();

        clock.setInstant(DEADLINE);
        job.expire();

        assertThat(statusOf(orderId)).isEqualTo(PaymentSummary.Status.NOT_PAID);
        assertThat(expiredEvents(orderId)).isEqualTo(1);
    }

    @Test
    @DisplayName("期限の前の支払は、期限切れにしない")
    void keepsBeforeDeadline() {
        var orderId = created();
        openPage(orderId);

        clock.setInstant(DEADLINE.minusSeconds(1));
        job.expire();

        assertThat(statusOf(orderId)).isEqualTo(PaymentSummary.Status.UNPAID);
        assertThat(expiredEvents(orderId)).isZero();
    }

    @Test
    @DisplayName("AC-156 期限の直前に支払が済み、通知がまだ届いていなければ、期限切れにせず、通知で与信済みになる")
    void keepsPaidBeforeNotification() {
        var orderId = created();
        var pageId = openPage(orderId);
        gateway.markPaid(pageId);

        clock.setInstant(DEADLINE);
        job.expire();

        assertThat(statusOf(orderId)).isEqualTo(PaymentSummary.Status.UNPAID);
        assertThat(expiredEvents(orderId)).isZero();

        gateway.notifyAuthorized(pageId);

        assertThat(statusOf(orderId)).isEqualTo(PaymentSummary.Status.AUTHORIZED);
    }

    @Test
    @DisplayName("AC-311 与信の通知を待つ支払が 1 回の件数（100 件）を超えてたまっても、それより新しい期限切れの支払を処理する")
    void expiresNewerPaymentBehindAwaitingPayments() {
        // 期限の前に支払が済み、通知が届かない支払を、1 回の件数より多く作る
        var awaiting = new ArrayList<UUID>();
        for (int i = 0; i < 101; i++) {
            var orderId = created();
            gateway.markPaid(openPage(orderId));
            awaiting.add(orderId);
        }
        clock.setInstant(DEADLINE);
        // 通知を待つ支払にする（ほかのテストが残した期限切れの支払も含めて、すべてを一度は処理する）
        job.expire();
        job.expire();
        job.expire();

        // それより新しい支払が、期限を過ぎる
        var newerOrderedAt = ORDERED_AT.plus(Duration.ofHours(1));
        var newer = UUID.randomUUID();
        paymentApi.create(newer, Money.of(6940), newerOrderedAt);
        clock.setInstant(newerOrderedAt.plus(Duration.ofMinutes(30)));
        job.expire();

        assertThat(statusOf(newer)).isEqualTo(PaymentSummary.Status.NOT_PAID);
        assertThat(paymentApi.find(awaiting)).extracting(PaymentSummary::status)
                .containsOnly(PaymentSummary.Status.UNPAID);
    }

    @Test
    @DisplayName("NFR-7 ジョブを 2 回流しても、期限切れのイベントは 1 回だけ")
    void expiresOnlyOnce() {
        var orderId = created();
        openPage(orderId);

        clock.setInstant(DEADLINE);
        job.expire();
        job.expire();

        assertThat(expiredEvents(orderId)).isEqualTo(1);
    }

    @Test
    @DisplayName("AC-154 期限切れのジョブは 5 分以内の間隔で動くので、期限から 5 分のうちにキャンセルされる")
    void runsWithinFiveMinutes() throws Exception {
        var scheduled = PaymentExpiryJob.class.getMethod("expire").getAnnotation(Scheduled.class);

        var interval = Duration.parse(String.valueOf(beanFactory.getBeanExpressionResolver().evaluate(beanFactory.resolveEmbeddedValue(scheduled.fixedDelayString()), new BeanExpressionContext(beanFactory, null))));

        assertThat(interval).isLessThanOrEqualTo(Duration.ofMinutes(5));
    }

    // ============================== 補助 ==============================

    private UUID created() {
        var orderId = UUID.randomUUID();
        paymentApi.create(orderId, Money.of(6940), ORDERED_AT);
        return orderId;
    }

    private String openPage(UUID orderId) {
        var url = paymentApi.pageUrl(orderId).orElseThrow();
        return url.substring(url.lastIndexOf('/') + 1);
    }

    private PaymentSummary.Status statusOf(UUID orderId) {
        return paymentApi.find(List.of(orderId)).getFirst().status();
    }

    private long expiredEvents(UUID orderId) {
        return events.stream(PaymentExpired.class).filter(event -> event.orderId().equals(orderId)).count();
    }
}
