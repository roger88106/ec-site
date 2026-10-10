package com.ecsite;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

/**
 * テスト用の、時刻を決められる時計。<br />
 * 時刻で切り替わる振る舞い（期間付きの税率、予約公開など）を、テストから時刻を動かして確かめるために使う。
 * 統合テストでは {@code TestClockConfig} でアプリの {@link Clock} と差し替える。
 */
public final class MutableClock extends Clock {

    /**
     * 現在の時刻。テストとアプリのスレッドの両方から読み書きするので、原子的に更新する
     */
    private final AtomicReference<Instant> instant;

    /**
     * 指定した時刻の時計を作る。
     *
     * @param instant 時刻
     */
    public MutableClock(Instant instant) {
        this.instant = new AtomicReference<>(instant);
    }

    /**
     * 時刻を設定する。
     *
     * @param instant 時刻
     */
    public void setInstant(Instant instant) {
        this.instant.set(instant);
    }

    /**
     * 時刻を進める。
     *
     * @param duration 進める時間
     */
    public void advance(Duration duration) {
        instant.updateAndGet(current -> current.plus(duration));
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        throw new UnsupportedOperationException("UTC 以外のタイムゾーンは使わない");
    }

    @Override
    public Instant instant() {
        return instant.get();
    }
}
