package com.ecsite.architecture.sample.caffeine;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.time.Duration;
import java.util.List;

/**
 * Caffeine のキャッシュの期限の検査の例：1 つのメソッドで 2 つのキャッシュを作り、片方だけに期限を指定する（規則に合わない）
 *
 * <p>構成の検査が違反を見つけられることを確認するための例であり、本番のコードではない。
 */
public final class BuildsTwoCachesWithOneExpiry {

    /**
     * 例の関数。
     *
     * @return キャッシュ
     */
    List<Cache<String, String>> build() {
        Cache<String, String> withExpiry = Caffeine.newBuilder().maximumSize(10).expireAfterWrite(Duration.ofMinutes(1)).build();
        Cache<String, String> withoutExpiry = Caffeine.newBuilder().maximumSize(10).build();
        return List.of(withExpiry, withoutExpiry);
    }
}
