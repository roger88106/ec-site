package com.ecsite.module.payment.domain.payment;

import com.ecsite.common.model.base.DomainEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * 与信の取消の依頼
 *
 * <p>決済モジュールの中だけで使うイベント。
 *
 * <p>※モジュールの中でも決済サービスを直接呼ばずにイベントにするのは、与信の取消を、支払を与信の取消待ちにする
 * トランザクションのコミットの後に回すため。イベントは支払と同じトランザクションで記録されるので、決済サービスが
 * 失敗しても、与信の取消待ちにした支払を戻さずに、再発行でやり直せる。
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
