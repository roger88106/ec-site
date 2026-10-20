package com.ecsite.module.payment.infrastructure.mock;

import com.ecsite.common.error.exception.SystemException;
import com.ecsite.module.payment.application.port.PaymentGateway;
import com.ecsite.module.payment.domain.payment.PaymentErrorCode;
import com.ecsite.module.payment.domain.payment.PaymentPage;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * テストで使う決済サービス。本番の mock（{@link MockPaymentGateway}）を包み、テストだけで使う操作を足す。<br />
 * 通知の遅れ・重複・順序の入れ替わりや、取消・売上確定・返金の失敗を起こし、呼ばれた操作を記録する。
 * 本番の jar に入れないため、テストで共有するソースに置く。
 */
public class TestPaymentGateway implements PaymentGateway {

    /**
     * 包む mock
     */
    private final MockPaymentGateway mock;

    /**
     * 作った支払の画面の数
     */
    private final AtomicInteger openedPages = new AtomicInteger();

    /**
     * これから接続できないことにする支払の画面の作成の回数
     */
    private final AtomicInteger openFailures = new AtomicInteger();

    /**
     * 取り消した与信の参照（取り消した順）
     */
    private final List<String> voided = new CopyOnWriteArrayList<>();

    /**
     * これから失敗させる与信の取消の回数
     */
    private final AtomicInteger voidFailures = new AtomicInteger();

    /**
     * 与信が切れたことにした与信の参照。売上確定が与信期限切れで断られる
     */
    private final Set<String> expiredAuthorizations = ConcurrentHashMap.newKeySet();

    /**
     * 売上を確定した注文 ID（確定した順）
     */
    private final List<UUID> captured = new CopyOnWriteArrayList<>();

    /**
     * 次の売上確定の最中に行う処理（売上確定と注文のキャンセルが重なったときのテスト用）。なければ null
     */
    private final AtomicReference<Runnable> duringNextCapture = new AtomicReference<>();

    /**
     * 売上確定を呼ばれた回数（失敗と重複を含む）
     */
    private final AtomicInteger captureCalls = new AtomicInteger();

    /**
     * これから接続できないことにする売上確定の回数
     */
    private final AtomicInteger captureFailures = new AtomicInteger();

    /**
     * 返金した注文 ID（返金した順）
     */
    private final List<UUID> refunded = new CopyOnWriteArrayList<>();

    /**
     * これから失敗させる返金の回数
     */
    private final AtomicInteger refundFailures = new AtomicInteger();

    /**
     * 返金と与信の取消を呼ばれた時点で行う処理（呼ばれた時点の状態を記録するテスト用）。なければ null
     */
    private final AtomicReference<Runnable> onRefundOrVoid = new AtomicReference<>();

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
        if (consume(openFailures)) {
            throw SystemException.of("mock：支払の画面を作れなくする: " + request.paymentId(), null,
                    PaymentErrorCode.PAYMENT_GATEWAY_UNAVAILABLE);
        }
        openedPages.incrementAndGet();
        return mock.openPage(request);
    }

    @Override
    public CloseResult closePage(String pageId) {
        return mock.closePage(pageId);
    }

    @Override
    public void voidAuthorization(String authorizationRef) {
        runOnRefundOrVoid();
        if (consume(voidFailures)) {
            throw SystemException.of("mock：与信の取消に失敗させる: " + authorizationRef, null);
        }
        mock.voidAuthorization(authorizationRef);
        voided.add(authorizationRef);
    }

    @Override
    public CaptureResult capture(CaptureRequest request) {
        captureCalls.incrementAndGet();
        if (consume(captureFailures)) {
            return CaptureResult.UNREACHABLE;
        }
        if (expiredAuthorizations.contains(request.authorizationRef())) {
            return CaptureResult.AUTHORIZATION_EXPIRED;
        }
        var result = mock.capture(request);
        // 冪等キー（注文 ID）が同じなら、確定は 1 回だけ
        if (!captured.contains(request.orderId())) {
            captured.add(request.orderId());
        }
        var during = duringNextCapture.getAndSet(null);
        if (during != null) {
            during.run();
        }
        return result;
    }

    @Override
    public void refund(RefundRequest request) {
        runOnRefundOrVoid();
        if (consume(refundFailures)) {
            throw SystemException.of("mock：返金に失敗させる: " + request.orderId(), null);
        }
        mock.refund(request);
        // 冪等キー（注文 ID）が同じなら、返金は 1 回だけ
        if (!refunded.contains(request.orderId())) {
            refunded.add(request.orderId());
        }
    }

// ============================== 支払の画面の操作（本番の mock に渡す） ==============================

    /**
     * 支払う：決済サービスで支払を済ませ、与信の成功を通知する。
     *
     * @param pageId 支払の画面 ID
     */
    public void pay(String pageId) {
        mock.pay(pageId);
    }

    /**
     * 決済サービスで支払を済ませるが、まだ通知しない（通知の遅れ）。
     *
     * @param pageId 支払の画面 ID
     */
    public void markPaid(String pageId) {
        mock.markPaid(pageId);
    }

    /**
     * 与信の成功を通知する。重ねて呼ぶと、重ねて届いた通知になる。
     *
     * @param pageId 支払の画面 ID
     */
    public void notifyAuthorized(String pageId) {
        mock.notifyAuthorized(pageId);
    }

    /**
     * 支払の画面を返す。
     *
     * @param pageId 支払の画面 ID
     * @return 画面。知らない画面なら空
     */
    public Optional<MockPaymentGateway.Page> page(String pageId) {
        return mock.page(pageId);
    }

    /**
     * 与信の参照を返す。支払の画面ごとに決まる。
     *
     * @param pageId 支払の画面 ID
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
        voidFailures.set(times);
    }

    /**
     * 次の支払の画面の作成を、指定した回数だけ、決済サービスに接続できないこと（外部サービスのエラー）にする。
     *
     * @param times 接続できなくする回数
     */
    public void failNextOpens(int times) {
        openFailures.set(times);
    }

    /**
     * 与信が切れたことにする。以降、その与信の売上確定は、与信期限切れで断られる。
     *
     * @param authorizationRef 与信の参照
     */
    public void expireAuthorization(String authorizationRef) {
        expiredAuthorizations.add(authorizationRef);
    }

    /**
     * 次の売上確定を、指定した回数だけ、決済サービスに接続できないことにする。
     *
     * @param times 接続できなくする回数
     */
    public void failNextCaptures(int times) {
        captureFailures.set(times);
    }

    /**
     * 次の売上確定で、決済サービスが確定した後、応答を返す前に処理を行う。
     *
     * @param action 行う処理（注文のキャンセルなど）
     */
    public void duringNextCapture(Runnable action) {
        duringNextCapture.set(action);
    }

    /**
     * 次の返金を、指定した回数だけ失敗させる。
     *
     * @param times 失敗させる回数
     */
    public void failNextRefunds(int times) {
        refundFailures.set(times);
    }

    /**
     * これからの返金と与信の取消で、呼ばれた時点に処理を行う（呼ばれた時点のトランザクションの有無の記録など）。
     *
     * @param action 行う処理
     */
    public void onRefundOrVoid(Runnable action) {
        onRefundOrVoid.set(action);
    }

    /**
     * 売上を確定した注文 ID を返す。
     *
     * @return 売上を確定した注文 ID（確定した順。同じ注文は 1 回）
     */
    public List<UUID> captured() {
        return List.copyOf(captured);
    }

    /**
     * 売上確定を呼ばれた回数を返す。
     *
     * @return 売上確定を呼ばれた回数（失敗と重複を含む）
     */
    public int captureCalls() {
        return captureCalls.get();
    }

    /**
     * 返金した注文 ID を返す。
     *
     * @return 返金した注文 ID（返金した順。同じ注文は 1 回）
     */
    public List<UUID> refunded() {
        return List.copyOf(refunded);
    }

    /**
     * 取り消した与信の参照を返す。
     *
     * @return 取り消した与信の参照（取り消した順）
     */
    public List<String> voided() {
        return List.copyOf(voided);
    }

    /**
     * 作った支払の画面の数を返す。
     *
     * @return 作った支払の画面の数（最後に状態を消してから）
     */
    public int openedPages() {
        return openedPages.get();
    }

    /**
     * テストだけで使う状態を最初に戻す。本番の mock の支払の画面は残す（ID が毎回違うので、他のテストに影響しない）。
     */
    public void reset() {
        openedPages.set(0);
        openFailures.set(0);
        voided.clear();
        voidFailures.set(0);
        expiredAuthorizations.clear();
        captured.clear();
        duringNextCapture.set(null);
        captureCalls.set(0);
        captureFailures.set(0);
        refunded.clear();
        refundFailures.set(0);
        onRefundOrVoid.set(null);
    }

    /**
     * 返金と与信の取消で、呼ばれた時点の処理があれば行う。
     */
    private void runOnRefundOrVoid() {
        var action = onRefundOrVoid.get();
        if (action != null) {
            action.run();
        }
    }

    /**
     * 失敗させる残りの回数を 1 つ減らす。
     *
     * @param remaining 残りの回数
     * @return 残りがあった（今回を失敗させる）なら true
     */
    private static boolean consume(AtomicInteger remaining) {
        return remaining.getAndUpdate(count -> Math.max(count - 1, 0)) > 0;
    }
}
