package com.ecsite.module.catalog.infrastructure.directory;

import com.ecsite.module.tax.api.TaxRateApi;
import com.ecsite.module.tax.api.model.TaxRateSchedule;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 税率の一覧のキャッシュ
 *
 * <p>税区分ごとの税率の一覧（予定を含む）を、インスタンスの中に {@link #TTL} 保持する。予定を含む一覧を持つので、税率の切り替えは表示時刻で判定でき、
 * キャッシュの影響は税率の登録・変更の反映が最大 {@link #TTL} 遅れることだけ。有効期間は注入した時計で測る。
 */
@Component
@RequiredArgsConstructor
class TaxRateScheduleCache {

    /**
     * 有効期間
     */
    static final Duration TTL = Duration.ofSeconds(30);

    /**
     * 税モジュールの API
     */
    private final TaxRateApi taxRateApi;

    /**
     * 時計
     */
    private final Clock clock;

    /**
     * キャッシュの中身
     *
     * <p>まだ取得していなければ null。
     */
    private final AtomicReference<Entry> entry = new AtomicReference<>();

    /**
     * 税率の一覧を返す。
     *
     * <p>有効期間を過ぎていれば、税モジュールから取得し直す。
     *
     * @return 税区分ごとの税率の一覧
     */
    List<TaxRateSchedule> schedules() {
        var now = clock.instant();
        var current = entry.get();
        // 複数のスレッドが同時に取得し直すことがあるが、結果は同じなので排他しない
        if (current == null || !now.isBefore(current.fetchedAt().plus(TTL))) {
            current = new Entry(taxRateApi.listRateSchedules(), now);
            entry.set(current);
        }
        return current.schedules();
    }

    /**
     * キャッシュを捨てる。
     *
     * <p>次の取得で税モジュールから取得し直す。
     */
    void evict() {
        entry.set(null);
    }

    /**
     * キャッシュの中身
     *
     * @param schedules 税区分ごとの税率の一覧
     * @param fetchedAt 取得した時刻
     */
    private record Entry(List<TaxRateSchedule> schedules, Instant fetchedAt) {
    }
}
