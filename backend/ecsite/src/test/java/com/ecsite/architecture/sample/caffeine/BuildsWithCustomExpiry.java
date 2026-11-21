package com.ecsite.architecture.sample.caffeine;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;

import java.time.Duration;

/**
 * Caffeine のキャッシュの期限の検査の例：要素ごとの期限を指定して作る（規則に合う）
 *
 * <p>構成の検査が規則に合う例を違反にしないことを確認するための例であり、本番のコードではない。
 */
public final class BuildsWithCustomExpiry {

    /**
     * 例の関数。
     *
     * @return キャッシュ
     */
    Cache<String, String> build() {
        return Caffeine.newBuilder()
                .maximumSize(10)
                .expireAfter(Expiry.<String, String>creating((key, value) -> Duration.ofMinutes(1)))
                .build();
    }
}
