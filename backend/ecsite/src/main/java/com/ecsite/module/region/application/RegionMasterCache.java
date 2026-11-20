package com.ecsite.module.region.application;

import com.ecsite.module.region.application.model.RegionMaster;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 地域のマスターデータを、メモリに持つキャッシュ
 *
 * <p>全件を 1 つの値として持つ。最初に使うときに読み、有効期間を過ぎた後の最初の使用で再読み込みする。有効期間は注入した時計で測る。
 * 再読み込みの間も、他の照会を待たせない（複数の照会が同時に再読み込みすることがある）。
 * 読み込みは {@link RegionMasterQueryService} に任せ、このクラスはトランザクションを持たない。
 */
@Component
public class RegionMasterCache {

    /**
     * 読み込みの担当
     */
    private final RegionMasterQueryService loader;

    /**
     * 有効期間
     */
    private final Duration expireAfterWrite;

    /**
     * 時計
     */
    private final Clock clock;

    /**
     * キャッシュの中身
     *
     * <p>まだ読み込んでいなければ null。
     */
    private final AtomicReference<@Nullable Entry> entry = new AtomicReference<>();

    /**
     * 設定と時計で作る。
     *
     * @param loader     読み込みの担当
     * @param properties キャッシュの設定
     * @param clock      時計
     */
    public RegionMasterCache(RegionMasterQueryService loader, RegionCacheProperties properties, Clock clock) {
        this.loader = loader;
        this.expireAfterWrite = properties.expireAfterWrite();
        this.clock = clock;
    }

    /**
     * 地域のマスターデータを返す。
     *
     * <p>まだ読み込んでいなければ、または有効期間を過ぎていれば、再読み込みする。
     *
     * @return 地域のマスターデータ
     */
    public RegionMaster get() {
        var now = clock.instant();
        var current = entry.get();
        // 複数のスレッドが同時に再読み込みすることがあるが、結果は同じなので排他しない
        if (current == null || !now.isBefore(current.fetchedAt().plus(expireAfterWrite))) {
            current = new Entry(loader.load(), now);
            entry.set(current);
        }
        return current.master();
    }

    /**
     * キャッシュの中身
     *
     * @param master    地域のマスターデータ
     * @param fetchedAt 読み込んだ時刻
     */
    private record Entry(RegionMaster master, Instant fetchedAt) {
    }
}
