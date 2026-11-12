package com.ecsite.common.model.base;

import java.time.Instant;
import java.util.UUID;

/**
 * ドメインイベント
 *
 * <p>イベントは過去形で命名する。同じイベントが複数回届きうる（at-least-once 配信）ので、重複を見分ける識別子として {@link #eventId()} を持つ。
 * 発生時刻は {@code Instant.now()} で取らず、アプリの {@link java.time.Clock} から得た時刻にする。
 */
public interface DomainEvent extends org.jmolecules.event.types.DomainEvent {

    /**
     * イベントの ID を返す。
     *
     * @return イベント ID
     */
    UUID eventId();

    /**
     * イベントの発生時刻を返す。
     *
     * @return イベント発生時刻
     */
    Instant occurredAt();
}
