package com.ecsite.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 再発行の設定の単体テスト（T017-02-02、AC-290）。<br />
 * Spring を起動せずに、設定の型へ値を当てはめて確認する。
 */
class EventResubmissionSettingsTest {

    /**
     * 再発行の設定の接頭辞
     */
    private static final String PREFIX = "ecsite.scheduling.event-resubmission";

    @Test
    @DisplayName("AC-290 再発行の間隔は既定で 1 分、発行からの時間の下限は 5 分、処理の回数の上限は 60")
    void defaults() {
        var properties = new Binder(new MapConfigurationPropertySource())
                .bindOrCreate(PREFIX, EventResubmissionJob.Properties.class);

        assertThat(properties.interval()).isEqualTo(Duration.ofMinutes(1));
        assertThat(properties.minAge()).isEqualTo(Duration.ofMinutes(5));
        assertThat(properties.maxAttempts()).isEqualTo(60);
    }

    @Test
    @DisplayName("AC-290 処理の回数の上限は、設定で変えられる")
    void overridesMaxAttempts() {
        var source = new MapConfigurationPropertySource();
        source.put(PREFIX + ".max-attempts", "10");

        var properties = new Binder(source).bindOrCreate(PREFIX, EventResubmissionJob.Properties.class);

        assertThat(properties.maxAttempts()).isEqualTo(10);
    }
}
