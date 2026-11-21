package com.ecsite.architecture.sample.caffeine;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

/**
 * Caffeine のキャッシュの期限の検査の例：設定の文字列から作る（規則に合わない）
 *
 * <p>構成の検査が違反を見つけられることを確認するための例であり、本番のコードではない。
 */
public final class BuildsFromSpec {

    /**
     * 例の関数。
     *
     * @return キャッシュ
     */
    Cache<Object, Object> build() {
        return Caffeine.from("maximumSize=10,expireAfterWrite=1m").build();
    }
}
