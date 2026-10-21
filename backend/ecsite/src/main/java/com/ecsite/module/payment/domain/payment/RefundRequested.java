package com.ecsite.module.payment.domain.payment;

import com.ecsite.common.model.base.DomainEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * 返金の依頼。決済の中だけで使う。<br />
 * コミットの後にリスナーが決済サービスで返金する。失敗したら未完了のまま残り、再発行でやり直す。
 *
 * @param eventId    イベント ID
 * @param occurredAt 発生時刻
 * @param paymentId  支払 ID
 */
public record RefundRequested(UUID eventId, Instant occurredAt, UUID paymentId) implements DomainEvent {

    /**
     * イベントを作る。
     *
     * @param occurredAt 発生時刻
     * @param paymentId  支払 ID
     * @return イベント
     */
    public static RefundRequested of(Instant occurredAt, UUID paymentId) {
        return new RefundRequested(UUID.randomUUID(), occurredAt, paymentId);
    }
}
