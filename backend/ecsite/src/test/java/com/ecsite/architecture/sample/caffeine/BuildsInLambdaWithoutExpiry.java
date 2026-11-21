package com.ecsite.architecture.sample.caffeine;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.util.function.Supplier;

/**
 * Caffeine のキャッシュの期限の検査の例：ラムダの中で、期限を指定せずに作る（規則に合わない）
 *
 * <p>構成の検査が違反を見つけられることを確認するための例であり、本番のコードではない。
 */
public final class BuildsInLambdaWithoutExpiry {

    /**
     * 例の関数。
     *
     * @return キャッシュを作る関数
     */
    Supplier<Cache<String, String>> factory() {
        return () -> Caffeine.newBuilder().maximumSize(10).build();
    }
}
