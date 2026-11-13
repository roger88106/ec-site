package com.ecsite.module.payment.infrastructure.mock;

import com.ecsite.common.error.exception.SystemException;
import com.ecsite.common.model.value.Money;
import com.ecsite.module.payment.application.port.PaymentGateway;
import com.ecsite.module.payment.application.port.PaymentNotificationReceiver;
import com.ecsite.module.payment.domain.payment.PaymentPage;
import com.ecsite.module.payment.infrastructure.ConditionalOnPaymentMock;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * mock の決済サービス
 *
 * <p>支払画面はバックエンドが出し、与信の成功の通知は HTTP を通さずに port へ直接渡す。
 * 失敗を起こす操作や呼ばれた操作の記録など、テストだけで使う操作は持たない（本番の jar に入れないため）。
 *
 * <p>支払画面の状態はメモリに持つ。mock はローカルとテストだけで使い、1 インスタンスで動かす前提のため。
 * 再起動すると、それまでの画面は見つからなくなる（開発のデータで、支払の期限切れを待つ）。
 */
@Component
@ConditionalOnPaymentMock
@RequiredArgsConstructor
public class MockPaymentGateway implements PaymentGateway {

    /**
     * 支払画面の ID の接頭辞
     */
    private static final String PAGE_ID_PREFIX = "mock_page_";

    /**
     * 与信の参照の接頭辞
     */
    private static final String AUTHORIZATION_PREFIX = "mock_auth_";

    /**
     * 通知の port
     */
    private final PaymentNotificationReceiver receiver;

    /**
     * mock の設定
     */
    private final MockPaymentProperties properties;

    /**
     * 時計
     *
     * <p>支払を済ませた時刻を記録する。
     */
    private final Clock clock;

    /**
     * 支払画面 ID → 画面
     */
    private final Map<String, Page> pages = new ConcurrentHashMap<>();

    /**
     * 売上を確定した与信の参照
     *
     * <p>確定した与信は取り消せない。
     */
    private final Set<String> capturedAuthorizations = ConcurrentHashMap.newKeySet();

// ============================== 決済サービス ==============================

    /**
     * {@inheritDoc}
     */
    @Override
    public PaymentPage openPage(PageRequest request) {
        var id = PAGE_ID_PREFIX + UUID.randomUUID();
        pages.put(id, new Page(request.orderId(), request.amount(), request.returnUrl(), PageStatus.OPEN, null));
        return new PaymentPage(id, properties.baseUrl() + "/mock-payment/" + id);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CloseResult closePage(String pageId) {
        var page = pages.computeIfPresent(pageId,
                (id, current) -> current.status() == PageStatus.PAID ? current : current.with(PageStatus.CLOSED));
        return page != null && page.status() == PageStatus.PAID ? CloseResult.ALREADY_PAID : CloseResult.CLOSED;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Authorization fetchAuthorization(String pageId) {
        var page = pages.get(pageId);
        if (page == null || page.status() != PageStatus.PAID || page.paidAt() == null) {
            throw SystemException.of("mock：支払の済んでいない支払画面の与信は取得できない: pageId=" + pageId, null);
        }
        return new Authorization(authorizationRefOf(pageId), page.paidAt());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public VoidResult voidAuthorization(String authorizationRef) {
        // 売上を確定した与信は取り消せない（返金で戻す）
        return capturedAuthorizations.contains(authorizationRef) ? VoidResult.ALREADY_CAPTURED : VoidResult.VOIDED;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CaptureResult capture(CaptureRequest request) {
        capturedAuthorizations.add(request.authorizationRef());
        return CaptureResult.CAPTURED;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void refund(RefundRequest request) {
        // mock は返金を受け付けるだけ（冪等キーが同じでも、何度でも受け付ける）
    }

// ============================== 支払画面の操作 ==============================

    /**
     * 支払う：決済サービスで支払を済ませ、与信の成功を通知する。
     *
     * @param pageId 支払画面 ID
     */
    public void pay(String pageId) {
        markPaid(pageId);
        notifyAuthorized(pageId);
    }

    /**
     * 決済サービスで支払を済ませ、まだ通知しない。
     *
     * @param pageId 支払画面 ID
     */
    public void markPaid(String pageId) {
        pages.computeIfPresent(pageId, (id, current) -> current.paid(clock.instant()));
    }

    /**
     * 与信の成功を通知する。
     *
     * <p>重ねて呼ぶと、重ねて届いた通知になる。
     *
     * @param pageId 支払画面 ID
     */
    public void notifyAuthorized(String pageId) {
        receiver.authorized(pageId, authorizationRefOf(pageId));
    }

    /**
     * 支払画面を返す。
     *
     * @param pageId 支払画面 ID
     * @return 画面。知らない画面なら空
     */
    public Optional<Page> page(String pageId) {
        return Optional.ofNullable(pages.get(pageId));
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
        return AUTHORIZATION_PREFIX + pageId.substring(PAGE_ID_PREFIX.length());
    }

    /**
     * mock の支払画面
     *
     * @param orderId   注文 ID
     * @param amount    金額
     * @param returnUrl 戻る先
     * @param status    画面の状態
     * @param paidAt    支払を済ませた最初の時刻。済んでいなければ null
     */
    public record Page(UUID orderId, Money amount, String returnUrl, PageStatus status, @Nullable Instant paidAt) {

        /**
         * 状態だけを変えた画面を返す。
         *
         * @param status 新しい状態
         * @return 画面
         */
        Page with(PageStatus status) {
            return new Page(orderId, amount, returnUrl, status, paidAt);
        }

        /**
         * 支払を済ませた画面を返す。
         *
         * <p>最初の 1 回の時刻を残す（重ねて支払っても変えない）。
         *
         * @param now 現在時刻
         * @return 画面
         */
        Page paid(Instant now) {
            return new Page(orderId, amount, returnUrl, PageStatus.PAID, paidAt == null ? now : paidAt);
        }
    }

    /**
     * mock の支払画面の状態
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
