package com.ecsite.architecture.sample.identifier.module.sample.event;

import com.ecsite.common.model.base.DomainEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * 識別子の型の検査の例：イベントは、イベント ID だけ UUID を持ってよい
 *
 * <p>構成の検査が、イベント ID を違反にせず、ほかの識別子の UUID を違反にすることを確認するための例であり、本番のコードではない。
 *
 * @param eventId    イベント ID
 * @param occurredAt 発生時刻
 * @param orderId    注文の ID（UUID なので規則に合わない）
 */
public record ExampleEvent(UUID eventId, Instant occurredAt, UUID orderId) implements DomainEvent {
}
