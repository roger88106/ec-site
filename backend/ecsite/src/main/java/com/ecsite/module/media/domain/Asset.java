package com.ecsite.module.media.domain;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.Accessors;
import org.jmolecules.ddd.annotation.AggregateRoot;
import org.jmolecules.ddd.annotation.Identity;

import java.time.Instant;
import java.util.UUID;

/**
 * アセット
 *
 * <p>検証して配信のバケットに置いた 1 つのファイル。
 */
@Getter
@Accessors(fluent = true)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@AllArgsConstructor
@AggregateRoot
public final class Asset {

    /**
     * アセット ID
     */
    @Identity
    @EqualsAndHashCode.Include
    private final UUID id;

    /**
     * 形式
     */
    private final ImageFormat format;

    /**
     * 配信のバケットのキー
     */
    private final String storageKey;

    /**
     * 確定した時刻
     */
    private final Instant createdAt;
}
