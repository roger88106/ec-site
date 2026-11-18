package com.ecsite.architecture.sample.entityequality;

import org.jmolecules.ddd.annotation.AggregateRoot;

import java.util.Objects;
import java.util.UUID;

/**
 * 同一性の検査の例：Identity の項目がない集約（違反）
 *
 * <p>構成の検査が規則に合う例と違反する例を見分けることを確認するための例であり、本番のコードではない。
 */
@AggregateRoot
public final class NoIdentityAggregate {

    /**
     * 識別子（Identity を付けない）
     */
    private final UUID id = UUID.randomUUID();

    @Override
    public boolean equals(Object other) {
        return other instanceof NoIdentityAggregate that && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
