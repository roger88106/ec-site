package com.ecsite.module.order.application.port;

import com.ecsite.common.model.value.Money;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 決済の腐敗防止層（docs/backend/modules/order.md「支払」）
 */
public interface PaymentDirectory {

    /**
     * 注文の支払（未払い）を作る。呼び出し元のトランザクションに参加する。
     *
     * @param orderId   注文 ID
     * @param amount    金額（総額）
     * @param orderedAt 注文日時（支払の期限の起点）
     */
    void create(UUID orderId, Money amount, Instant orderedAt);

    /**
     * 支払の画面の URL を返す。決済サービスを呼ぶので、トランザクションの外で呼ぶ。
     *
     * @param orderId 注文 ID
     * @return 支払の画面の URL。支払えなければ空
     */
    Optional<String> pageUrl(UUID orderId);

    /**
     * 注文の支払の売上を確定する。売上確定済みなら、決済サービスを呼ばずに成功する。決済サービスを呼ぶので、トランザクションの外で呼ぶ。
     *
     * @param orderId 注文 ID
     * @throws com.ecsite.common.error.exception.BusinessException 与信の期限が切れている、売上を確定できる状態でない
     * @throws com.ecsite.common.error.exception.SystemException   決済サービスに接続できない
     */
    void capture(UUID orderId);

    /**
     * 注文のキャンセルに合わせて支払を取り消す。呼び出し元のトランザクションに参加し、与信の取消・返金の結果は待たない。
     *
     * @param orderId 注文 ID
     */
    void cancel(UUID orderId);

    /**
     * 注文の支払をまとめて返す。
     *
     * @param orderIds 注文 ID
     * @return 注文 ID → 支払。支払のない注文（決済を入れる前の注文）は含まない
     */
    Map<UUID, Payment> find(Collection<UUID> orderIds);

    /**
     * 注文の与信期限と、期限が近いかをまとめて返す。
     *
     * @param orderIds 注文 ID
     * @return 注文 ID → 与信期限。与信を受けていない注文は含まない
     */
    Map<UUID, Authorization> findAuthorizations(Collection<UUID> orderIds);

    /**
     * 与信期限が近い注文 ID を返す。期限を過ぎたものを含む。売上確定の前の注文だけが対象。
     *
     * @return 注文 ID
     */
    List<UUID> findExpiringOrderIds();

    /**
     * 注文の支払
     *
     * @param status   支払の状態
     * @param deadline 支払の期限
     * @param payable  支払えるか（未払いで、支払の期限の前。決済が判定する）
     */
    record Payment(Status status, Instant deadline, boolean payable) {
    }

    /**
     * 注文の与信期限
     *
     * @param deadline 与信期限
     * @param expiring 期限が近いか（期限を過ぎたものを含む。売上確定の前だけ）
     */
    record Authorization(Instant deadline, boolean expiring) {
    }

    /**
     * 支払の状態（docs/backend/modules/payment.md「状態遷移」）
     */
    enum Status {

        /**
         * 未払い
         */
        UNPAID,

        /**
         * 与信済み
         */
        AUTHORIZED,

        /**
         * 与信の取消待ち
         */
        VOID_PENDING,

        /**
         * 与信の取消済み
         */
        VOIDED,

        /**
         * 支払なし
         */
        NOT_PAID,

        /**
         * 売上確定済み
         */
        CAPTURED,

        /**
         * 与信期限切れ
         */
        AUTHORIZATION_EXPIRED,

        /**
         * 返金待ち
         */
        REFUND_PENDING,

        /**
         * 返金済み
         */
        REFUNDED,
    }
}
