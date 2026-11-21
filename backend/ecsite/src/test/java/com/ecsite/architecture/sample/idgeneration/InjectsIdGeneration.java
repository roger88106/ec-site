package com.ecsite.architecture.sample.idgeneration;

import java.util.UUID;
import java.util.function.Supplier;
import java.util.random.RandomGenerator;

/**
 * 識別子と乱数の生成の検査の例：注入した識別子の生成と乱数を使う（規則に合う）
 *
 * <p>構成の検査が規則に合う例を違反にしないことを確認するための例であり、本番のコードではない。
 */
public final class InjectsIdGeneration {

    /**
     * 注入した識別子の生成
     */
    private final Supplier<UUID> ids;

    /**
     * 注入した乱数
     */
    private final RandomGenerator random;

    /**
     * 例を作る。
     *
     * @param ids    識別子の生成
     * @param random 乱数
     */
    public InjectsIdGeneration(Supplier<UUID> ids, RandomGenerator random) {
        this.ids = ids;
        this.random = random;
    }

    /**
     * 例の関数。
     *
     * @return 識別子と乱数の組
     */
    String create() {
        return ids.get() + "-" + random.nextInt(10);
    }
}
