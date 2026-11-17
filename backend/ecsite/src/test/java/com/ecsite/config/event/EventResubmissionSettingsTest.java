package com.ecsite.config.event;

import com.ecsite.support.TestSettings;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.context.properties.bind.BindException;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 再発行の設定の単体テスト
 *
 * <p>Spring を起動せずに確認する。既定は、設定ファイル（{@code application.yml}）の値を含めて、起動したアプリが使う値を確認する。
 */
class EventResubmissionSettingsTest {

    // ============================== 定数 ==============================

    /**
     * 再発行の設定の接頭辞
     */
    private static final String PREFIX = "ecsite.scheduling.event-resubmission";

    // ============================== テスト ==============================

    @Test
    @DisplayName("AC-290 再発行の既定は、間隔 1 分、発行からの時間の下限 5 分、処理の回数の上限 60")
    void defaults() {
        // 操作：設定ファイルを読んで当てはめる
        var properties = TestSettings.loadAndBind(PREFIX, EventResubmissionJob.Properties.class);

        // 確認：既定の値
        assertThat(properties.interval()).isEqualTo(Duration.ofMinutes(1));
        assertThat(properties.minAge()).isEqualTo(Duration.ofMinutes(5));
        assertThat(properties.maxAttempts()).isEqualTo(60);
    }

    @Test
    @DisplayName("AC-290 処理の回数の上限は、設定で変えられる")
    void overridesMaxAttempts() {
        // 操作：処理の回数の上限 10 を渡して当てはめる
        var properties = TestSettings.bind(PREFIX, EventResubmissionJob.Properties.class, Map.of("max-attempts", "10"));

        // 確認：渡した値になる
        assertThat(properties.maxAttempts()).isEqualTo(10);
    }

    @Test
    @DisplayName("処理の回数の上限が下限の 1 なら、値の検証を通る")
    void acceptsMaxAttemptsAtMinimum() {
        // 操作：上限 1 を検証付きで当てはめる
        var properties = TestSettings.bindValidated(PREFIX, EventResubmissionJob.Properties.class, Map.of("max-attempts", "1"));

        // 確認：1 回
        assertThat(properties.maxAttempts()).isEqualTo(1);
    }

    @ParameterizedTest(name = "「{0}」が {1}")
    @CsvSource({"interval, 0s", "interval, -1s", "interval, 999us", "min-age, 0s", "min-age, -1s", "min-age, 999us"})
    @DisplayName("下限が 1ms の時間の設定が、0、負の値、1ms 未満（999us）なら、値の検証で拒否される")
    void rejectsDurationBelowMinimum(String name, String value) {
        // 操作と確認：許す範囲の外の時間を渡すと、値の検証で拒否される
        assertThatThrownBy(() -> TestSettings.bindValidated(PREFIX, EventResubmissionJob.Properties.class, Map.of(name, value)))
                .isInstanceOf(BindException.class);
    }

    @ParameterizedTest(name = "「{0}」")
    @CsvSource({"interval", "min-age"})
    @DisplayName("下限が 1ms の時間の設定が、下限の 1ms なら、値の検証を通り、その値になる")
    void acceptsDurationAtMinimum(String name) {
        // 操作：1ms を検証付きで当てはめる
        var properties = TestSettings.bindValidated(PREFIX, EventResubmissionJob.Properties.class, Map.of(name, "1ms"));

        // 確認：1ms になる
        var actual = name.equals("interval") ? properties.interval() : properties.minAge();
        assertThat(actual).isEqualTo(Duration.ofMillis(1));
    }

    @Test
    @DisplayName("処理の回数の上限が 1 より小さい設定は、拒否される")
    void rejectsNonPositiveMaxAttempts() {
        // 操作と確認：上限 0 を渡すと、値の検証で拒否される
        assertThatThrownBy(() -> TestSettings.bindValidated(PREFIX, EventResubmissionJob.Properties.class, Map.of("max-attempts", "0")))
                .isInstanceOf(BindException.class);
    }
}
