package com.ecsite.common.model.base;

import java.time.Instant;
import java.util.UUID;

/**
 * ドメインイベント。<br />
 * イベントは過去形で命名する（constitution 6）。受け取る側は同じイベントが複数回届いても結果が変わらないように作る
 * （at-least-once 配信。constitution 5-7）。そのための識別子として {@link #eventId()} を持つ。
 * <p>
 * 発生時刻は {@code Instant.now()} で取らず、アプリの {@link java.time.Clock} から得た時刻を受け取る（constitution 5-6）。
 * </p>
 */
public interface DomainEvent extends org.jmolecules.event.types.DomainEvent {

    /**
     * @return イベント ID
     */
    UUID eventId();

    /**
     * @return イベント発生時刻
     */
    Instant occurredAt();
}
