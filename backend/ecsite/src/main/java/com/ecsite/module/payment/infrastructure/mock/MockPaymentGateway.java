package com.ecsite.module.payment.infrastructure.mock;

import com.ecsite.common.error.exception.SystemException;
import com.ecsite.common.model.value.Money;
import com.ecsite.module.payment.application.port.PaymentGateway;
import com.ecsite.module.payment.application.port.PaymentNotificationReceiver;
import com.ecsite.module.payment.domain.payment.PaymentPage;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * mock の決済サービス（docs/backend/modules/payment.md「mock」）。<br />
 * 支払の画面はバックエンドが出し（{@link MockPaymentPageController}）、与信の成功の通知は HTTP を通さずに通知の受け口へ直接渡す。
 * テストのコードからは、公開のメソッドで、通知の遅れ・重複・順序の入れ替わりや、取消・売上確定・返金の失敗を起こせる。
 * <p>
 * 支払の画面の状態はメモリに持つ。mock はローカルとテストだけで使い、1 インスタンスで動かす前提のため。
 * 再起動すると、それまでの画面は見つからなくなる（開発のデータで、支払の期限切れを待つ）。
 * </p>
 */
@Component
@ConditionalOnProperty(name = "ecsite.payment.gateway", havingValue = "mock", matchIfMissing = true)
@EnableConfigurationProperties(MockPaymentProperties.class)
@RequiredArgsConstructor
public class MockPaymentGateway implements PaymentGateway {

    /**
     * 支払の画面の ID の接頭辞
     */
    private static final String PAGE_ID_PREFIX = "mock_page_";

    /**
     * 与信の参照の接頭辞
     */
    private static final String AUTHORIZATION_PREFIX = "mock_auth_";

    /**
     * 通知の受け口
     */
    private final PaymentNotificationReceiver receiver;

    /**
     * mock の設定
     */
    private final MockPaymentProperties properties;

    /**
     * 支払の画面 ID → 画面
     */
    private final Map<String, Page> pages = new ConcurrentHashMap<>();

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
     * 売上を確定した与信の参照。確定した与信は取り消せない
     */
    private final Set<String> capturedAuthorizations = ConcurrentHashMap.newKeySet();

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

// ============================== 決済サービス ==============================

    @Override
    public PaymentPage openPage(PageRequest request) {
        var id = PAGE_ID_PREFIX + UUID.randomUUID();
        pages.put(id, new Page(request.orderId(), request.amount(), request.returnUrl(), PageStatus.OPEN));
        return new PaymentPage(id, properties.baseUrl() + "/mock-payment/" + id);
    }

    @Override
    public CloseResult closePage(String pageId) {
        var page = pages.computeIfPresent(pageId,
                (id, current) -> current.status() == PageStatus.PAID ? current : current.with(PageStatus.CLOSED));
        return page != null && page.status() == PageStatus.PAID ? CloseResult.ALREADY_PAID : CloseResult.CLOSED;
    }

    @Override
    public void voidAuthorization(String authorizationRef) {
        if (voidFailures.getAndUpdate(remaining -> Math.max(remaining - 1, 0)) > 0) {
            throw SystemException.of("mock：与信の取消に失敗させる: " + authorizationRef, null);
        }
        // 売上を確定した与信は、決済サービスが取消を断る（返金で戻す）
        if (capturedAuthorizations.contains(authorizationRef)) {
            throw SystemException.of("mock：売上を確定した与信は取り消せない: " + authorizationRef, null);
        }
        voided.add(authorizationRef);
    }

    @Override
    public CaptureResult capture(CaptureRequest request) {
        captureCalls.incrementAndGet();
        if (captureFailures.getAndUpdate(remaining -> Math.max(remaining - 1, 0)) > 0) {
            return CaptureResult.UNREACHABLE;
        }
        if (expiredAuthorizations.contains(request.authorizationRef())) {
            return CaptureResult.AUTHORIZATION_EXPIRED;
        }
        // 冪等キー（注文 ID）が同じなら、確定は 1 回だけ
        if (!captured.contains(request.orderId())) {
            captured.add(request.orderId());
        }
        capturedAuthorizations.add(request.authorizationRef());
        var during = duringNextCapture.getAndSet(null);
        if (during != null) {
            during.run();
        }
        return CaptureResult.CAPTURED;
    }

    @Override
    public void refund(RefundRequest request) {
        if (refundFailures.getAndUpdate(remaining -> Math.max(remaining - 1, 0)) > 0) {
            throw SystemException.of("mock：返金に失敗させる: " + request.orderId(), null);
        }
        // 冪等キー（注文 ID）が同じなら、返金は 1 回だけ
        if (!refunded.contains(request.orderId())) {
            refunded.add(request.orderId());
        }
    }

// ============================== 支払の画面の操作（画面とテストから） ==============================

    /**
     * 支払う：決済サービスで支払を済ませ、与信の成功を通知する。
     *
     * @param pageId 支払の画面 ID
     */
    public void pay(String pageId) {
        markPaid(pageId);
        notifyAuthorized(pageId);
    }

    /**
     * 決済サービスで支払を済ませるが、まだ通知しない（通知の遅れ）。
     *
     * @param pageId 支払の画面 ID
     */
    public void markPaid(String pageId) {
        pages.computeIfPresent(pageId, (id, current) -> current.with(PageStatus.PAID));
    }

    /**
     * 与信の成功を通知する。重ねて呼ぶと、重ねて届いた通知になる。
     *
     * @param pageId 支払の画面 ID
     */
    public void notifyAuthorized(String pageId) {
        receiver.authorized(pageId, authorizationRefOf(pageId));
    }

    /**
     * 支払の画面を返す。
     *
     * @param pageId 支払の画面 ID
     * @return 画面。知らない画面なら空
     */
    public Optional<Page> page(String pageId) {
        return Optional.ofNullable(pages.get(pageId));
    }

// ============================== テストからの操作 ==============================

    /**
     * 与信の参照を返す。支払の画面ごとに決まる。
     *
     * @param pageId 支払の画面 ID
     * @return 与信の参照
     */
    public String authorizationRefOf(String pageId) {
        return AUTHORIZATION_PREFIX + pageId.substring(PAGE_ID_PREFIX.length());
    }

    /**
     * 次の与信の取消を、指定した回数だけ失敗させる。
     *
     * @param times 失敗させる回数
     */
    public void failNextVoids(int times) {
        voidFailures.set(times);
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
     * @return 売上を確定した注文 ID（確定した順。同じ注文は 1 回）
     */
    public List<UUID> captured() {
        return List.copyOf(captured);
    }

    /**
     * @return 売上確定を呼ばれた回数（失敗と重複を含む）
     */
    public int captureCalls() {
        return captureCalls.get();
    }

    /**
     * @return 返金した注文 ID（返金した順。同じ注文は 1 回）
     */
    public List<UUID> refunded() {
        return List.copyOf(refunded);
    }

    /**
     * @return 取り消した与信の参照（取り消した順）
     */
    public List<String> voided() {
        return List.copyOf(voided);
    }

    /**
     * @return 作った支払の画面の数
     */
    public int openedPages() {
        return pages.size();
    }

    /**
     * 状態を最初に戻す。
     */
    public void reset() {
        pages.clear();
        voided.clear();
        voidFailures.set(0);
        expiredAuthorizations.clear();
        captured.clear();
        capturedAuthorizations.clear();
        duringNextCapture.set(null);
        captureCalls.set(0);
        captureFailures.set(0);
        refunded.clear();
        refundFailures.set(0);
    }

    /**
     * mock の支払の画面
     *
     * @param orderId   注文 ID
     * @param amount    金額
     * @param returnUrl 戻る先
     * @param status    画面の状態
     */
    public record Page(UUID orderId, Money amount, String returnUrl, PageStatus status) {

        Page with(PageStatus status) {
            return new Page(orderId, amount, returnUrl, status);
        }
    }

    /**
     * mock の支払の画面の状態
     */
    public enum PageStatus {

        /**
         * 開いている
         */
        OPEN,

        /**
         * 支払が済んだ
         */
        PAID,

        /**
         * 閉じた（期限切れ）
         */
        CLOSED,
    }
}
