package com.ecsite.module.payment.application.job;

import com.ecsite.IntegrationTest;
import com.ecsite.MutableClock;
import com.ecsite.PaymentTestData;
import com.ecsite.module.payment.api.PaymentApi;
import com.ecsite.module.payment.api.model.PaymentSummary;
import com.ecsite.module.payment.application.PaymentCommandService;
import com.ecsite.module.payment.application.PaymentProperties;
import com.ecsite.module.payment.application.PaymentQueryService;
import com.ecsite.module.payment.application.model.Authorization;
import com.ecsite.module.payment.domain.payment.Payment;
import com.ecsite.module.payment.domain.payment.PaymentStatus;
import com.ecsite.module.payment.domain.payment.port.PaymentRepository;
import com.ecsite.module.payment.event.PaymentAuthorized;
import com.ecsite.module.payment.event.PaymentExpired;
import com.ecsite.module.payment.infrastructure.mock.MockPaymentGateway;
import com.ecsite.module.payment.infrastructure.mock.TestPaymentGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanExpressionContext;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 支払の期限切れのジョブの統合テスト
 *
 * <p>ジョブはテストから呼ぶ。ほかのテストの支払も期限切れになるので、イベントは注文 ID で絞って確認する。
 * 与信の反映を待つ支払が 1 回の件数を超えてたまる場合は、検索のテスト（{@code JooqPaymentRepositoryTest}）で確認する。
 */
@IntegrationTest
@RecordApplicationEvents
class PaymentExpiryJobTest {

    // ============================== 定数 ==============================

    /**
     * 注文日時
     *
     * <p>日本時間の 2026-10-08 00:30。
     */
    private static final Instant ORDERED_AT = Instant.parse("2026-10-07T15:30:00Z");

    /**
     * 支払の期限（注文日時 ＋ 30 分）
     */
    private static final Instant DEADLINE = Instant.parse("2026-10-07T16:00:00Z");

    /**
     * 決済サービスで支払を済ませた時刻（期限の 1 分前）
     */
    private static final Instant PAID_AT = Instant.parse("2026-10-07T15:59:00Z");

    /**
     * 支払を済ませた後にジョブが動く時刻（期限の 3 時間後）
     */
    private static final Instant JOB_TIME = Instant.parse("2026-10-07T19:00:00Z");

    /**
     * 通知を受けた時刻（期限の 2 分後）
     */
    private static final Instant NOTIFIED_AT = Instant.parse("2026-10-07T16:02:00Z");

    /**
     * 与信の有効期間（既定）
     */
    private static final Duration AUTHORIZATION_TERM = Duration.ofDays(7);

    /**
     * 期限切れの確認の間隔の上限
     */
    private static final Duration MAX_INTERVAL = Duration.ofMinutes(5);

    /**
     * 与信の取得を打ち切る、接続の失敗の連続の件数
     */
    private static final int UNAVAILABLE_LIMIT = 3;

    // ============================== Bean ==============================

    /**
     * 確認するジョブ
     */
    @Autowired
    PaymentExpiryJob job;

    /**
     * 支払の参照のサービス
     */
    @Autowired
    PaymentQueryService queryService;

    /**
     * 支払の状態と時刻を読むリポジトリ
     */
    @Autowired
    PaymentRepository paymentRepository;

    /**
     * 決済の API
     */
    @Autowired
    PaymentApi paymentApi;

    /**
     * 支払の更新のサービス
     */
    @Autowired
    PaymentCommandService commandService;

    /**
     * 決済の設定
     */
    @Autowired
    PaymentProperties properties;

    /**
     * テスト用の決済サービス
     */
    @Autowired
    TestPaymentGateway gateway;

    /**
     * テストの時計
     */
    @Autowired
    MutableClock clock;

    /**
     * 発行されたイベント
     */
    @Autowired
    ApplicationEvents events;

    /**
     * スケジュールの設定を解決する Bean の工場
     */
    @Autowired
    ConfigurableListableBeanFactory beanFactory;

    /**
     * ジョブを繰り返す補助に渡す Spring のコンテキスト
     */
    @Autowired
    ApplicationContext context;

    /**
     * 支払を進める補助
     */
    PaymentTestData payments;

    /**
     * 決済サービスで支払を済ませた（通知は届いていない）支払の、注文 ID ごとの支払画面 ID
     */
    final Map<UUID, String> pageIds = new HashMap<>();

    @BeforeEach
    void setUp() {
        payments = new PaymentTestData(paymentApi, gateway);
        clock.setInstant(ORDERED_AT);
    }

    // ============================== テスト ==============================

    @Nested
    @DisplayName("期限を過ぎた未払いの支払")
    class Expiry {

        @Test
        @DisplayName("AC-153 期限を過ぎた未払いの支払は、支払なしになる")
        void becomesNotPaid() {
            // 準備：画面を開いた未払いの支払が、期限を過ぎる
            var orderId = payments.created(ORDERED_AT);
            payments.openPage(orderId);
            clock.setInstant(DEADLINE);

            // 操作：
            expireUntil(() -> settled(orderId));

            // 確認：
            assertThat(payments.statusOf(orderId)).isEqualTo(PaymentSummary.Status.NOT_PAID);
        }

        @Test
        @DisplayName("AC-153 期限を過ぎた未払いの支払は、支払画面を閉じる")
        void closesPage() {
            // 準備：画面を開いた未払いの支払が、期限を過ぎる
            var orderId = payments.created(ORDERED_AT);
            var pageId = payments.openPage(orderId);
            clock.setInstant(DEADLINE);

            // 操作：
            expireUntil(() -> settled(orderId));

            // 確認：
            assertThat(gateway.page(pageId).orElseThrow().status()).isEqualTo(MockPaymentGateway.PageStatus.CLOSED);
        }

        @Test
        @DisplayName("AC-153 期限を過ぎた未払いの支払は、期限切れのイベントを 1 件発行する")
        void publishesExpiredEvent() {
            // 準備：画面を開いた未払いの支払が、期限を過ぎる
            var orderId = payments.created(ORDERED_AT);
            payments.openPage(orderId);
            clock.setInstant(DEADLINE);

            // 操作：
            expireUntil(() -> settled(orderId));

            // 確認：
            assertThat(expiredEvents(orderId)).isEqualTo(1);
        }

        @Test
        @DisplayName("AC-153 支払画面を開かなかった支払も、期限を過ぎたら支払なしになり、期限切れのイベントを発行する")
        void expiresWithoutPage() {
            // 準備：画面を開かなかった支払が、期限を過ぎる
            var orderId = payments.created(ORDERED_AT);
            clock.setInstant(DEADLINE);

            // 操作：
            expireUntil(() -> settled(orderId));

            // 確認：
            assertThat(payments.statusOf(orderId)).isEqualTo(PaymentSummary.Status.NOT_PAID);
            assertThat(expiredEvents(orderId)).isEqualTo(1);
        }

        @Test
        @DisplayName("期限の前の支払は、期限切れにしない")
        void keepsBeforeDeadline() {
            // 準備：期限の 1 秒前
            var orderId = payments.created(ORDERED_AT);
            payments.openPage(orderId);
            clock.setInstant(DEADLINE.minusSeconds(1));

            // 操作：期限を過ぎた支払がなくなるまでジョブを動かす
            expireUntil(() -> queryService.findPastDeadline(1).isEmpty());

            // 確認：未払いのままで、イベントもない
            assertThat(payments.statusOf(orderId)).isEqualTo(PaymentSummary.Status.UNPAID);
            assertThat(expiredEvents(orderId)).isZero();
        }

        @Test
        @DisplayName("NFR-7 ジョブを 2 回実行しても、期限切れのイベントは 1 回だけ")
        void expiresOnlyOnce() {
            // 準備：期限切れにした支払
            var orderId = payments.created(ORDERED_AT);
            payments.openPage(orderId);
            clock.setInstant(DEADLINE);
            expireUntil(() -> settled(orderId));

            // 操作：もう一度ジョブを動かす
            job.expire();

            // 確認：
            assertThat(expiredEvents(orderId)).isEqualTo(1);
        }

        @Test
        @DisplayName("AC-154 期限切れの確認の間隔の既定は 5 分以内")
        void runsWithinFiveMinutes() throws Exception {
            // 準備：ジョブのスケジュールの設定
            var scheduled = PaymentExpiryJob.class.getMethod("expire").getAnnotation(Scheduled.class);

            // 操作：設定の値を解決する
            var interval = Duration.parse(String.valueOf(beanFactory.getBeanExpressionResolver().evaluate(
                    beanFactory.resolveEmbeddedValue(scheduled.fixedDelayString()), new BeanExpressionContext(beanFactory, null))));

            // 確認：
            assertThat(interval).isLessThanOrEqualTo(MAX_INTERVAL);
        }
    }

    @Nested
    @DisplayName("期限の前に支払が済み、通知が届いていない支払")
    class PaidWithoutNotification {

        @Test
        @DisplayName("AC-156 期限の直前に支払が済み、通知がまだ届いていなければ、期限切れにしない")
        void doesNotExpire() {
            // 準備：支払を済ませ、通知が届いていない支払が、期限を過ぎる
            var orderId = paidWithoutNotification();
            clock.setInstant(DEADLINE);

            // 操作：
            expireUntil(() -> settled(orderId));

            // 確認：期限切れのイベントがない
            assertThat(expiredEvents(orderId)).isZero();
        }

        @Test
        @DisplayName("AC-156、AC-409-1 期限切れのジョブが決済サービスで支払済みと確認した支払は、与信済みになり、与信の参照を持つ")
        void authorizes() {
            // 準備：支払を済ませ、通知が届いていない支払が、期限を過ぎる
            var orderId = paidWithoutNotification();
            clock.setInstant(DEADLINE);

            // 操作：
            expireUntil(() -> settled(orderId));

            // 確認：決済サービスの与信の参照を持つ
            assertThat(payments.statusOf(orderId)).isEqualTo(PaymentSummary.Status.AUTHORIZED);
            assertThat(paymentOf(orderId).authorizationRef()).isEqualTo(gateway.authorizationRefOf(pageIds.get(orderId)));
        }

        @Test
        @DisplayName("AC-409-1 期限切れのジョブで与信済みになった支払は、与信の成功のイベントを 1 件発行する")
        void publishesAuthorizedEvent() {
            // 準備：支払を済ませ、通知が届いていない支払が、期限を過ぎる
            var orderId = paidWithoutNotification();
            clock.setInstant(DEADLINE);

            // 操作：
            expireUntil(() -> settled(orderId));

            // 確認：
            assertThat(authorizedEvents(orderId)).isEqualTo(1);
        }

        @Test
        @DisplayName("AC-409-2 与信の時刻は、ジョブの時刻ではなく、支払を済ませた時刻")
        void authorizedAtIsPaidAt() {
            // 準備：期限の 1 分前に支払い、期限の 3 時間後にジョブが動く
            var orderId = paidAt(PAID_AT);
            clock.setInstant(JOB_TIME);

            // 操作：
            expireUntil(() -> settled(orderId));

            // 確認：
            assertThat(paymentOf(orderId).authorizedAt()).contains(PAID_AT);
        }

        @Test
        @DisplayName("AC-409-2 与信期限は、支払を済ませた時刻から計算する")
        void deadlineFromPaidAt() {
            // 準備：期限の 1 分前に支払い、期限の 3 時間後にジョブが動く
            var orderId = paidAt(PAID_AT);
            clock.setInstant(JOB_TIME);

            // 操作：
            expireUntil(() -> settled(orderId));

            // 確認：支払を済ませた時刻の 7 日後で、ジョブの時刻の 7 日後ではない
            var deadline = paymentOf(orderId).authorizationDeadline(properties.authorizationTerm()).orElseThrow();
            assertThat(deadline).isEqualTo(PAID_AT.plus(AUTHORIZATION_TERM));
            assertThat(deadline).isNotEqualTo(JOB_TIME.plus(AUTHORIZATION_TERM));
        }

        @Test
        @DisplayName("AC-409-1 期限切れのジョブで与信済みにしたときの、与信の成功のイベントの時刻は、ジョブの時刻")
        void eventOccursAtJobTime() {
            // 準備：期限の 1 分前に支払い、期限の 3 時間後にジョブが動く
            var orderId = paidAt(PAID_AT);
            clock.setInstant(JOB_TIME);

            // 操作：
            expireUntil(() -> settled(orderId));

            // 確認：
            assertThat(events.stream(PaymentAuthorized.class).filter(event -> event.orderId().equals(orderId)))
                    .singleElement().satisfies(event -> assertThat(event.occurredAt()).isEqualTo(JOB_TIME));
        }

        @Test
        @DisplayName("NFR-409-3 与信の取得は、データベースのトランザクションの外で決済サービスを呼ぶ")
        void fetchesOutsideTransaction() {
            // 準備：与信の取得の時点で、トランザクションがあるかを記録する
            var orderId = paidWithoutNotification();
            var transactionActive = new CopyOnWriteArrayList<Boolean>();
            gateway.onFetchAuthorization(() -> transactionActive.add(TransactionSynchronizationManager.isActualTransactionActive()));
            clock.setInstant(DEADLINE);

            // 操作：
            expireUntil(() -> settled(orderId));

            // 確認：与信済みになり、取得は全てトランザクションの外
            assertThat(payments.statusOf(orderId)).isEqualTo(PaymentSummary.Status.AUTHORIZED);
            assertThat(transactionActive).isNotEmpty().containsOnly(false);
        }
    }

    @Nested
    @DisplayName("通知とジョブの重なり")
    class NotificationAndJob {

        @Test
        @DisplayName("AC-409-4 ジョブで与信済みになった後に通知が届いても、状態と与信の時刻は変わらない")
        void keepsStateWhenNotifiedAfterJob() {
            // 準備：ジョブで与信済みにした
            var orderId = paidWithoutNotification();
            clock.setInstant(DEADLINE);
            expireUntil(() -> settled(orderId));
            var authorizedAt = paymentOf(orderId).authorizedAt();

            // 操作：10 分後に通知が届く
            clock.setInstant(DEADLINE.plusSeconds(600));
            gateway.notifyAuthorized(pageIds.get(orderId));

            // 確認：
            assertThat(payments.statusOf(orderId)).isEqualTo(PaymentSummary.Status.AUTHORIZED);
            assertThat(paymentOf(orderId).authorizedAt()).isEqualTo(authorizedAt);
        }

        @Test
        @DisplayName("AC-409-4 ジョブで与信済みになった後に通知が届いても、与信の成功のイベントは 1 回")
        void publishesOnceWhenNotifiedAfterJob() {
            // 準備：ジョブで与信済みにした
            var orderId = paidWithoutNotification();
            clock.setInstant(DEADLINE);
            expireUntil(() -> settled(orderId));

            // 操作：10 分後に通知が届く
            clock.setInstant(DEADLINE.plusSeconds(600));
            gateway.notifyAuthorized(pageIds.get(orderId));

            // 確認：
            assertThat(authorizedEvents(orderId)).isEqualTo(1);
        }

        @Test
        @DisplayName("AC-409-5 ジョブが待つ支払にした後、取得の前に通知が届いたら、取得した与信は反映せず、与信の時刻は通知を受けた時刻のまま")
        void keepsNotificationTimeWhenNotifiedBeforeFetch() {
            // 準備：通知で与信済みになった、待つ支払
            var orderId = notifiedWhileAwaiting();

            // 操作：次のジョブの後、行ロックの時点で通知が先に届いていた形で、取得した与信を反映する
            clock.setInstant(DEADLINE.plus(Duration.ofHours(1)));
            job.expire();
            var reflected = commandService.authorizeFromGateway(paymentOf(orderId).id(),
                    new Authorization("pi_other", DEADLINE.plus(Duration.ofHours(1))));

            // 確認：反映せず、与信の時刻と参照は通知のもの
            assertThat(reflected).isFalse();
            assertThat(payments.statusOf(orderId)).isEqualTo(PaymentSummary.Status.AUTHORIZED);
            assertThat(paymentOf(orderId).authorizedAt()).contains(NOTIFIED_AT);
            assertThat(paymentOf(orderId).authorizationRef()).isEqualTo(gateway.authorizationRefOf(pageIds.get(orderId)));
        }

        @Test
        @DisplayName("AC-409-5 ジョブが待つ支払にした後、取得の前に通知が届いたら、与信の成功のイベントは 1 回")
        void publishesOnceWhenNotifiedBeforeFetch() {
            // 準備：通知で与信済みになった、待つ支払
            var orderId = notifiedWhileAwaiting();

            // 操作：次のジョブを動かす
            clock.setInstant(DEADLINE.plus(Duration.ofHours(1)));
            job.expire();

            // 確認：
            assertThat(authorizedEvents(orderId)).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("与信を取得できない支払")
    class FetchFailure {

        @Test
        @DisplayName("AC-409-3 前から与信の反映を待っていた支払（取得に失敗して待ったまま）も、次のジョブの実行で与信済みになる")
        void authorizesPaymentAlreadyAwaiting() {
            // 準備：取得に失敗して待つ支払
            var orderId = awaitingAfterRejectedFetch();

            // 操作：取得できるようにして、次のジョブを動かす
            gateway.reset();
            job.expire();

            // 確認：
            assertThat(payments.statusOf(orderId)).isEqualTo(PaymentSummary.Status.AUTHORIZED);
        }

        @Test
        @DisplayName("AC-409-3 前から与信の反映を待っていた支払が与信済みになると、与信の成功のイベントを 1 件発行する")
        void publishesEventForPaymentAlreadyAwaiting() {
            // 準備：取得に失敗して待つ支払
            var orderId = awaitingAfterRejectedFetch();

            // 操作：取得できるようにして、次のジョブを動かす
            gateway.reset();
            job.expire();

            // 確認：
            assertThat(authorizedEvents(orderId)).isEqualTo(1);
        }

        @Test
        @DisplayName("AC-409-6 与信を取得できない支払は、未払いで、与信の反映を待つ支払のまま残る")
        void keepsAwaitingWhenFetchFails() {
            // 準備：与信を取得できない支払が、期限を過ぎる
            var failing = paidWithRejectedFetch();
            clock.setInstant(DEADLINE.plusSeconds(60));

            // 操作：
            expireUntil(() -> processed(failing));

            // 確認：
            assertThat(payments.statusOf(failing)).isEqualTo(PaymentSummary.Status.UNPAID);
            assertThat(paymentOf(failing).awaitingAuthorizationSince()).isPresent();
        }

        @Test
        @DisplayName("AC-409-6 与信を取得できない支払があっても、残りの支払は与信済みになる")
        void authorizesOthersWhenFetchFails() {
            // 準備：取得できない支払と、取得できる支払
            var failing = paidWithRejectedFetch();
            var other = paidWithoutNotification(ORDERED_AT.plusSeconds(1));
            clock.setInstant(DEADLINE.plusSeconds(60));

            // 操作：
            expireUntil(() -> processed(failing) && settled(other));

            // 確認：
            assertThat(payments.statusOf(other)).isEqualTo(PaymentSummary.Status.AUTHORIZED);
        }

        @Test
        @DisplayName("AC-409-6 与信を取得できなかった支払は、次のジョブの実行で再試行して与信済みになる")
        void retriesNextRun() {
            // 準備：取得できずに待つ支払
            var failing = paidWithRejectedFetch();
            clock.setInstant(DEADLINE.plusSeconds(60));
            expireUntil(() -> processed(failing));

            // 操作：取得できるようにして、次のジョブを動かす
            gateway.reset();
            job.expire();

            // 確認：
            assertThat(payments.statusOf(failing)).isEqualTo(PaymentSummary.Status.AUTHORIZED);
        }

        @Test
        @DisplayName("AC-409-10 接続の失敗が 3 件続いたら、その回は取得を打ち切り、4 件とも未払いで待つ支払のまま残る")
        void stopsAfterConsecutiveUnavailable() {
            // 準備：支払済みの 4 件のうち、初めの 3 件の取得で接続できない
            var orderIds = fourPaidWithUnavailableFetches();

            // 操作：
            job.expire();

            // 確認：取得の呼び出しは 3 回で、4 件とも待つ支払
            assertThat(gateway.fetchAuthorizationCalls()).isEqualTo(UNAVAILABLE_LIMIT);
            assertThat(allAwaitingAuthorization(orderIds)).isTrue();
            assertThat(paymentApi.find(orderIds)).extracting(PaymentSummary::status)
                    .containsOnly(PaymentSummary.Status.UNPAID);
        }

        @Test
        @DisplayName("AC-409-10 取得を打ち切った支払は、次のジョブの実行で 4 件とも与信済みになる")
        void retriesAfterStop() {
            // 準備：取得を打ち切った回の後
            var orderIds = fourPaidWithUnavailableFetches();
            job.expire();

            // 操作：次のジョブを動かす
            job.expire();

            // 確認：
            assertThat(allAuthorized(orderIds)).isTrue();
        }
    }

    // ============================== 確認の補助 ==============================

    /**
     * 指定の注文の支払が、未払いでなくなったか（支払なしか与信済みになったか）を返す。
     *
     * @param orderId 注文 ID
     * @return 未払いでなければ true
     */
    private boolean settled(UUID orderId) {
        return paymentOf(orderId).status() != PaymentStatus.UNPAID;
    }

    /**
     * 指定の注文の支払を、期限切れの処理が扱ったか（未払いでなくなったか、与信の反映を待つ支払になったか）を返す。
     *
     * @param orderId 注文 ID
     * @return 扱っていれば true
     */
    private boolean processed(UUID orderId) {
        return settled(orderId) || paymentOf(orderId).awaitingAuthorizationSince().isPresent();
    }

    /**
     * 指定の注文の支払が、全て与信の反映を待つ支払になっているかを返す。
     *
     * @param orderIds 注文 ID の一覧
     * @return 全て待っていれば true
     */
    private boolean allAwaitingAuthorization(List<UUID> orderIds) {
        var found = paymentRepository.findByOrderIds(orderIds);
        return found.size() == orderIds.size()
                && found.stream().allMatch(payment -> payment.awaitingAuthorizationSince().isPresent());
    }

    /**
     * 指定の注文の支払が、全て与信済みかを返す。
     *
     * @param orderIds 注文 ID の一覧
     * @return 全て与信済みなら true
     */
    private boolean allAuthorized(List<UUID> orderIds) {
        var found = paymentRepository.findByOrderIds(orderIds);
        return found.size() == orderIds.size()
                && found.stream().allMatch(payment -> payment.status() == PaymentStatus.AUTHORIZED);
    }

    /**
     * 注文の支払を読む。
     *
     * @param orderId 注文 ID
     * @return 支払
     */
    private Payment paymentOf(UUID orderId) {
        return paymentRepository.findByOrderId(orderId).orElseThrow();
    }

    /**
     * 注文の与信の成功のイベントの数を返す。
     *
     * @param orderId 注文 ID
     * @return イベントの数
     */
    private long authorizedEvents(UUID orderId) {
        return events.stream(PaymentAuthorized.class).filter(event -> event.orderId().equals(orderId)).count();
    }

    /**
     * 注文の期限切れのイベントの数を返す。
     *
     * @param orderId 注文 ID
     * @return イベントの数
     */
    private long expiredEvents(UUID orderId) {
        return events.stream(PaymentExpired.class).filter(event -> event.orderId().equals(orderId)).count();
    }

    // ============================== 準備の補助 ==============================

    /**
     * テストの支払が処理されるまで、ジョブを繰り返し実行する（上限つき）。
     *
     * @param done テストの支払が処理されたか
     */
    private void expireUntil(BooleanSupplier done) {
        PaymentJobs.expireUntil(context, done);
    }

    /**
     * 注文日時に作り、画面を開いて決済サービスで支払を済ませた（通知は届いていない）支払を作る。
     *
     * @return 注文 ID
     */
    private UUID paidWithoutNotification() {
        return paidWithoutNotification(ORDERED_AT);
    }

    /**
     * 画面を開いて決済サービスで支払を済ませた（通知は届いていない）支払を作る。
     *
     * @param orderedAt 注文日時
     * @return 注文 ID
     */
    private UUID paidWithoutNotification(Instant orderedAt) {
        var orderId = payments.created(orderedAt);
        var pageId = payments.openPage(orderId);
        pageIds.put(orderId, pageId);
        gateway.markPaid(pageId);
        return orderId;
    }

    /**
     * 指定の時刻に決済サービスで支払を済ませた（通知は届いていない）支払を作る。<br />
     * 時計は支払った時刻になる。
     *
     * @param paidAt 支払った時刻
     * @return 注文 ID
     */
    private UUID paidAt(Instant paidAt) {
        var orderId = payments.created(ORDERED_AT);
        var pageId = payments.openPage(orderId);
        pageIds.put(orderId, pageId);
        clock.setInstant(paidAt);
        gateway.markPaid(pageId);
        return orderId;
    }

    /**
     * 決済サービスで支払を済ませたが、与信を取得できない支払を作る。
     *
     * @return 注文 ID
     */
    private UUID paidWithRejectedFetch() {
        var orderId = paidWithoutNotification();
        gateway.rejectAuthorizationFetch(pageIds.get(orderId));
        return orderId;
    }

    /**
     * 期限を過ぎてジョブが与信を取得できず、与信の反映を待つ未払いの支払を作る。
     *
     * @return 注文 ID
     */
    private UUID awaitingAfterRejectedFetch() {
        var orderId = paidWithRejectedFetch();
        clock.setInstant(DEADLINE);
        expireUntil(() -> processed(orderId));
        assertThat(payments.statusOf(orderId)).isEqualTo(PaymentSummary.Status.UNPAID);
        assertThat(paymentOf(orderId).awaitingAuthorizationSince()).isPresent();
        return orderId;
    }

    /**
     * ジョブが待つ支払にした後、取得の前に通知が届いて与信済みになった支払を作る。<br />
     * 通知を受けた時刻は {@link #NOTIFIED_AT}。
     *
     * @return 注文 ID
     */
    private UUID notifiedWhileAwaiting() {
        var orderId = paidWithoutNotification();
        gateway.failNextAuthorizationFetches(1);
        clock.setInstant(DEADLINE);
        expireUntil(() -> processed(orderId));
        assertThat(paymentOf(orderId).awaitingAuthorizationSince()).isPresent();
        clock.setInstant(NOTIFIED_AT);
        gateway.notifyAuthorized(pageIds.get(orderId));
        return orderId;
    }

    /**
     * 決済サービスで支払を済ませた 4 件の支払を作り、次の与信の取得を 3 件続けて接続の失敗にする。<br />
     * 時計は期限になる。
     *
     * <p>他のテストが残した、与信の反映を待つ支払を先に解消して、取得の呼び出しの回数に影響しないようにする。
     *
     * @return 注文 ID の一覧
     */
    private List<UUID> fourPaidWithUnavailableFetches() {
        clock.setInstant(DEADLINE);
        expireUntil(() -> queryService.findAwaitingAuthorization(1).isEmpty());
        gateway.reset();
        clock.setInstant(ORDERED_AT);
        var orderIds = new ArrayList<UUID>();
        for (int i = 0; i < 4; i++) {
            orderIds.add(paidWithoutNotification());
        }
        gateway.failNextAuthorizationFetches(UNAVAILABLE_LIMIT);
        clock.setInstant(DEADLINE);
        return orderIds;
    }
}
