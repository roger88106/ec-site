package com.ecsite.observability;

import com.ecsite.AsyncEvents;
import com.ecsite.IntegrationTest;
import com.ecsite.common.error.CommonErrorCode;
import com.ecsite.common.error.exception.BusinessException;
import com.jayway.jsonpath.JsonPath;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.springframework.boot.micrometer.tracing.test.autoconfigure.AutoConfigureTracing;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 可観測性の検証
 *
 * <p>バックエンドのアーキテクチャの「水平スケール」に対応する。
 * テストでは通常トレーシングとメトリクスが無効なので、このテストでは有効にする（送信はしない）。
 *
 * <p>業務ポートと運用ポートを実際に開いて起動し、死活監視とメトリクスは運用ポートに HTTP で問い合わせる。
 * 統合テストの設定は運用の口を業務ポートと同じに置く（MockMvc で確認するため）ので、ここでは運用ポートを別に開く。
 * 応答の内容と、運用ポートでだけ出ることを、入れ子のクラスに分けて確認する。設定を 1 つにして、Spring のコンテキストを共有する。
 */
@IntegrationTest
@AutoConfigureTracing
@AutoConfigureMetrics
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "management.server.port=0")
@TestPropertySource(properties = {
        // 全リクエストを記録する。Zipkin には送らない
        "management.tracing.sampling.probability=1.0",
        "management.tracing.export.zipkin.enabled=false"
})
@Import(ObservabilityTest.TracedController.class)
class ObservabilityTest {

    @Autowired
    MockMvc mockMvc;

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

    @Nested
    @DisplayName("応答の内容")
    class Responses {

        @Test
        @DisplayName("liveness が 200 を返す")
        void liveness() throws Exception {
            var response = send(managementPort, "/actuator/health/liveness");

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(JsonPath.<String>read(response.body(), "$.status")).isEqualTo("UP");
        }

        @Test
        @DisplayName("readiness が 200 を返す")
        void readiness() throws Exception {
            var response = send(managementPort, "/actuator/health/readiness");

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(JsonPath.<String>read(response.body(), "$.status")).isEqualTo("UP");
        }

        @Test
        @DisplayName("Prometheus の形式でメトリクスを返す")
        void prometheus() throws Exception {
            var response = send(managementPort, "/actuator/prometheus");

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).contains("jvm_memory_used_bytes");
        }

        @Test
        @DisplayName("span がある場合、エラーレスポンスの traceId はそのトレース ID になる")
        void errorResponseCarriesCurrentTraceId() throws Exception {
            mockMvc.perform(get("/api/test-traced/not-found"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.traceId").value(TracedController.observedTraceId.get()));
        }
    }

    @Nested
    @DisplayName("運用ポート")
    class ManagementPort {

        @Test
        @DisplayName("AC-236 業務ポートでは、死活監視とメトリクスが出ない")
        void businessPortHidesManagementEndpoints() throws Exception {
            assertThat(managementPort).isNotEqualTo(serverPort);
            assertThat(send(serverPort, "/actuator/health").statusCode()).isEqualTo(404);
            assertThat(send(serverPort, "/actuator/prometheus").statusCode()).isEqualTo(404);
        }

        @Test
        @DisplayName("AC-236 運用ポートでは、死活監視とメトリクスが出る")
        void managementPortServesManagementEndpoints() throws Exception {
            assertThat(send(managementPort, "/actuator/health").statusCode()).as("health").isEqualTo(200);
            assertThat(send(managementPort, "/actuator/prometheus").statusCode()).as("prometheus").isEqualTo(200);
        }
    }

    /**
     * GET を送り、応答を返す。
     *
     * @param port ポート
     * @param path パス
     * @return 応答
     */
    private HttpResponse<String> send(int port, String path) throws IOException, InterruptedException {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).timeout(AsyncEvents.TIMEOUT).GET().build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    // ============================== テスト用の Controller ==============================

    /**
     * 処理中のトレース ID を記録してから、例外を投げる Controller
     */
    @RestController
    static class TracedController {

        /**
         * 処理中に見えたトレース ID
         */
        static final AtomicReference<String> observedTraceId = new AtomicReference<>();

        private final Tracer tracer;

        TracedController(Tracer tracer) {
            this.tracer = tracer;
        }

        @GetMapping("/api/test-traced/not-found")
        void notFound() {
            observedTraceId.set(tracer.currentSpan().context().traceId());
            throw BusinessException.of(CommonErrorCode.NOT_FOUND);
        }
    }
}
