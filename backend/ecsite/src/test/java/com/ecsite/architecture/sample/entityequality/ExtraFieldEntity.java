package com.ecsite.architecture.sample.entityequality;

import org.jmolecules.ddd.annotation.AggregateRoot;
import org.jmolecules.ddd.annotation.Identity;

import java.util.Objects;
import java.util.UUID;

/**
 * 同一性の検査の例：手書きの equals と hashCode が、識別子以外の項目も比べる集約（違反）
 *
 * <p>構成の検査が規則に合う例と違反する例を見分けることを確認するための例であり、本番のコードではない。
 */
@AggregateRoot
public final class ExtraFieldEntity {

    /**
     * 識別子
     */
    @Identity
    private final UUID id = UUID.randomUUID();

    /**
     * 名前（状態）
     */
    private final String name = UUID.randomUUID().toString();

    @Override
    public boolean equals(Object other) {
        return other instanceof ExtraFieldEntity that && Objects.equals(id, that.id) && Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name);
    }
}
