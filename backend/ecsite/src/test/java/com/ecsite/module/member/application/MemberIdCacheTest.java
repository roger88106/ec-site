package com.ecsite.module.member.application;

import com.github.benmanes.caffeine.cache.Ticker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 会員 ID のキャッシュの単体テスト
 *
 * <p>期限と件数の上限が守られることを、時計を進めて確認する。追い出しは executor を同期にして確認する。
 */
class MemberIdCacheTest {

    // ============================== 定数 ==============================

    /**
     * 期限
     */
    private static final Duration EXPIRE = Duration.ofMinutes(10);

    // ============================== Bean ==============================

    /**
     * 進められる時計（ナノ秒）
     */
    private final AtomicLong nanos = new AtomicLong();

    /**
     * テストの時計
     */
    private final Ticker ticker = nanos::get;

    // ============================== テスト ==============================

    @Nested
    @DisplayName("期限")
    class Expiration {

        @Test
        @DisplayName("入れてから期限の 1 ナノ秒前は、会員 ID を返す")
        void returnsJustBeforeExpiration() {
            // 準備：入れる
            var cache = newCache(10, EXPIRE);
            var id = UUID.randomUUID();
            cache.put("a", id);

            // 操作：期限の 1 ナノ秒前まで進める
            nanos.set(EXPIRE.toNanos() - 1);

            // 確認：返す
            assertThat(cache.find("a")).contains(id);
        }

        @Test
        @DisplayName("入れてから期限ちょうどで、空になる")
        void emptyAtExpiration() {
            // 準備：入れる
            var cache = newCache(10, EXPIRE);
            cache.put("a", UUID.randomUUID());

            // 操作：期限ちょうどまで進める
            nanos.set(EXPIRE.toNanos());

            // 確認：空
            assertThat(cache.find("a")).isEmpty();
        }
    }

    @Nested
    @DisplayName("件数の上限")
    class MaximumSize {

        @Test
        @DisplayName("上限が 1 で 2 件入れると、1 件だけ残る")
        void keepsOnlyMaximumSize() {
            // 準備：上限 1 のキャッシュ
            var cache = newCache(1, EXPIRE);

            // 操作：2 件入れる
            cache.put("a", UUID.randomUUID());
            cache.put("b", UUID.randomUUID());

            // 確認：1 件だけ残る
            var remaining = (cache.find("a").isPresent() ? 1 : 0) + (cache.find("b").isPresent() ? 1 : 0);
            assertThat(remaining).isEqualTo(1);
        }
    }

    // ============================== 準備の補助 ==============================

    /**
     * テストの時計と、同期の executor でキャッシュを作る。
     */
    private MemberIdCache newCache(long maximumSize, Duration expireAfterWrite) {
        return new MemberIdCache(new MemberIdCacheProperties(maximumSize, expireAfterWrite), ticker, Runnable::run);
    }
}
