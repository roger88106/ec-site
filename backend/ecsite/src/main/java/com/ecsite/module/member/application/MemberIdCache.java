package com.ecsite.module.member.application;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;

/**
 * IdP のアカウントの ID から会員 ID への対応を、メモリに持つキャッシュ
 *
 * <p>対応は作った後に変わらないので、インスタンスごとに中身が違ってよい。件数の上限と期限を付けて持つ。
 */
@Component
public class MemberIdCache {

    /**
     * IdP のアカウントの ID → 会員 ID
     */
    private final Cache<String, UUID> cache;

    /**
     * 設定から作る。
     *
     * @param properties キャッシュの設定
     */
    @Autowired
    public MemberIdCache(MemberIdCacheProperties properties) {
        this(properties, Ticker.systemTicker(), ForkJoinPool.commonPool());
    }

    /**
     * 時計と executor を指定して作る（テストで期限を進め、追い出しを同期にするため）。
     *
     * @param properties キャッシュの設定
     * @param ticker     時計
     * @param executor   追い出しなどの保守を実行する executor
     */
    public MemberIdCache(MemberIdCacheProperties properties, Ticker ticker, Executor executor) {
        this.cache = Caffeine.newBuilder()
                .maximumSize(properties.maximumSize())
                .expireAfterWrite(properties.expireAfterWrite())
                .ticker(ticker)
                .executor(executor)
                .build();
    }

    /**
     * 会員 ID を探す。
     *
     * @param idpSubject IdP のアカウントの ID
     * @return 会員 ID。キャッシュになければ空
     */
    public Optional<UUID> find(String idpSubject) {
        return Optional.ofNullable(cache.getIfPresent(idpSubject));
    }

    /**
     * 会員 ID を入れる。
     *
     * @param idpSubject IdP のアカウントの ID
     * @param memberId   会員 ID
     */
    public void put(String idpSubject, UUID memberId) {
        cache.put(idpSubject, memberId);
    }
}
