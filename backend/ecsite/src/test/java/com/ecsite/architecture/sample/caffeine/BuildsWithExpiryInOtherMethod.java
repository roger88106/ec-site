package com.ecsite.architecture.sample.caffeine;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.time.Duration;

/**
 * Caffeine のキャッシュの期限の検査の例：期限を指定するキャッシュと同じクラスで、期限を指定しないキャッシュを作る（規則に合わない）
 *
 * <p>構成の検査が違反を見つけられることを確認するための例であり、本番のコードではない。
 */
public final class BuildsWithExpiryInOtherMethod {

    /**
     * 例の関数：期限を指定して作る。
     *
     * @return キャッシュ
     */
    Cache<String, String> buildWithExpiry() {
        return Caffeine.newBuilder().maximumSize(10).expireAfterWrite(Duration.ofMinutes(1)).build();
    }

    /**
     * 例の関数：期限を指定せずに作る。
     *
     * @return キャッシュ
     */
    Cache<String, String> buildWithoutExpiry() {
        return Caffeine.newBuilder().maximumSize(10).build();
    }
}
