package com.ecsite.config.event;

import com.ecsite.TestSettings;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 処理の済んだイベントを消す片付けのジョブの設定と分散ロックの単体テスト（AC-285、287）。<br />
 * 設定は Spring を起動せずに、設定の型へ値を当てはめて確認する。他のモジュールの片付けのジョブは {@code CleanupJobSettingsTest} が確認する。
 */
class EventCleanupJobSettingsTest {

    private static final String PREFIX = "ecsite.scheduling.event-cleanup";

    @Test
    @DisplayName("AC-285 処理の済んだイベントの片付けの間隔は既定で 1 時間、保存期間は 7 日")
    void defaults() {
        var events = bind(Map.of());

        assertThat(events.interval()).isEqualTo(Duration.ofHours(1));
        assertThat(events.retention()).isEqualTo(Duration.ofDays(7));
    }

    @Test
    @DisplayName("AC-285 間隔と保存期間は、設定で変えられる")
    void overridesBySettings() {
        assertThat(bind(Map.of("interval", "10m", "retention", "3d")))
                .extracting(EventCleanupJob.Properties::interval, EventCleanupJob.Properties::retention)
                .containsExactly(Duration.ofMinutes(10), Duration.ofDays(3));
    }

    @Test
    @DisplayName("AC-287 処理の済んだイベントの片付けは、定期に動き、分散ロックで同時に 1 つのインスタンスだけが動く")
    void runsWithDistributedLock() throws Exception {
        var method = EventCleanupJob.class.getDeclaredMethod("cleanUp");

        assertThat(method.getAnnotation(Scheduled.class)).isNotNull();
        assertThat(method.getAnnotation(SchedulerLock.class)).isNotNull()
                .extracting(SchedulerLock::name).isEqualTo("event-cleanup");
    }

    /**
     * 接頭辞の下の値を、設定の型に当てはめる（値がなければ既定値）。
     */
    private static EventCleanupJob.Properties bind(Map<String, String> values) {
        return TestSettings.bind(PREFIX, EventCleanupJob.Properties.class, values);
    }
}
