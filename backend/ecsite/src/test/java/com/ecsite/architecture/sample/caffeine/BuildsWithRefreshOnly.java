package com.ecsite.architecture.sample.caffeine;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;

import java.time.Duration;

/**
 * Caffeine のキャッシュの期限の検査の例：再読み込みの間隔だけを指定して作る（規則に合わない）
 *
 * <p>構成の検査が違反を見つけられることを確認するための例であり、本番のコードではない。
 */
public final class BuildsWithRefreshOnly {

    /**
     * 例の関数。
     *
     * @return キャッシュ
     */
    LoadingCache<String, String> build() {
        return Caffeine.newBuilder().maximumSize(10).refreshAfterWrite(Duration.ofMinutes(1)).build(key -> key);
    }
}
