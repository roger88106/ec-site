package com.ecsite.module.payment.domain.payment;

import com.ecsite.common.model.base.DomainEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * 返金の依頼
 *
 * <p>決済モジュールの中だけで使うイベント。
 *
 * <p>※モジュールの中でも決済サービスを直接呼ばずにイベントにするのは、返金を、支払を返金待ちにするトランザクションの
 * コミットの後に回すため。イベントは支払と同じトランザクションで記録されるので、決済サービスが失敗しても、
 * 返金待ちにした支払を戻さずに、再発行でやり直せる。
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
