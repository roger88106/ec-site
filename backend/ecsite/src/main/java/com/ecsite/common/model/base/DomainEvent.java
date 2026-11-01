package com.ecsite.common.model.base;

import java.time.Instant;
import java.util.UUID;

/**
 * ドメインイベント。<br />
 * イベントは過去形で命名する（バックエンドのアーキテクチャの「モジュール間の連携」）。受け取る側は同じイベントが複数回届いても結果が変わらないように作る
 * （at-least-once 配信。バックエンドのアーキテクチャの「水平スケール」）。そのための識別子として {@link #eventId()} を持つ。
 * <p>
 * 発生時刻は {@code Instant.now()} で取らず、アプリの {@link java.time.Clock} から得た時刻を受け取る（バックエンドのアーキテクチャの「水平スケール」）。
 * </p>
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
