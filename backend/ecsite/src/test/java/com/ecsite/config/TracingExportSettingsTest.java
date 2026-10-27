package com.ecsite.config;

import com.ecsite.TestSettings;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * トレースの送信の既定の検査。<br />
 * 共通の設定は送信しない（送信先を持たない）ので、送信先のない環境が、届かない送信先へ送り続けない。local の profile は、compose の Zipkin に送る。
 */
class TracingExportSettingsTest {

    /**
     * Zipkin へ送信するかを決める設定
     */
    private static final String ZIPKIN_ENABLED = "management.tracing.export.zipkin.enabled";

    /**
     * Zipkin の送信先の設定
     */
    private static final String ZIPKIN_ENDPOINT = "management.tracing.export.zipkin.endpoint";

    @Test
    @DisplayName("共通の設定では、トレースの送信が無効で、送信先を持たない")
    void commonSettingsDoNotExport() {
        Map<String, String> settings = TestSettings.load();

        assertThat(settings).containsEntry(ZIPKIN_ENABLED, "false");
        assertThat(settings).doesNotContainKey(ZIPKIN_ENDPOINT);
    }

    @Test
    @DisplayName("local の profile では、トレースの送信が有効になる")
    void localProfileExports() {
        Map<String, String> settings = TestSettings.load("local");

        assertThat(settings).containsEntry(ZIPKIN_ENABLED, "true");
        assertThat(settings).containsKey(ZIPKIN_ENDPOINT);
    }
}
