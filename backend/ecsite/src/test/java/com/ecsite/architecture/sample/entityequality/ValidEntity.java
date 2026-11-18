package com.ecsite.architecture.sample.entityequality;

import org.jmolecules.ddd.annotation.Entity;
import org.jmolecules.ddd.annotation.Identity;

import java.util.Objects;
import java.util.UUID;

/**
 * 同一性の検査の例：規則に合う entity（class、equals と hashCode を宣言、Identity あり）
 *
 * <p>構成の検査が規則に合う例と違反する例を見分けることを確認するための例であり、本番のコードではない。
 */
@Entity
public final class ValidEntity {

    /**
     * 識別子
     */
    @Identity
    private final UUID id = UUID.randomUUID();

    @Override
    public boolean equals(Object other) {
        return other instanceof ValidEntity that && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
