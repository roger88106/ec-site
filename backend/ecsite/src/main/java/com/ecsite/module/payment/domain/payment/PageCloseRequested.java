package com.ecsite.module.payment.domain.payment;

import com.ecsite.common.model.base.DomainEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * 支払画面を閉じる依頼
 *
 * <p>取消で支払なしにした支払をコミットした後に、支払画面を閉じるためのイベント。決済のモジュールの中だけで使う。
 * 支払と同じトランザクションで記録されるので、決済サービスが失敗しても、支払を戻さずに再発行でやり直せる。
 *
 * @param eventId    イベント ID
 * @param occurredAt 発生時刻
 * @param paymentId  支払 ID
 */
public record PageCloseRequested(UUID eventId, Instant occurredAt, UUID paymentId) implements DomainEvent {

    /**
     * イベントを作る。
     *
     * @param occurredAt 発生時刻
     * @param paymentId  支払 ID
     * @return イベント
     */
    public static PageCloseRequested of(Instant occurredAt, UUID paymentId) {
        return new PageCloseRequested(UUID.randomUUID(), occurredAt, paymentId);
    }
}
