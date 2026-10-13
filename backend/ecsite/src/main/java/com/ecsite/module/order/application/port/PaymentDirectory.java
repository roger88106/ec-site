package com.ecsite.module.order.application.port;

import com.ecsite.common.model.value.Money;

import java.time.Instant;
import java.util.Collection;
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
     * 注文のキャンセルに合わせて支払を取り消す。呼び出し元のトランザクションに参加し、与信の取消の結果は待たない。
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
     * 注文の支払
     *
     * @param status   支払の状態
     * @param deadline 支払の期限
     */
    record Payment(Status status, Instant deadline) {
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
    }
}
