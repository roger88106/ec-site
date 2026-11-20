package com.ecsite.module.region.application;

import com.ecsite.module.region.application.model.RegionMaster;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 地域のマスターデータを、メモリに持つキャッシュ
 *
 * <p>全件を 1 つの値として持つ。最初に使うときに読み、期限が切れた後の最初の使用で読み直す。
 * 読み込みは {@link RegionMasterQueryService} に任せ、このクラスはトランザクションを持たない。
 */
@Component
public class RegionMasterCache {

    /**
     * 値を 1 つだけ持つためのキー
     */
    private static final String KEY = "region-master";

    /**
     * 読み込みの担当
     */
    private final RegionMasterQueryService loader;

    /**
     * キー → 地域のマスターデータ
     */
    private final Cache<String, RegionMaster> cache;

    /**
     * 設定と実時間で作る。
     *
     * @param loader     読み込みの担当
     * @param properties キャッシュの設定
     */
    @Autowired
    public RegionMasterCache(RegionMasterQueryService loader, RegionCacheProperties properties) {
        this(loader, properties, Ticker.systemTicker());
    }

    /**
     * 時計を指定して作る（テストで期限を進めるため）。
     *
     * @param loader     読み込みの担当
     * @param properties キャッシュの設定
     * @param ticker     時計
     */
    public RegionMasterCache(RegionMasterQueryService loader, RegionCacheProperties properties, Ticker ticker) {
        this.loader = loader;
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(properties.expireAfterWrite())
                .ticker(ticker)
                .build();
    }

    /**
     * 地域のマスターデータを返す。なければ、または期限が切れていれば読む。
     *
     * @return 地域のマスターデータ
     */
    public RegionMaster get() {
        return cache.get(KEY, key -> loader.load());
    }
}
