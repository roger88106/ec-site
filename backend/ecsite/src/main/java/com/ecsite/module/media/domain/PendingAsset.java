package com.ecsite.module.media.domain;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.experimental.Accessors;
import org.jmolecules.ddd.annotation.AggregateRoot;
import org.jmolecules.ddd.annotation.Identity;

import java.time.Instant;
import java.util.UUID;

/**
 * 仮のアセット
 *
 * <p>用意して配信のバケットに置き、まだ登録も破棄もしていない 1 つのファイル。登録すると、同じ ID のアセットになる。
 */
@Getter
@Accessors(fluent = true)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@AllArgsConstructor
@AggregateRoot
public final class PendingAsset {

    /**
     * アセット ID
     */
    @Identity
    @EqualsAndHashCode.Include
    @NonNull
    private final UUID id;

    /**
     * 配信のバケットのキー
     */
    @NonNull
    private final String storageKey;

    /**
     * 用意した時刻
     */
    @NonNull
    private final Instant preparedAt;
}
