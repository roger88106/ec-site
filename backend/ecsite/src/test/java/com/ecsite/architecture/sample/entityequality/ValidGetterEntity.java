package com.ecsite.architecture.sample.entityequality;

import org.jmolecules.ddd.annotation.Entity;
import org.jmolecules.ddd.annotation.Identity;

import java.util.Objects;
import java.util.UUID;

/**
 * 同一性の検査の例：getter と canEqual を使い、識別子だけで比べる entity（規則に合う）。Lombok の {@code @EqualsAndHashCode} が作る形を手で書いた
 *
 * <p>構成の検査が規則に合う例と違反する例を見分けることを確認するための例であり、本番のコードではない。
 */
@Entity
public final class ValidGetterEntity {

    /**
     * 識別子
     */
    @Identity
    private final UUID id = UUID.randomUUID();

    /**
     * 名前（状態）
     */
    private final String name = UUID.randomUUID().toString();

    /**
     * 識別子を返す。
     *
     * @return 識別子
     */
    public UUID getId() {
        return id;
    }

    /**
     * 名前を返す。
     *
     * @return 名前
     */
    public String getName() {
        return name;
    }

    /**
     * 比べられる型かを返す。
     *
     * @param other 相手
     * @return 同じ型なら true
     */
    public boolean canEqual(Object other) {
        return other instanceof ValidGetterEntity;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ValidGetterEntity that
                && that.canEqual(this)
                && Objects.equals(getId(), that.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getId());
    }
}
