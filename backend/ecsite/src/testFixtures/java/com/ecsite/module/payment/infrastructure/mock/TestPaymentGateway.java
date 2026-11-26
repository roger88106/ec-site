package com.ecsite.module.payment.infrastructure.mock;

import com.ecsite.common.error.exception.SystemException;
import com.ecsite.common.model.value.Money;
import com.ecsite.module.payment.application.model.Authorization;
import com.ecsite.module.payment.application.port.PaymentGateway;
import com.ecsite.module.payment.domain.PaymentErrorCode;
import com.ecsite.module.payment.domain.payment.PaymentPage;
import com.ecsite.module.payment.infrastructure.mock.TestPaymentActions.Timing;
import com.ecsite.module.payment.infrastructure.mock.TestPaymentFailures.Kind;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * テストで使う決済サービス
 *
 * <p>本番の mock（{@link MockPaymentGateway}）を包み、テストだけで使う操作を追加する。
 * 失敗の指定は {@link TestPaymentFailures}、呼ばれた操作の記録は {@link TestPaymentCalls}、操作の途中に差し込む処理は {@link TestPaymentActions} が持ち、このクラスは各操作でそれらを呼ぶ順序を決める。
 * 本番の jar に入れないため、テストで共有するソースに置く。
 */
public class TestPaymentGateway implements PaymentGateway {

    /**
     * 包む mock
     */
    private final MockPaymentGateway mock;

    /**
     * テストだけで使う状態
     *
     * <p>{@link #reset()} で新しい状態に入れ替える。項目を追加するときは、{@link State} に追加する。
     */
    private volatile State state = State.empty();

    /**
     * 本番の mock を包む。
     *
     * @param mock 包む mock
     */
    public TestPaymentGateway(MockPaymentGateway mock) {
        this.mock = mock;
    }

    // ============================== 決済サービス ==============================

    @Override
    public PaymentPage openPage(PageRequest request) {
        state.calls().recordAmount(Operation.OPEN_PAGE, request.orderId().value(), request.amount());
        if (state.failures().consume(Kind.OPEN)) {
            throw SystemException.of("mock：支払画面を作れなくする: " + request.paymentId(), null,
                    PaymentErrorCode.PAYMENT_GATEWAY_UNAVAILABLE);
        }
        var page = mock.openPage(request);
        state.calls().recordOpenedPage(page.id());
        state.actions().run(Timing.AFTER_OPEN);
        return page;
    }

    @Override
    public CloseResult closePage(String pageId) {
        state.calls().recordClose(pageId);
        if (state.failures().consume(Kind.CLOSE)) {
            throw SystemException.of("mock：支払画面を閉じる操作を接続の失敗にする: " + pageId, null,
                    PaymentErrorCode.PAYMENT_GATEWAY_UNAVAILABLE);
        }
        return mock.closePage(pageId);
    }

    @Override
    public Authorization fetchAuthorization(String pageId) {
        state.calls().recordAuthorizationFetch(pageId);
        state.actions().run(Timing.ON_FETCH_AUTHORIZATION);
        if (state.failures().consume(Kind.AUTHORIZATION_FETCH)) {
            throw SystemException.of("mock：与信の取得を接続の失敗にする: " + pageId, null,
                    PaymentErrorCode.PAYMENT_GATEWAY_UNAVAILABLE);
        }
        if (state.failures().isAuthorizationFetchRejected(pageId)) {
            throw SystemException.of("mock：与信の取得を失敗させる: " + pageId, null);
        }
        return mock.fetchAuthorization(pageId);
    }

    @Override
    public VoidResult voidAuthorization(String authorizationRef) {
        state.actions().run(Timing.ON_REFUND_OR_VOID);
        if (state.failures().consume(Kind.VOID)) {
            throw SystemException.of("mock：与信の取消に失敗させる: " + authorizationRef, null);
        }
        var result = mock.voidAuthorization(authorizationRef);
        if (result == VoidResult.VOIDED) {
            state.calls().recordVoid(authorizationRef);
        }
        return result;
    }

    @Override
    public CaptureResult capture(CaptureRequest request) {
        state.calls().recordAmount(Operation.CAPTURE, request.orderId().value(), request.amount());
        if (state.failures().consume(Kind.CAPTURE_CONNECTION)) {
            return CaptureResult.UNREACHABLE;
        }
        if (state.failures().isExpired(request.authorizationRef())) {
            return CaptureResult.AUTHORIZATION_EXPIRED;
        }
        var result = mock.capture(request);
        state.calls().recordCapture(request.orderId().value(), request.authorizationRef());
        state.actions().run(Timing.AFTER_CAPTURE);
        if (state.failures().consume(Kind.CAPTURE_RESPONSE)) {
            return CaptureResult.UNREACHABLE;
        }
        return result;
    }

    @Override
    public CaptureState fetchCaptureState(String authorizationRef) {
        state.calls().recordCaptureStateFetch(authorizationRef);
        if (state.failures().consume(Kind.CAPTURE_STATE_FETCH)) {
            throw SystemException.of("mock：売上確定の状態の取得を接続の失敗にする: " + authorizationRef, null,
                    PaymentErrorCode.PAYMENT_GATEWAY_UNAVAILABLE);
        }
        var captureState = mock.fetchCaptureState(authorizationRef);
        // 確定していない与信が切れていれば、与信期限切れ
        if (captureState == CaptureState.NOT_CAPTURED && state.failures().isExpired(authorizationRef)) {
            captureState = CaptureState.AUTHORIZATION_EXPIRED;
        }
        state.actions().run(Timing.AFTER_CAPTURE_STATE_FETCH);
        return captureState;
    }

    @Override
    public void refund(RefundRequest request) {
        state.calls().recordAmount(Operation.REFUND, request.orderId().value(), request.amount());
        state.actions().run(Timing.ON_REFUND_OR_VOID);
        if (state.failures().consume(Kind.REFUND)) {
            throw SystemException.of("mock：返金に失敗させる: " + request.orderId(), null);
        }
        mock.refund(request);
        state.calls().recordRefund(request.orderId().value());
    }

    // ============================== 支払画面の操作（本番の mock に渡す） ==============================

    /**
     * 支払う：決済サービスで支払を済ませ、与信の成功を通知する。
     *
     * @param pageId 支払画面 ID
     */
    public void pay(String pageId) {
        mock.pay(pageId);
    }

    /**
     * 決済サービスで支払を済ませるが、まだ通知しない（通知の遅れ）。
     *
     * @param pageId 支払画面 ID
     */
    public void markPaid(String pageId) {
        mock.markPaid(pageId);
    }

    /**
     * 与信の成功を通知する。
     *
     * <p>重ねて呼ぶと、重ねて届いた通知になる。
     *
     * @param pageId 支払画面 ID
     */
    public void notifyAuthorized(String pageId) {
        mock.notifyAuthorized(pageId);
    }

    /**
     * 支払画面を返す。
     *
     * @param pageId 支払画面 ID
     * @return 画面。知らない画面なら空
     */
    public Optional<MockPaymentGateway.Page> page(String pageId) {
        return mock.page(pageId);
    }

    /**
     * 与信の参照を返す。
     *
     * <p>支払画面ごとに決まる。
     *
     * @param pageId 支払画面 ID
     * @return 与信の参照
     */
    public String authorizationRefOf(String pageId) {
        return mock.authorizationRefOf(pageId);
    }

    // ============================== テストだけで使う操作 ==============================

    /**
     * 次の与信の取消を、指定した回数だけ失敗させる。
     *
     * @param times 失敗させる回数
     */
    public void failNextVoids(int times) {
        state.failures().failNext(Kind.VOID, times);
    }

    /**
     * 次の支払画面の作成を、指定した回数だけ、決済サービスに接続できないこと（外部サービスのエラー）にする。
     *
     * @param times 接続できなくする回数
     */
    public void failNextOpens(int times) {
        state.failures().failNext(Kind.OPEN, times);
    }

    /**
     * 次の画面を閉じる操作を、指定した回数だけ、接続の失敗（外部サービスのエラー）にする。
     *
     * @param times 接続の失敗にする回数
     */
    public void failNextCloses(int times) {
        state.failures().failNext(Kind.CLOSE, times);
    }

    /**
     * 次の与信の取得を、指定した回数だけ、接続の失敗（外部サービスのエラー）にする。
     *
     * @param times 接続の失敗にする回数
     */
    public void failNextAuthorizationFetches(int times) {
        state.failures().failNext(Kind.AUTHORIZATION_FETCH, times);
    }

    /**
     * その支払画面の与信の取得を、予期しないエラーで失敗させ続ける。
     *
     * <p>解除は {@link #reset()}。
     *
     * @param pageId 支払画面 ID
     */
    public void rejectAuthorizationFetch(String pageId) {
        state.failures().rejectAuthorizationFetch(pageId);
    }

    /**
     * 与信が切れたことにする。
     *
     * <p>以降、その与信の売上確定は、与信期限切れで断られる。
     *
     * @param authorizationRef 与信の参照
     */
    public void expireAuthorization(String authorizationRef) {
        state.failures().expireAuthorization(authorizationRef);
    }

    /**
     * 次の売上確定を、指定した回数だけ、決済サービスに接続できないことにする。
     *
     * @param times 接続できなくする回数
     */
    public void failNextCaptures(int times) {
        state.failures().failNext(Kind.CAPTURE_CONNECTION, times);
    }

    /**
     * 次の売上確定から指定した回数だけ、決済サービスでは売上を確定するが、応答が届かないようにする。
     *
     * <p>呼んだ側には、接続できなかった場合と同じ結果が返る。
     *
     * @param times 応答を届かなくする回数
     */
    public void loseNextCaptureResponses(int times) {
        state.failures().failNext(Kind.CAPTURE_RESPONSE, times);
    }

    /**
     * 次の売上確定の状態の取得を、指定した回数だけ、接続の失敗（外部サービスのエラー）にする。
     *
     * @param times 接続の失敗にする回数
     */
    public void failNextCaptureStateFetches(int times) {
        state.failures().failNext(Kind.CAPTURE_STATE_FETCH, times);
    }

    /**
     * 次の売上確定の状態の取得で、決済サービスが状態を読んだ後、応答を返す前に処理を行う。
     *
     * @param action 行う処理（売上確定の再実行、時計を進めるなど）
     */
    public void duringNextCaptureStateFetch(Runnable action) {
        state.actions().once(Timing.AFTER_CAPTURE_STATE_FETCH, action);
    }

    /**
     * 次の支払画面の作成で、決済サービスが画面を作った後、応答を返す前に処理を行う。
     *
     * @param action 行う処理（同じ注文の支払画面のリクエスト、時計を進めるなど）
     */
    public void duringNextOpen(Runnable action) {
        state.actions().once(Timing.AFTER_OPEN, action);
    }

    /**
     * 次の売上確定で、決済サービスが確定した後、応答を返す前に処理を行う。
     *
     * @param action 行う処理（注文のキャンセルなど）
     */
    public void duringNextCapture(Runnable action) {
        state.actions().once(Timing.AFTER_CAPTURE, action);
    }

    /**
     * 次の返金を、指定した回数だけ失敗させる。
     *
     * @param times 失敗させる回数
     */
    public void failNextRefunds(int times) {
        state.failures().failNext(Kind.REFUND, times);
    }

    /**
     * これからの返金と与信の取消で、呼ばれた時点に処理を行う（呼ばれた時点のトランザクションの有無の記録など）。
     *
     * @param action 行う処理
     */
    public void onRefundOrVoid(Runnable action) {
        state.actions().always(Timing.ON_REFUND_OR_VOID, action);
    }

    /**
     * これからの与信の取得で、呼ばれた時点に処理を行う（呼ばれた時点のトランザクションの有無の記録など）。
     *
     * @param action 行う処理
     */
    public void onFetchAuthorization(Runnable action) {
        state.actions().always(Timing.ON_FETCH_AUTHORIZATION, action);
    }

    /**
     * 売上を確定した注文 ID を返す。
     *
     * @return 売上を確定した注文 ID（確定した順。同じ注文は 1 回）
     */
    public List<UUID> captured() {
        return state.calls().captured();
    }

    /**
     * 売上確定を呼ばれた回数を返す。
     *
     * @return 売上確定を呼ばれた回数（失敗と重複を含む）
     */
    public int captureCalls() {
        return state.calls().captureCalls();
    }

    /**
     * 注文について、売上確定を呼ばれた回数を返す。
     *
     * <p>他のテストの注文の呼び出しを数えないように、注文で絞る。
     *
     * @param orderId 注文 ID
     * @return 売上確定を呼ばれた回数（失敗と重複を含む）
     */
    public int captureCallsOf(UUID orderId) {
        return state.calls().captureCallsOf(orderId);
    }

    /**
     * 注文の売上確定に渡された与信の参照を返す。
     *
     * @param orderId 注文 ID
     * @return 与信の参照。売上確定を呼ばれていなければ空
     */
    public Optional<String> capturedAuthorizationRef(UUID orderId) {
        return state.calls().capturedAuthorizationRef(orderId);
    }

    /**
     * 与信の取得を呼ばれた回数を返す。
     *
     * @return 与信の取得を呼ばれた回数（失敗を含む）
     */
    public int fetchAuthorizationCalls() {
        return state.calls().fetchAuthorizationCalls();
    }

    /**
     * 支払画面について、画面を閉じる操作を呼ばれた回数を返す。
     *
     * <p>他のテストの画面を数えないように、支払画面 ID で絞る。
     *
     * @param pageId 支払画面 ID
     * @return 画面を閉じる操作を呼ばれた回数（失敗と重複を含む）
     */
    public int closeCallsOf(String pageId) {
        return state.calls().closeCallsOf(pageId);
    }

    /**
     * 売上確定の状態の取得を呼ばれた回数を返す。
     *
     * @return 売上確定の状態の取得を呼ばれた回数（失敗を含む）
     */
    public int fetchCaptureStateCalls() {
        return state.calls().fetchCaptureStateCalls();
    }

    /**
     * 与信について、売上確定の状態の取得を呼ばれた回数を返す。
     *
     * <p>他のテストの支払の照合を数えないように、与信の参照で絞る。
     *
     * @param authorizationRef 与信の参照
     * @return 売上確定の状態の取得を呼ばれた回数（失敗を含む）
     */
    public int fetchCaptureStateCallsOf(String authorizationRef) {
        return state.calls().fetchCaptureStateCallsOf(authorizationRef);
    }

    /**
     * 返金した注文 ID を返す。
     *
     * @return 返金した注文 ID（返金した順。同じ注文は 1 回）
     */
    public List<UUID> refunded() {
        return state.calls().refunded();
    }

    /**
     * 注文について、返金した回数を返す。
     *
     * <p>他のテストの注文の返金を数えないように、注文で絞る。
     *
     * @param orderId 注文 ID
     * @return 返金した回数（同じ注文は 1 回）
     */
    public int refundsOf(UUID orderId) {
        return state.calls().refundsOf(orderId);
    }

    /**
     * 取り消した与信の参照を返す。
     *
     * @return 取り消した与信の参照（取り消した順）
     */
    public List<String> voided() {
        return state.calls().voided();
    }

    /**
     * 与信について、取り消した回数を返す。
     *
     * <p>他のテストの与信の取消を数えないように、与信の参照で絞る。
     *
     * @param authorizationRef 与信の参照
     * @return 取り消した回数
     */
    public int voidsOf(String authorizationRef) {
        return state.calls().voidsOf(authorizationRef);
    }

    /**
     * 注文について、決済サービスに渡した金額を、呼ばれた順に返す。
     *
     * @param operation 操作（支払画面を開く、売上確定、返金）
     * @param orderId   注文 ID
     * @return 渡した金額（呼ばれた順。失敗と重複を含む）
     */
    public List<Money> amountsOf(Operation operation, UUID orderId) {
        return state.calls().amountsOf(operation, orderId);
    }

    /**
     * 作った支払画面の数を返す。
     *
     * @return 作った支払画面の数（最後に状態を消してから）
     */
    public int openedPages() {
        return state.calls().openedPages();
    }

    /**
     * 作った支払画面の ID を返す。
     *
     * @return 作った順の支払画面 ID（最後に状態を消してから）
     */
    public List<String> openedPageIds() {
        return state.calls().openedPageIds();
    }

    /**
     * テストだけで使う状態を最初に戻す。
     *
     * <p>本番の mock の支払画面は残す（ID が毎回違うので、他のテストに影響しない）。
     * 操作の途中で初期化されたときも、その後の呼び出しと記録は新しい状態に入る。
     */
    public void reset() {
        state = State.empty();
    }

    /**
     * テストだけで使う状態
     *
     * @param failures 失敗の注入の指定
     * @param calls    呼ばれた操作の記録
     * @param actions  操作の途中に差し込む処理の指定
     */
    private record State(TestPaymentFailures failures, TestPaymentCalls calls, TestPaymentActions actions) {

        /**
         * 何も指定も記録もない状態を作る。
         *
         * @return 空の状態
         */
        static State empty() {
            return new State(new TestPaymentFailures(), new TestPaymentCalls(), new TestPaymentActions());
        }
    }

    /**
     * 金額を渡す決済サービスの操作
     */
    public enum Operation {

        /**
         * 支払画面を開く（与信を取る額）
         */
        OPEN_PAGE,

        /**
         * 売上確定（確定する額）
         */
        CAPTURE,

        /**
         * 返金（返金する額）
         */
        REFUND
    }
}
