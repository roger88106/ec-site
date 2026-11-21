package com.ecsite.architecture.sample.caffeine;

import com.github.benmanes.caffeine.cache.AsyncCache;
import com.github.benmanes.caffeine.cache.Caffeine;

/**
 * Caffeine のキャッシュの期限の検査の例：期限を指定せずに非同期のキャッシュを作る（規則に合わない）
 *
 * <p>構成の検査が違反を見つけられることを確認するための例であり、本番のコードではない。
 */
public final class BuildsAsyncWithoutExpiry {

    /**
     * 例の関数。
     *
     * @return キャッシュ
     */
    AsyncCache<String, String> build() {
        return Caffeine.newBuilder().maximumSize(10).buildAsync();
    }
}
