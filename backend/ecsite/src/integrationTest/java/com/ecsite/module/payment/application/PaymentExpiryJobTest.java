package com.ecsite.module.payment.application;

import com.ecsite.IntegrationTest;
import com.ecsite.MutableClock;
import com.ecsite.PaymentTestData;
import com.ecsite.TestClockConfig;
import com.ecsite.module.payment.api.PaymentApi;
import com.ecsite.module.payment.api.model.PaymentSummary;
import com.ecsite.module.payment.application.port.PaymentGateway;
import com.ecsite.module.payment.domain.payment.Payment;
import com.ecsite.module.payment.domain.payment.PaymentStatus;
import com.ecsite.module.payment.domain.payment.port.PaymentRepository;
import com.ecsite.module.payment.event.PaymentAuthorized;
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
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 支払の期限切れのジョブの統合テスト
 *
 * <p>ジョブはテストから呼ぶ。ほかのテストの支払も期限切れになるので、イベントは注文 ID で絞って確認する。
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
    PaymentQueryService queryService;

    @Autowired
    PaymentRepository paymentRepository;

    @Autowired
    PaymentApi paymentApi;

    @Autowired
    PaymentCommandService commandService;

    @Autowired
    PaymentProperties properties;

    @Autowired
    TestPaymentGateway gateway;

    @Autowired
    MutableClock clock;

    /**
     * 支払を進める補助
     */
    PaymentTestData payments;

    @Autowired
    ApplicationEvents events;

    @Autowired
    ConfigurableListableBeanFactory beanFactory;

    @BeforeEach
    void setUp() {
        payments = new PaymentTestData(paymentApi, gateway);
        clock.setInstant(ORDERED_AT);
        gateway.reset();
    }

    @Test
    @DisplayName("AC-153 期限を過ぎた未払いの支払は、支払画面を閉じて支払なしになり、期限切れのイベントが出る")
    void expiresUnpaidPastDeadline() {
        var orderId = payments.created(ORDERED_AT);
        var pageId = payments.openPage(orderId);

        clock.setInstant(DEADLINE);
        job.expire();

        assertThat(payments.statusOf(orderId)).isEqualTo(PaymentSummary.Status.NOT_PAID);
        assertThat(gateway.page(pageId).orElseThrow().status()).isEqualTo(MockPaymentGateway.PageStatus.CLOSED);
        assertThat(expiredEvents(orderId)).isEqualTo(1);
    }

    @Test
    @DisplayName("AC-153 支払画面を開かなかった支払も、期限を過ぎたら支払なしになる")
    void expiresWithoutPage() {
        var orderId = payments.created(ORDERED_AT);

        clock.setInstant(DEADLINE);
        job.expire();

        assertThat(payments.statusOf(orderId)).isEqualTo(PaymentSummary.Status.NOT_PAID);
        assertThat(expiredEvents(orderId)).isEqualTo(1);
    }

    @Test
    @DisplayName("期限の前の支払は、期限切れにしない")
    void keepsBeforeDeadline() {
        var orderId = payments.created(ORDERED_AT);
        payments.openPage(orderId);

        clock.setInstant(DEADLINE.minusSeconds(1));
        job.expire();

        assertThat(payments.statusOf(orderId)).isEqualTo(PaymentSummary.Status.UNPAID);
        assertThat(expiredEvents(orderId)).isZero();
    }

    @Test
    @DisplayName("AC-156 期限の直前に支払が済み、通知がまだ届いていなければ、期限切れにせず、ジョブが与信済みにする")
    void authorizesPaidBeforeNotification() {
        var orderId = payments.created(ORDERED_AT);
        gateway.markPaid(payments.openPage(orderId));

        clock.setInstant(DEADLINE);
        job.expire();

        assertThat(payments.statusOf(orderId)).isEqualTo(PaymentSummary.Status.AUTHORIZED);
        assertThat(expiredEvents(orderId)).isZero();
    }

    @Test
    @DisplayName("AC-409-1 通知が届いていなくても、期限切れのジョブが決済サービスで支払済みと確認した支払は与信済みになり、与信の成功のイベントが出る")
    void authorizesPaidWithoutNotification() {
        var orderId = payments.created(ORDERED_AT);
        var pageId = payments.openPage(orderId);
        gateway.markPaid(pageId);

        clock.setInstant(DEADLINE);
        job.expire();

        assertThat(payments.statusOf(orderId)).isEqualTo(PaymentSummary.Status.AUTHORIZED);
        assertThat(paymentOf(orderId).authorizationRef()).isEqualTo(gateway.authorizationRefOf(pageId));
        assertThat(authorizedEvents(orderId)).isEqualTo(1);
    }

    @Test
    @DisplayName("AC-409-2 与信の時刻は支払を済ませた時刻（ジョブの時刻ではない）で、与信期限はその時刻から計算する")
    void authorizedAtIsGatewayTime() {
        var orderId = payments.created(ORDERED_AT);
        var pageId = payments.openPage(orderId);
        var paidAt = DEADLINE.minusSeconds(60);
        clock.setInstant(paidAt);
        gateway.markPaid(pageId);
        var jobTime = DEADLINE.plus(Duration.ofHours(3));

        clock.setInstant(jobTime);
        job.expire();

        var payment = paymentOf(orderId);
        var term = properties.authorizationTerm();
        assertThat(payment.authorizedAt()).contains(paidAt);
        assertThat(payment.authorizationDeadline(term)).contains(term.deadlineOf(paidAt));
        assertThat(payment.authorizationDeadline(term).orElseThrow()).isNotEqualTo(term.deadlineOf(jobTime));
        assertThat(events.stream(PaymentAuthorized.class).filter(event -> event.orderId().equals(orderId)))
                .singleElement().satisfies(event -> assertThat(event.occurredAt()).isEqualTo(jobTime));
    }

    @Test
    @DisplayName("AC-409-3 前から与信の反映を待っていた支払（取得に失敗して待ったまま）も、次のジョブの実行で与信済みになる")
    void authorizesPaymentAlreadyAwaiting() {
        var orderId = payments.created(ORDERED_AT);
        var pageId = payments.openPage(orderId);
        gateway.markPaid(pageId);
        gateway.rejectAuthorizationFetch(pageId);
        clock.setInstant(DEADLINE);
        job.expire();
        assertThat(payments.statusOf(orderId)).isEqualTo(PaymentSummary.Status.UNPAID);
        assertThat(paymentOf(orderId).awaitingAuthorizationSince()).isPresent();
        gateway.reset();

        job.expire();

        assertThat(payments.statusOf(orderId)).isEqualTo(PaymentSummary.Status.AUTHORIZED);
        assertThat(authorizedEvents(orderId)).isEqualTo(1);
    }

    @Test
    @DisplayName("AC-409-4 ジョブで与信済みになった後に通知が届いても、状態と与信の時刻は変わらず、与信の成功のイベントは 1 回")
    void ignoresNotificationAfterJob() {
        var orderId = payments.created(ORDERED_AT);
        var pageId = payments.openPage(orderId);
        gateway.markPaid(pageId);
        clock.setInstant(DEADLINE);
        job.expire();
        var authorizedAt = paymentOf(orderId).authorizedAt();

        clock.setInstant(DEADLINE.plusSeconds(600));
        gateway.notifyAuthorized(pageId);

        assertThat(payments.statusOf(orderId)).isEqualTo(PaymentSummary.Status.AUTHORIZED);
        assertThat(paymentOf(orderId).authorizedAt()).isEqualTo(authorizedAt);
        assertThat(authorizedEvents(orderId)).isEqualTo(1);
    }

    @Test
    @DisplayName("AC-409-5 ジョブが待つ支払にした後、取得の前に通知が届いたら、与信の時刻は通知を受けた時刻のままで、与信の成功のイベントは 1 回")
    void keepsNotificationTimeWhenNotifiedBeforeFetch() {
        var orderId = payments.created(ORDERED_AT);
        var pageId = payments.openPage(orderId);
        gateway.markPaid(pageId);
        gateway.failNextAuthorizationFetches(1);
        clock.setInstant(DEADLINE);
        job.expire();
        assertThat(paymentOf(orderId).awaitingAuthorizationSince()).isPresent();
        var notifiedAt = DEADLINE.plusSeconds(120);
        clock.setInstant(notifiedAt);
        gateway.notifyAuthorized(pageId);

        clock.setInstant(DEADLINE.plus(Duration.ofHours(1)));
        job.expire();
        // 取得の後に、行ロックの時点で通知が先に届いていた形（取得した与信は反映しない）
        var reflected = commandService.authorizeFromGateway(paymentOf(orderId).id(),
                new PaymentGateway.Authorization("pi_other", DEADLINE.plus(Duration.ofHours(1))));

        assertThat(reflected).isFalse();
        assertThat(payments.statusOf(orderId)).isEqualTo(PaymentSummary.Status.AUTHORIZED);
        assertThat(paymentOf(orderId).authorizedAt()).contains(notifiedAt);
        assertThat(paymentOf(orderId).authorizationRef()).isEqualTo(gateway.authorizationRefOf(pageId));
        assertThat(authorizedEvents(orderId)).isEqualTo(1);
    }

    @Test
    @DisplayName("AC-409-6 与信を取得できない支払は、与信の反映を待つ支払のまま残り、残りの支払は与信済みになる。次のジョブの実行で再試行する")
    void keepsAwaitingWhenFetchFails() {
        var failing = payments.created(ORDERED_AT);
        var failingPage = payments.openPage(failing);
        var other = payments.created(ORDERED_AT.plusSeconds(1));
        var otherPage = payments.openPage(other);
        gateway.markPaid(failingPage);
        gateway.markPaid(otherPage);
        gateway.rejectAuthorizationFetch(failingPage);

        clock.setInstant(DEADLINE.plusSeconds(60));
        job.expire();

        assertThat(payments.statusOf(failing)).isEqualTo(PaymentSummary.Status.UNPAID);
        assertThat(paymentOf(failing).awaitingAuthorizationSince()).isPresent();
        assertThat(payments.statusOf(other)).isEqualTo(PaymentSummary.Status.AUTHORIZED);

        gateway.reset();
        job.expire();

        assertThat(payments.statusOf(failing)).isEqualTo(PaymentSummary.Status.AUTHORIZED);
    }

    @Test
    @DisplayName("AC-409-10 接続の失敗が 3 件続いたら、その回は取得を打ち切って 4 件とも未払いのまま残り、次のジョブの実行で 4 件とも与信済みになる")
    void stopsAfterConsecutiveUnavailableAndRetriesNextRun() {
        // 他のテストが残した、与信の反映を待つ支払を先に解消して、取得の呼び出しの回数に影響しないようにする
        clock.setInstant(DEADLINE);
        int maxRuns = queryService.findAwaitingAuthorization(Integer.MAX_VALUE).size() / 100 + 2;
        for (int run = 0; run < maxRuns && !queryService.findAwaitingAuthorization(1).isEmpty(); run++) {
            job.expire();
        }
        gateway.reset();
        clock.setInstant(ORDERED_AT);
        var orderIds = new ArrayList<UUID>();
        for (int i = 0; i < 4; i++) {
            var orderId = payments.created(ORDERED_AT);
            gateway.markPaid(payments.openPage(orderId));
            orderIds.add(orderId);
        }
        gateway.failNextAuthorizationFetches(3);

        clock.setInstant(DEADLINE);
        job.expire();

        assertThat(gateway.fetchAuthorizationCalls()).isEqualTo(3);
        assertThat(allAwaitingAuthorization(orderIds)).isTrue();
        assertThat(paymentApi.find(orderIds)).extracting(PaymentSummary::status)
                .containsOnly(PaymentSummary.Status.UNPAID);

        job.expire();

        assertThat(allAuthorized(orderIds)).isTrue();
    }

    @Test
    @DisplayName("NFR-409-3 与信の取得は、データベースのトランザクションの外で決済サービスを呼ぶ")
    void fetchesOutsideTransaction() {
        var orderId = payments.created(ORDERED_AT);
        gateway.markPaid(payments.openPage(orderId));
        var transactionActive = new CopyOnWriteArrayList<Boolean>();
        gateway.onFetchAuthorization(() -> transactionActive.add(TransactionSynchronizationManager.isActualTransactionActive()));

        clock.setInstant(DEADLINE);
        job.expire();

        assertThat(payments.statusOf(orderId)).isEqualTo(PaymentSummary.Status.AUTHORIZED);
        assertThat(transactionActive).isNotEmpty().containsOnly(false);
    }

    @Test
    @DisplayName("AC-311 与信の反映を待つ支払が 1 回の件数（100 件）を超えて滞留しても、それより新しい期限切れの支払を処理する")
    void expiresNewerPaymentBehindAwaitingPayments() {
        // 期限の前に支払が済み、与信を取得できない支払を、1 回の件数より多く作る
        var awaiting = new ArrayList<UUID>();
        for (int i = 0; i < 101; i++) {
            var orderId = payments.created(ORDERED_AT);
            var pageId = payments.openPage(orderId);
            gateway.markPaid(pageId);
            gateway.rejectAuthorizationFetch(pageId);
            awaiting.add(orderId);
        }
        try {
            clock.setInstant(DEADLINE);
            // 自分の支払が全て与信の反映を待つ支払になるまで実行する（他のテストが残した期限切れの支払も、同じ回で処理される）。
            // 1 回に少なくとも 1 件は処理するので、回数の上限は、未処理の支払の数に 1 を加えたもの
            int pending = queryService.findPastDeadline(Integer.MAX_VALUE).size();
            int maxRuns = pending + 1;
            for (int run = 0; run < maxRuns && !allAwaitingAuthorization(awaiting); run++) {
                job.expire();
            }
            assertThat(allAwaitingAuthorization(awaiting)).as("上限の回数で、自分の支払が全て処理される").isTrue();

            // それより新しい支払が、期限を過ぎる
            var newerOrderedAt = ORDERED_AT.plus(Duration.ofHours(1));
            var newer = payments.created(newerOrderedAt);
            clock.setInstant(newerOrderedAt.plus(Duration.ofMinutes(30)));
            job.expire();

            assertThat(payments.statusOf(newer)).isEqualTo(PaymentSummary.Status.NOT_PAID);
            assertThat(paymentApi.find(awaiting)).extracting(PaymentSummary::status)
                    .containsOnly(PaymentSummary.Status.UNPAID);
        } finally {
            // 与信の反映を待つ支払を残すと、100 件の上限で他のテストに影響する。取得できるようにして、自分の支払が全て与信済みになるまで実行する
            gateway.reset();
            int maxRuns = queryService.findAwaitingAuthorization(Integer.MAX_VALUE).size() / 100 + 2;
            for (int run = 0; run < maxRuns && !allAuthorized(awaiting); run++) {
                job.expire();
            }
        }
        assertThat(allAuthorized(awaiting)).as("待つ支払を残さない").isTrue();
    }

    @Test
    @DisplayName("NFR-7 ジョブを 2 回実行しても、期限切れのイベントは 1 回だけ")
    void expiresOnlyOnce() {
        var orderId = payments.created(ORDERED_AT);
        payments.openPage(orderId);

        clock.setInstant(DEADLINE);
        job.expire();
        job.expire();

        assertThat(expiredEvents(orderId)).isEqualTo(1);
    }

    @Test
    @DisplayName("AC-154 期限切れの確認の間隔の既定は 5 分以内")
    void runsWithinFiveMinutes() throws Exception {
        var scheduled = PaymentExpiryJob.class.getMethod("expire").getAnnotation(Scheduled.class);

        var interval = Duration.parse(String.valueOf(beanFactory.getBeanExpressionResolver().evaluate(beanFactory.resolveEmbeddedValue(scheduled.fixedDelayString()), new BeanExpressionContext(beanFactory, null))));

        assertThat(interval).isLessThanOrEqualTo(Duration.ofMinutes(5));
    }

    // ============================== 補助 ==============================

    /**
     * 指定の注文の支払が、全て与信の反映を待つ支払になっているか。
     */
    private boolean allAwaitingAuthorization(List<UUID> orderIds) {
        var found = paymentRepository.findByOrderIds(orderIds);
        return found.size() == orderIds.size()
                && found.stream().allMatch(payment -> payment.awaitingAuthorizationSince().isPresent());
    }

    /**
     * 指定の注文の支払が、全て与信済みか。
     */
    private boolean allAuthorized(List<UUID> orderIds) {
        var found = paymentRepository.findByOrderIds(orderIds);
        return found.size() == orderIds.size()
                && found.stream().allMatch(payment -> payment.status() == PaymentStatus.AUTHORIZED);
    }

    private Payment paymentOf(UUID orderId) {
        return paymentRepository.findByOrderId(orderId).orElseThrow();
    }

    private long authorizedEvents(UUID orderId) {
        return events.stream(PaymentAuthorized.class).filter(event -> event.orderId().equals(orderId)).count();
    }

    private long expiredEvents(UUID orderId) {
        return events.stream(PaymentExpired.class).filter(event -> event.orderId().equals(orderId)).count();
    }
}
