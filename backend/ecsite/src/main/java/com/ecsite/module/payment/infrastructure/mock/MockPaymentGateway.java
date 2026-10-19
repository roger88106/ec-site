package com.ecsite.module.payment.infrastructure.mock;

import com.ecsite.common.error.exception.SystemException;
import com.ecsite.common.model.value.Money;
import com.ecsite.module.payment.application.port.PaymentGateway;
import com.ecsite.module.payment.application.port.PaymentNotificationReceiver;
import com.ecsite.module.payment.domain.payment.PaymentPage;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * mock の決済サービス（docs/backend/modules/payment.md「mock」）。<br />
 * 支払の画面はバックエンドが出し（{@link MockPaymentPageController}）、与信の成功の通知は HTTP を通さずに通知の受け口へ直接渡す。
 * port と支払の画面の操作だけを持つ。失敗を起こす操作や呼ばれた操作の記録など、テストだけで使う操作は、
 * テストで共有するソースの、mock を包む実装が持つ（本番の jar に入れないため）。
 * <p>
 * 支払の画面の状態はメモリに持つ。mock はローカルとテストだけで使い、1 インスタンスで動かす前提のため。
 * 再起動すると、それまでの画面は見つからなくなる（開発のデータで、支払の期限切れを待つ）。
 * </p>
 */
@Component
@ConditionalOnBooleanProperty(name = "ecsite.payment.mock.enabled")
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
     * 売上を確定した与信の参照。確定した与信は取り消せない
     */
    private final Set<String> capturedAuthorizations = ConcurrentHashMap.newKeySet();

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
        // 売上を確定した与信は、決済サービスが取消を断る（返金で戻す）
        if (capturedAuthorizations.contains(authorizationRef)) {
            throw SystemException.of("mock：売上を確定した与信は取り消せない: " + authorizationRef, null);
        }
    }

    @Override
    public CaptureResult capture(CaptureRequest request) {
        capturedAuthorizations.add(request.authorizationRef());
        return CaptureResult.CAPTURED;
    }

    @Override
    public void refund(RefundRequest request) {
        // mock は返金を受け付けるだけ（冪等キーが同じでも、何度でも受け付ける）
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
     * mock の支払の画面
     *
     * @param orderId   注文 ID
     * @param amount    金額
     * @param returnUrl 戻る先
     * @param status    画面の状態
     */
    public record Page(UUID orderId, Money amount, String returnUrl, PageStatus status) {

        /**
         * 状態だけを変えた画面を返す。
         *
         * @param status 新しい状態
         * @return 画面
         */
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
