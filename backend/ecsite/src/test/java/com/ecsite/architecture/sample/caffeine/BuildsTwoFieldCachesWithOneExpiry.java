package com.ecsite.architecture.sample.caffeine;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.time.Duration;

/**
 * Caffeine のキャッシュの期限の検査の例：項目の初期化子で 2 つのキャッシュを作り、片方だけに期限を指定する（規則に合わない）
 *
 * <p>構成の検査が違反を見つけられることを確認するための例であり、本番のコードではない。
 */
public final class BuildsTwoFieldCachesWithOneExpiry {

    /**
     * 期限を指定するキャッシュ
     */
    final Cache<String, String> withExpiry = Caffeine.newBuilder().maximumSize(10).expireAfterWrite(Duration.ofMinutes(1)).build();

    /**
     * 期限を指定しないキャッシュ
     */
    final Cache<String, String> withoutExpiry = Caffeine.newBuilder().maximumSize(10).build();
}
