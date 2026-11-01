package com.ecsite.config.event;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 未完了のイベントと止めたイベントの件数のメトリクス（バックエンドのアーキテクチャの「水平スケール」）。<br />
 * 再発行のジョブが数え直して値を置く。メトリクスの収集のたびに DB を読まないため。
 */
@Component
class EventPublicationMetrics {

    /**
     * 未完了のイベントの件数（止めたイベントを含む）
     */
    private final AtomicLong incomplete = new AtomicLong();

    /**
     * 止めたイベントの件数
     */
    private final AtomicLong stopped = new AtomicLong();

    /**
     * 件数のメトリクスを登録する。
     *
     * @param meterRegistry メトリクスの登録先
     */
    EventPublicationMetrics(MeterRegistry meterRegistry) {
        Gauge.builder("ecsite.events.incomplete", incomplete, AtomicLong::get)
                .description("未完了のイベントの件数（止めたイベントを含む）")
                .register(meterRegistry);
        Gauge.builder("ecsite.events.stopped", stopped, AtomicLong::get)
                .description("処理の回数が上限に達して、再発行を止めたイベントの件数")
                .register(meterRegistry);
    }

    /**
     * 件数を置き換える。
     *
     * @param incompleteCount 未完了のイベントの件数
     * @param stoppedCount    止めたイベントの件数
     */
    void record(long incompleteCount, long stoppedCount) {
        incomplete.set(incompleteCount);
        stopped.set(stoppedCount);
    }
}
