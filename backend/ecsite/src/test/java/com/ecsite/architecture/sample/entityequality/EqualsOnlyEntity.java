package com.ecsite.architecture.sample.entityequality;

import org.jmolecules.ddd.annotation.Entity;
import org.jmolecules.ddd.annotation.Identity;

import java.util.Objects;
import java.util.UUID;

/**
 * 同一性の検査の例：equals だけを宣言し、hashCode を宣言しない entity（違反）
 *
 * <p>構成の検査が規則に合う例と違反する例を見分けることを確認するための例であり、本番のコードではない。
 */
@Entity
public final class EqualsOnlyEntity {

    /**
     * 識別子
     */
    @Identity
    private final UUID id = UUID.randomUUID();

    // 構成の検査の違反の例。規則が見つける「hashCode のない equals」を、そのまま書く
    @SuppressWarnings("EqualsHashCode")
    @Override
    public boolean equals(Object other) {
        return other instanceof EqualsOnlyEntity that && Objects.equals(id, that.id);
    }
}
