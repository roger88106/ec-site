package com.ecsite.config.event;

import com.ecsite.support.TestSettings;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.bind.BindException;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 処理の済んだイベントを消す片付けのジョブの設定と分散ロックの単体テスト
 *
 * <p>設定は Spring を起動せずに確認する。既定は、設定ファイル（{@code application.yml}）の値を含めて、起動したアプリが使う値を確認する。
 * 他のモジュールの片付けのジョブは {@code CleanupJobSettingsTest} が確認する。
 */
class EventCleanupJobSettingsTest {

    // ============================== 定数 ==============================

    /**
     * 処理の済んだイベントの片付けの設定の接頭辞
     */
    private static final String PREFIX = "ecsite.scheduling.event-cleanup";

    // ============================== テスト ==============================

    @Test
    @DisplayName("AC-285 処理の済んだイベントの片付けの既定は、間隔 1 時間、保存期間 7 日")
    void defaults() {
        // 操作：設定ファイルを読んで当てはめる
        var properties = TestSettings.loadAndBind(PREFIX, EventCleanupJob.Properties.class);

        // 確認：既定の値
        assertThat(properties.interval()).isEqualTo(Duration.ofHours(1));
        assertThat(properties.retention()).isEqualTo(Duration.ofDays(7));
    }

    @Test
    @DisplayName("AC-285 間隔と保存期間は、設定で変えられる")
    void overridesBySettings() {
        // 操作：間隔 10 分、保存期間 3 日を渡して当てはめる
        var properties = TestSettings.bind(PREFIX, EventCleanupJob.Properties.class, Map.of("interval", "10m", "retention", "3d"));

        // 確認：渡した値になる
        assertThat(properties.interval()).isEqualTo(Duration.ofMinutes(10));
        assertThat(properties.retention()).isEqualTo(Duration.ofDays(3));
    }

    @ParameterizedTest(name = "「{0}」")
    @ValueSource(strings = {"0s", "-1s"})
    @DisplayName("片付けの間隔が 0 以下なら、値の検証で拒否される")
    void rejectsNonPositiveInterval(String value) {
        // 操作と確認：値の検証で拒否される
        assertThatThrownBy(() -> TestSettings.bindValidated(PREFIX, EventCleanupJob.Properties.class, Map.of("interval", value)))
                .isInstanceOf(BindException.class);
    }

    @Test
    @DisplayName("片付けの間隔が正の最小値（1ns）なら、値の検証を通り、その値になる")
    void acceptsSmallestPositiveInterval() {
        // 操作：1ns を検証付きで当てはめる
        var properties = TestSettings.bindValidated(PREFIX, EventCleanupJob.Properties.class, Map.of("interval", "1ns"));

        // 確認：1ns になる
        assertThat(properties.interval()).isEqualTo(Duration.ofNanos(1));
    }

    @Test
    @DisplayName("保存期間が負なら、値の検証で拒否される")
    void rejectsNegativeRetention() {
        // 操作と確認：-1s は値の検証で拒否される
        assertThatThrownBy(() -> TestSettings.bindValidated(PREFIX, EventCleanupJob.Properties.class, Map.of("retention", "-1s")))
                .isInstanceOf(BindException.class);
    }

    @Test
    @DisplayName("保存期間が 0 なら、値の検証を通り、0 になる")
    void acceptsZeroRetention() {
        // 操作：0s を検証付きで当てはめる
        var properties = TestSettings.bindValidated(PREFIX, EventCleanupJob.Properties.class, Map.of("retention", "0s"));

        // 確認：0 になる
        assertThat(properties.retention()).isEqualTo(Duration.ZERO);
    }

    @Test
    @DisplayName("AC-287 処理の済んだイベントの片付けは、定期に動き、分散ロックで同時に 1 つのインスタンスだけが動く")
    void runsWithDistributedLock() throws Exception {
        // 準備：ジョブの片付けのメソッド
        var method = EventCleanupJob.class.getDeclaredMethod("cleanUp");

        // 確認：定期に動き、ジョブの名前の分散ロックを取る
        assertThat(method.getAnnotation(Scheduled.class)).isNotNull();
        assertThat(method.getAnnotation(SchedulerLock.class)).isNotNull()
                .extracting(SchedulerLock::name).isEqualTo("event-cleanup");
    }
}
