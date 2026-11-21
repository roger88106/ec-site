package com.ecsite.architecture.sample.caffeine;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.util.concurrent.TimeUnit;

/**
 * Caffeine のキャッシュの期限の検査の例：最後の読み書きからの期限を指定して作る（規則に合う）
 *
 * <p>構成の検査が規則に合う例を違反にしないことを確認するための例であり、本番のコードではない。
 */
public final class BuildsWithExpireAfterAccess {

    /**
     * 例の関数。
     *
     * @return キャッシュ
     */
    Cache<String, String> build() {
        return Caffeine.newBuilder().maximumSize(10).expireAfterAccess(1, TimeUnit.MINUTES).build();
    }
}
