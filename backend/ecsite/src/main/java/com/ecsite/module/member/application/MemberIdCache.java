package com.ecsite.module.member.application;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

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
    public MemberIdCache(MemberIdCacheProperties properties) {
        this.cache = Caffeine.newBuilder()
                .maximumSize(properties.maximumSize())
                .expireAfterWrite(properties.expireAfterWrite())
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
