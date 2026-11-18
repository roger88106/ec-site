package com.ecsite.architecture.sample.entityequality;

import org.jmolecules.ddd.annotation.Entity;
import org.jmolecules.ddd.annotation.Identity;

import java.util.UUID;

/**
 * 同一性の検査の例：record の entity（違反）
 *
 * <p>構成の検査が規則に合う例と違反する例を見分けることを確認するための例であり、本番のコードではない。
 *
 * @param id 識別子
 */
@Entity
public record RecordEntity(@Identity UUID id) {
}
