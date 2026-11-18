package com.ecsite.architecture.sample.entityequality;

/**
 * 同一性の検査の例：基底クラスを継ぐだけで、注釈がなく、equals も宣言しない集約（違反）
 *
 * <p>構成の検査が規則に合う例と違反する例を見分けることを確認するための例であり、本番のコードではない。
 */
public final class InheritingAggregate extends com.ecsite.common.model.base.AggregateRoot {

    @Override
    public long version() {
        return 0;
    }
}
