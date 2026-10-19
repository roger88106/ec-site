package com.ecsite.observability;

import com.ecsite.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 運用ポートの検査。<br />
 * 業務ポートと運用ポートを実際に開いて起動し、死活監視とメトリクスが運用ポートだけで出ることを確かめる。
 * 統合テストの設定は運用の口を業務ポートと同じに置く（MockMvc で確かめるため）ので、ここでは運用ポートを別に開く。
 * テストではメトリクスの書き出しが止まっているので、{@link AutoConfigureMetrics} で有効にする。
 */
@IntegrationTest
@AutoConfigureMetrics
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "management.server.port=0")
class ManagementPortTest {

    /**
     * 業務ポート
     */
    @Value("${local.server.port}")
    int serverPort;

    /**
     * 運用ポート
     */
    @Value("${local.management.port}")
    int managementPort;

    /**
     * HTTP の送り手
     */
    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    @DisplayName("AC-236 業務ポートでは、死活監視とメトリクスが出ない")
    void businessPortHidesManagementEndpoints() throws Exception {
        assertThat(managementPort).isNotEqualTo(serverPort);
        assertThat(status(serverPort, "/actuator/health")).isNotEqualTo(200);
        assertThat(status(serverPort, "/actuator/prometheus")).isNotEqualTo(200);
    }

    @Test
    @DisplayName("AC-236 運用ポートでは、死活監視とメトリクスが出る")
    void managementPortServesManagementEndpoints() throws Exception {
        assertThat(status(managementPort, "/actuator/health")).as("health").isEqualTo(200);
        assertThat(status(managementPort, "/actuator/prometheus")).as("prometheus").isEqualTo(200);
    }

    /**
     * GET のステータスコード。
     *
     * @param port ポート
     * @param path パス
     * @return ステータスコード
     */
    private int status(int port, String path) throws IOException, InterruptedException {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build();
        return http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
    }
}
