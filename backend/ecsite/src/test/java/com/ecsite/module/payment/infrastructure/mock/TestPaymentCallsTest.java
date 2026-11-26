package com.ecsite.module.payment.infrastructure.mock;

import com.ecsite.common.model.value.Money;
import com.ecsite.module.payment.infrastructure.mock.TestPaymentGateway.Operation;
import com.ecsite.support.async.Concurrently;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.function.IntConsumer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * テスト用の呼び出しの記録（{@link TestPaymentCalls}）の検証
 */
@DisplayName("テスト用の呼び出しの記録")
class TestPaymentCallsTest {

    // ============================== 定数 ==============================

    /**
     * 同時に記録するスレッドの数
     */
    private static final int THREADS = 8;

    /**
     * 1 つのスレッドが記録する回数
     */
    private static final int RECORDS_PER_THREAD = 50;

    // ============================== 対象 ==============================

    /**
     * 確認する呼び出しの記録
     */
    private final TestPaymentCalls calls = new TestPaymentCalls();

    // ============================== テスト ==============================

    @Test
    @DisplayName("何も記録していないと、全ての読み取りが空か 0 になる")
    void everythingIsEmptyWhenNothingRecorded() {
        // 準備：記録しない
        var orderId = UUID.randomUUID();

        // 操作と確認：全ての読み取りが空か 0
        assertThat(calls.amountsOf(Operation.CAPTURE, orderId)).isEmpty();
        assertThat(calls.captureCalls()).isZero();
        assertThat(calls.captureCallsOf(orderId)).isZero();
        assertThat(calls.openedPages()).isZero();
        assertThat(calls.openedPageIds()).isEmpty();
        assertThat(calls.closeCallsOf("page")).isZero();
        assertThat(calls.fetchAuthorizationCalls()).isZero();
        assertThat(calls.fetchCaptureStateCalls()).isZero();
        assertThat(calls.fetchCaptureStateCallsOf("auth")).isZero();
        assertThat(calls.voided()).isEmpty();
        assertThat(calls.voidsOf("auth")).isZero();
        assertThat(calls.captured()).isEmpty();
        assertThat(calls.capturedAuthorizationRef(orderId)).isEmpty();
        assertThat(calls.refunded()).isEmpty();
        assertThat(calls.refundsOf(orderId)).isZero();
    }

    @Test
    @DisplayName("金額を記録すると、操作と注文の両方で絞って、呼ばれた順に重複も含めて読める")
    void amountsAreFilteredByOperationAndOrderInCallOrder() {
        // 準備：同じ注文の売上確定を重複して含め、他の操作と他の注文も記録する
        var order = UUID.randomUUID();
        var other = UUID.randomUUID();
        calls.recordAmount(Operation.CAPTURE, order, Money.of(100));
        calls.recordAmount(Operation.REFUND, order, Money.of(300));
        calls.recordAmount(Operation.CAPTURE, other, Money.of(400));
        calls.recordAmount(Operation.CAPTURE, order, Money.of(100));
        calls.recordAmount(Operation.CAPTURE, order, Money.of(200));

        // 操作と確認：操作と注文の両方で絞られる
        assertThat(calls.amountsOf(Operation.CAPTURE, order))
                .containsExactly(Money.of(100), Money.of(100), Money.of(200));
        assertThat(calls.amountsOf(Operation.REFUND, order)).containsExactly(Money.of(300));
        assertThat(calls.amountsOf(Operation.OPEN_PAGE, order)).isEmpty();
        assertThat(calls.amountsOf(Operation.CAPTURE, other)).containsExactly(Money.of(400));
    }

    @Test
    @DisplayName("金額を記録すると、売上確定の回数は売上確定だけを数える")
    void captureCallsCountOnlyCaptures() {
        // 準備：売上確定を重複して含め、他の操作と他の注文も記録する
        var order = UUID.randomUUID();
        calls.recordAmount(Operation.CAPTURE, order, Money.of(100));
        calls.recordAmount(Operation.CAPTURE, order, Money.of(100));
        calls.recordAmount(Operation.OPEN_PAGE, order, Money.of(100));
        calls.recordAmount(Operation.REFUND, order, Money.of(100));
        calls.recordAmount(Operation.CAPTURE, UUID.randomUUID(), Money.of(100));

        // 操作と確認：全体と注文ごとの回数が売上確定だけを数える
        assertThat(calls.captureCalls()).isEqualTo(3);
        assertThat(calls.captureCallsOf(order)).isEqualTo(2);
    }

    @Test
    @DisplayName("作った支払画面を記録すると、作った順と数で読め、返した一覧は後の記録で変わらない")
    void openedPagesAreReadInOrderAndSnapshotIsStable() {
        // 準備：2 つ記録して一覧を取る
        calls.recordOpenedPage("page-1");
        calls.recordOpenedPage("page-2");
        var snapshot = calls.openedPageIds();

        // 操作：さらに記録する
        calls.recordOpenedPage("page-3");

        // 確認：数と順が合い、取った一覧は変わらない
        assertThat(calls.openedPages()).isEqualTo(3);
        assertThat(calls.openedPageIds()).containsExactly("page-1", "page-2", "page-3");
        assertThat(snapshot).containsExactly("page-1", "page-2");
    }

    @Test
    @DisplayName("画面を閉じる呼び出しを記録すると、画面ごとの回数を重複も含めて数える")
    void closeCallsAreCountedPerPageIncludingDuplicates() {
        // 準備：同じ画面を重複して含め、他の画面も記録する
        calls.recordClose("page-1");
        calls.recordClose("page-1");
        calls.recordClose("page-2");

        // 操作と確認：画面ごとに数える
        assertThat(calls.closeCallsOf("page-1")).isEqualTo(2);
        assertThat(calls.closeCallsOf("page-2")).isEqualTo(1);
        assertThat(calls.closeCallsOf("page-3")).isZero();
    }

    @Test
    @DisplayName("売上確定の状態の取得を記録すると、全体と与信ごとの回数を重複も含めて数える")
    void captureStateFetchesAreCountedOverallAndPerAuthorization() {
        // 準備：同じ与信を重複して含め、他の与信も記録する
        calls.recordCaptureStateFetch("auth-1");
        calls.recordCaptureStateFetch("auth-1");
        calls.recordCaptureStateFetch("auth-2");

        // 操作と確認：全体と与信ごとに数える
        assertThat(calls.fetchCaptureStateCalls()).isEqualTo(3);
        assertThat(calls.fetchCaptureStateCallsOf("auth-1")).isEqualTo(2);
        assertThat(calls.fetchCaptureStateCallsOf("auth-2")).isEqualTo(1);
        assertThat(calls.fetchCaptureStateCallsOf("auth-3")).isZero();
    }

    @Test
    @DisplayName("与信の取得を記録すると、同じ画面の重複も含めて回数を数える")
    void authorizationFetchesAreCountedIncludingDuplicates() {
        // 準備：同じ画面を重複して記録する
        calls.recordAuthorizationFetch("page-1");
        calls.recordAuthorizationFetch("page-1");
        calls.recordAuthorizationFetch("page-2");

        // 操作と確認：全て数える
        assertThat(calls.fetchAuthorizationCalls()).isEqualTo(3);
    }

    @Test
    @DisplayName("取り消した与信を記録すると、取り消した順に重複を残して読め、返した一覧は後の記録で変わらない")
    void voidsAreReadInOrderWithDuplicatesAndSnapshotIsStable() {
        // 準備：同じ与信の取消を重複して含めて記録し、一覧を取る
        calls.recordVoid("auth-1");
        calls.recordVoid("auth-2");
        calls.recordVoid("auth-1");
        var snapshot = calls.voided();

        // 操作：さらに記録する
        calls.recordVoid("auth-3");

        // 確認：順と重複が残り、与信ごとの回数が合い、取った一覧は変わらない
        assertThat(calls.voided()).containsExactly("auth-1", "auth-2", "auth-1", "auth-3");
        assertThat(calls.voidsOf("auth-1")).isEqualTo(2);
        assertThat(calls.voidsOf("auth-2")).isEqualTo(1);
        assertThat(calls.voidsOf("auth-9")).isZero();
        assertThat(snapshot).containsExactly("auth-1", "auth-2", "auth-1");
    }

    @Test
    @DisplayName("売上確定を記録すると、確定した順に読め、同じ注文は 1 回で、返した一覧は後の記録で変わらない")
    void capturesAreReadInOrderOncePerOrderAndSnapshotIsStable() {
        // 準備：同じ注文を重複して含めて記録し、一覧を取る
        var first = UUID.randomUUID();
        var second = UUID.randomUUID();
        calls.recordCapture(first, "auth-1");
        calls.recordCapture(second, "auth-2");
        calls.recordCapture(first, "auth-1");
        var snapshot = calls.captured();

        // 操作：さらに記録する
        calls.recordCapture(UUID.randomUUID(), "auth-3");

        // 確認：順が合い、同じ注文は 1 回で、取った一覧は変わらない
        assertThat(calls.captured()).hasSize(3).startsWith(first, second);
        assertThat(snapshot).containsExactly(first, second);
    }

    @Test
    @DisplayName("売上確定を記録すると、与信の参照は注文ごとに最後のものを読め、記録のない注文は空になる")
    void capturedAuthorizationRefIsTheLastOnePerOrder() {
        // 準備：同じ注文に別の与信の参照を続けて記録する
        var order = UUID.randomUUID();
        calls.recordCapture(order, "auth-1");
        calls.recordCapture(order, "auth-2");

        // 操作と確認：最後のものを読め、記録のない注文は空
        assertThat(calls.capturedAuthorizationRef(order)).contains("auth-2");
        assertThat(calls.capturedAuthorizationRef(UUID.randomUUID())).isEmpty();
    }

    @Test
    @DisplayName("返金を記録すると、返金した順に読め、同じ注文は 1 回で、返した一覧は後の記録で変わらない")
    void refundsAreReadInOrderOncePerOrderAndSnapshotIsStable() {
        // 準備：同じ注文を重複して含めて記録し、一覧を取る
        var first = UUID.randomUUID();
        var second = UUID.randomUUID();
        calls.recordRefund(first);
        calls.recordRefund(second);
        calls.recordRefund(first);
        var snapshot = calls.refunded();

        // 操作：さらに記録する
        calls.recordRefund(UUID.randomUUID());

        // 確認：順と注文ごとの回数が合い、記録のない注文は 0 で、取った一覧は変わらない
        assertThat(calls.refunded()).hasSize(3).startsWith(first, second);
        assertThat(calls.refundsOf(first)).isEqualTo(1);
        assertThat(calls.refundsOf(second)).isEqualTo(1);
        assertThat(calls.refundsOf(UUID.randomUUID())).isZero();
        assertThat(snapshot).containsExactly(first, second);
    }

    @Test
    @DisplayName("同時に記録しても、全ての記録が残る")
    void allRecordsRemainWhenRecordedConcurrently() throws Exception {
        // 準備：同時に記録する対象の注文を決める
        var order = UUID.randomUUID();

        // 操作：全てのスレッドで同時に、各種の記録を繰り返す
        runConcurrently(i -> {
            calls.recordAmount(Operation.CAPTURE, order, Money.of(100));
            calls.recordOpenedPage("page-" + i);
            calls.recordClose("page");
            calls.recordAuthorizationFetch("page");
            calls.recordCaptureStateFetch("auth");
            calls.recordVoid("auth");
        });

        // 確認：全ての記録が残る
        var total = THREADS * RECORDS_PER_THREAD;
        assertThat(calls.amountsOf(Operation.CAPTURE, order)).hasSize(total);
        assertThat(calls.captureCalls()).isEqualTo(total);
        assertThat(calls.openedPages()).isEqualTo(total);
        assertThat(calls.closeCallsOf("page")).isEqualTo(total);
        assertThat(calls.fetchAuthorizationCalls()).isEqualTo(total);
        assertThat(calls.fetchCaptureStateCallsOf("auth")).isEqualTo(total);
        assertThat(calls.voidsOf("auth")).isEqualTo(total);
    }

    @Test
    @DisplayName("同じ注文の売上確定と返金を同時に記録しても、それぞれ 1 回になる")
    void sameOrderIsRecordedOnceWhenCapturedAndRefundedConcurrently() throws Exception {
        // 準備：同時に記録する対象の注文を決める
        var order = UUID.randomUUID();

        // 操作：全てのスレッドで同時に、同じ注文の売上確定と返金を記録する
        runConcurrently(i -> {
            calls.recordCapture(order, "auth");
            calls.recordRefund(order);
        });

        // 確認：どちらも 1 回だけ残る
        assertThat(calls.captured()).containsExactly(order);
        assertThat(calls.refundsOf(order)).isEqualTo(1);
    }

    // ============================== 確認の補助 ==============================

    /**
     * 全てのスレッドを同時に始めて、指定した記録を各スレッドで繰り返し、全ての終わりを待つ。
     *
     * @param record 記録の処理（引数は、全てのスレッドを通した連番）
     * @throws Exception 記録の処理が失敗したとき
     */
    private static void runConcurrently(IntConsumer record) throws Exception {
        Concurrently.run(THREADS, thread -> {
            var offset = thread * RECORDS_PER_THREAD;
            for (var i = 0; i < RECORDS_PER_THREAD; i++) {
                record.accept(offset + i);
            }
            return null;
        });
    }
}
