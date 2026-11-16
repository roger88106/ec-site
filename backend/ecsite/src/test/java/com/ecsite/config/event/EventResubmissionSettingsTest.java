package com.ecsite.config.event;

import com.ecsite.support.TestSettings;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
    @DisplayName("処理の回数の上限が 1 より小さい設定は、拒否される")
    void rejectsNonPositiveMaxAttempts() {
        // 操作と確認：上限 0 を渡すと、値の検証で拒否される
        assertThatThrownBy(() -> TestSettings.bindValidated(PREFIX, EventResubmissionJob.Properties.class, Map.of("max-attempts", "0")))
                .isInstanceOf(BindException.class);
    }
}
