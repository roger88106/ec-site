package com.ecsite.module.payment.domain.payment;

import com.ecsite.common.model.base.DomainEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * 与信の取消の依頼
 *
 * <p>与信の取消待ちにした支払のコミットの後に与信を取り消すための、決済モジュールの中だけで使うイベント。
 * 支払と同じトランザクションで記録されるので、決済サービスが失敗しても、支払を戻さずに再発行でやり直せる。
 *
 * @param eventId    イベント ID
 * @param occurredAt 発生時刻
 * @param paymentId  支払 ID
 */
public record VoidRequested(UUID eventId, Instant occurredAt, UUID paymentId) implements DomainEvent {

    /**
     * イベントを作る。
     *
     * @param occurredAt 発生時刻
     * @param paymentId  支払 ID
     * @return イベント
     */
    public static VoidRequested of(Instant occurredAt, UUID paymentId) {
        return new VoidRequested(UUID.randomUUID(), occurredAt, paymentId);
    }
}
