package com.ecsite.module.payment.application;

import com.ecsite.module.payment.api.PaymentApi;
import com.ecsite.module.payment.api.model.PaymentSummary;
import com.ecsite.module.payment.domain.payment.port.PaymentRepository;
import com.ecsite.module.payment.infrastructure.mock.MockPaymentGateway;
import com.ecsite.module.payment.infrastructure.mock.TestPaymentGateway;
import com.ecsite.support.MutableClock;
import com.ecsite.support.async.AsyncEvents;
import com.ecsite.support.config.IntegrationTest;
import com.ecsite.support.data.PaymentTestData;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

import static com.ecsite.support.async.AsyncEvents.TIMEOUT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * 注文の取消で、開いた支払画面を閉じることの統合テスト
 *
 * <p>画面を閉じる処理はコミットの後に非同期で行われるので、結果を待つ。
 */
@IntegrationTest
class PaymentCancelPageCloseTest {

    // ============================== 定数 ==============================

    /**
     * 注文日時
     *
     * <p>日本時間の 2026-10-08 00:30。
     */
    private static final Instant ORDERED_AT = Instant.parse("2026-10-07T15:30:00Z");

    /**
     * 再発行のジョブが対象にする、発行からの経過時間（5 分）を超える長さ
     */
    private static final Duration PAST_RESUBMIT_AGE = Duration.ofMinutes(6);

    // ============================== Bean ==============================

    /**
     * 確認する決済の API
     */
    @Autowired
    PaymentApi paymentApi;

    /**
     * 支払のバージョンを読むリポジトリ
     */
    @Autowired
    PaymentRepository paymentRepository;

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
     * 非同期のイベントの処理を待つ補助
     */
    @Autowired
    AsyncEvents asyncEvents;

    /**
     * 支払 ID を読む DB
     */
    @Autowired
    DSLContext dsl;

    /**
     * 支払の操作を、呼んだ側のトランザクションに加えるためのテンプレート
     */
    @Autowired
    TransactionTemplate transactionTemplate;

    /**
     * 支払を進める補助
     */
    PaymentTestData payments;

    @BeforeEach
    void setUp() {
        gateway.reset();
        payments = new PaymentTestData(paymentApi, gateway, paymentRepository, transactionTemplate);
        clock.setInstant(ORDERED_AT);
    }

    // ============================== テスト ==============================

    @Test
    @DisplayName("DC-604-1 支払画面を開いた未払いの支払を取り消すと、コミットの後に決済サービスで画面が閉じられ、支払は支払なしのまま")
    void closesPageAfterCommit() {
        // 準備：支払画面を開いた未払いの支払
        var orderId = payments.created(ORDERED_AT);
        var pageId = payments.openPage(orderId);
        var pageInTransaction = new AtomicReference<MockPaymentGateway.PageStatus>();

        // 操作：注文のキャンセルと同じく、トランザクションの中で取り消す
        transactionTemplate.executeWithoutResult(status -> {
            paymentApi.cancel(orderId);
            pageInTransaction.set(gateway.page(pageId).orElseThrow().status());
        });

        // 確認：コミットの前は開いたままで、コミットの後に閉じ、支払は支払なしのまま
        asyncEvents.awaitPublishedThenHandled(pageClose(orderId));
        assertThat(pageInTransaction.get()).isEqualTo(MockPaymentGateway.PageStatus.OPEN);
        assertThat(gateway.page(pageId).orElseThrow().status()).isEqualTo(MockPaymentGateway.PageStatus.CLOSED);
        assertThat(payments.statusOf(orderId)).isEqualTo(PaymentSummary.Status.NOT_PAID);
    }

    @Test
    @DisplayName("DC-604-3 支払画面で支払が済んでいた支払は、通知が届かなくても、与信の取消待ちを経て与信の取消済みになり、その与信が取り消される")
    void voidsAuthorizationPaidBeforeClose() {
        // 準備：画面で支払が済んだが、与信の成功の通知が届かない未払いの支払
        var orderId = payments.created(ORDERED_AT);
        var pageId = payments.openPage(orderId);
        gateway.markPaid(pageId);

        // 操作：注文の支払を取り消す
        cancel(orderId);

        // 確認：与信の取消の依頼が発行され、与信の取消済みになり、その与信が取り消された
        awaitStatus(orderId, PaymentSummary.Status.VOIDED);
        asyncEvents.awaitPublishedThenHandled(PaymentRequests.voidAuthorization(dsl, orderId));
        assertThat(gateway.voided()).containsExactly(gateway.authorizationRefOf(pageId));
    }

    @Test
    @DisplayName("DC-604-4 決済サービスに接続できず画面を閉じられないとき、支払は支払なしのまま残り、再発行で画面が閉じられる")
    void retriesFailedClose() {
        // 準備：次の画面を閉じる操作が、接続の失敗になる
        var orderId = payments.created(ORDERED_AT);
        var pageId = payments.openPage(orderId);
        gateway.failNextCloses(1);

        // 操作：注文の支払を取り消す
        cancel(orderId);

        // 確認：閉じる依頼の処理が失敗し、支払は支払なしのままで、画面は開いたまま
        asyncEvents.awaitFailed(pageClose(orderId));
        assertThat(payments.statusOf(orderId)).isEqualTo(PaymentSummary.Status.NOT_PAID);
        assertThat(gateway.page(pageId).orElseThrow().status()).isEqualTo(MockPaymentGateway.PageStatus.OPEN);

        // 操作：再発行のジョブと同じく、発行から 5 分を過ぎたこのテストのイベントを再発行する
        clock.advance(PAST_RESUBMIT_AGE);
        asyncEvents.resubmit(pageClose(orderId));

        // 確認：画面が閉じられ、支払は支払なしのまま
        asyncEvents.awaitHandled(pageClose(orderId));
        assertThat(gateway.page(pageId).orElseThrow().status()).isEqualTo(MockPaymentGateway.PageStatus.CLOSED);
        assertThat(payments.statusOf(orderId)).isEqualTo(PaymentSummary.Status.NOT_PAID);
    }

    @Test
    @DisplayName("DC-604-5 支払が済んでいた支払で与信を取得できないとき、支払は支払なしのまま残り、再発行で与信を取り消して与信の取消済みになる")
    void retriesFailedAuthorizationFetch() {
        // 準備：画面で支払が済んだが通知が届かず、次の与信の取得が接続の失敗になる支払
        var orderId = payments.created(ORDERED_AT);
        var pageId = payments.openPage(orderId);
        gateway.markPaid(pageId);
        gateway.failNextAuthorizationFetches(1);

        // 操作：注文の支払を取り消す
        cancel(orderId);

        // 確認：閉じる依頼の処理が失敗し、支払は支払なしのままで、与信は取り消されていない
        asyncEvents.awaitFailed(pageClose(orderId));
        assertThat(payments.statusOf(orderId)).isEqualTo(PaymentSummary.Status.NOT_PAID);
        assertThat(gateway.voided()).isEmpty();

        // 操作：再発行のジョブと同じく、発行から 5 分を過ぎたこのテストのイベントを再発行する
        clock.advance(PAST_RESUBMIT_AGE);
        asyncEvents.resubmit(pageClose(orderId));

        // 確認：与信を取得して取り消し、与信の取消済みになる
        awaitStatus(orderId, PaymentSummary.Status.VOIDED);
        assertThat(gateway.voided()).containsExactly(gateway.authorizationRefOf(pageId));
    }

    @Test
    @DisplayName("DC-604-6 リスナーが受け取る前に与信の成功の通知が届いて与信の取消待ちになっていれば、決済サービスの画面を閉じる操作も与信の取得も呼ばない")
    void doesNotCallGatewayWhenNoLongerNotPaid() {
        // 準備：最初の閉じる操作が失敗して、閉じる依頼が未完了で残り、その後に通知が届いて与信の取消済みになった支払
        var orderId = payments.created(ORDERED_AT);
        var pageId = payments.openPage(orderId);
        gateway.failNextCloses(1);
        cancel(orderId);
        asyncEvents.awaitFailed(pageClose(orderId));
        gateway.notifyAuthorized(pageId);
        awaitStatus(orderId, PaymentSummary.Status.VOIDED);
        asyncEvents.awaitPublishedThenHandled(PaymentRequests.voidAuthorization(dsl, orderId));
        assertThat(gateway.closeCallsOf(pageId)).isEqualTo(1);

        // 操作：再発行のジョブと同じく、閉じる依頼を再発行する
        clock.advance(PAST_RESUBMIT_AGE);
        asyncEvents.resubmit(pageClose(orderId));

        // 確認：依頼の処理は終わり、画面を閉じる操作も与信の取得も呼ばず、与信は 1 回だけ取り消された
        asyncEvents.awaitHandled(pageClose(orderId));
        assertThat(gateway.closeCallsOf(pageId)).isEqualTo(1);
        assertThat(gateway.fetchAuthorizationCalls()).isZero();
        assertThat(gateway.voidsOf(gateway.authorizationRefOf(pageId))).isEqualTo(1);
        assertThat(payments.statusOf(orderId)).isEqualTo(PaymentSummary.Status.VOIDED);
    }

    // ============================== 確認の補助 ==============================

    /**
     * 注文の支払の、支払画面を閉じる依頼のイベントを選ぶ条件を返す。
     *
     * @param orderId 注文 ID
     * @return イベントの条件
     */
    private Predicate<Object> pageClose(UUID orderId) {
        return PaymentRequests.pageClose(dsl, orderId);
    }

    /**
     * 注文の支払が指定の状態になるまで待つ。
     *
     * @param orderId 注文 ID
     * @param status  待つ状態
     */
    private void awaitStatus(UUID orderId, PaymentSummary.Status status) {
        await().atMost(TIMEOUT).until(() -> payments.statusOf(orderId) == status);
    }

    // ============================== 準備の補助 ==============================

    /**
     * 支払の取消を、トランザクションの中で呼ぶ（取消は呼んだ側のトランザクションに加わる）。
     *
     * @param orderId 注文 ID
     */
    private void cancel(UUID orderId) {
        transactionTemplate.executeWithoutResult(status -> paymentApi.cancel(orderId));
    }
}
