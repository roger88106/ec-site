package com.ecsite.observability;

import com.ecsite.common.error.CommonErrorCode;
import com.ecsite.common.error.exception.BusinessException;
import com.ecsite.support.async.AsyncEvents;
import com.ecsite.support.config.IntegrationTest;
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

    // ============================== Bean ==============================

    /**
     * 業務ポートの API を呼ぶ MockMvc
     */
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

    // ============================== テスト ==============================

    @Nested
    @DisplayName("応答の内容")
    class Responses {

        @Test
        @DisplayName("liveness が 200 を返す")
        void liveness() throws Exception {
            // 操作：運用ポートで liveness を問い合わせる
            var response = send(managementPort, "/actuator/health/liveness");

            // 確認：200 で、状態が UP
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(JsonPath.<String>read(response.body(), "$.status")).isEqualTo("UP");
        }

        @Test
        @DisplayName("readiness が 200 を返す")
        void readiness() throws Exception {
            // 操作：運用ポートで readiness を問い合わせる
            var response = send(managementPort, "/actuator/health/readiness");

            // 確認：200 で、状態が UP
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(JsonPath.<String>read(response.body(), "$.status")).isEqualTo("UP");
        }

        @Test
        @DisplayName("Prometheus の形式でメトリクスを返す")
        void prometheus() throws Exception {
            // 操作：運用ポートでメトリクスを問い合わせる
            var response = send(managementPort, "/actuator/prometheus");

            // 確認：200 で、Prometheus の名前の JVM のメトリクスがある
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).contains("jvm_memory_used_bytes");
        }

        @Test
        @DisplayName("AC-292 運用ポートのメトリクスに、未完了のイベントの件数と止めたイベントの件数がある")
        void prometheusHasEventCounts() throws Exception {
            // 操作：運用ポートでメトリクスを問い合わせる
            var response = send(managementPort, "/actuator/prometheus");

            // 確認：200 で、未完了と止めたイベントの件数のメトリクス（gauge）がある
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).contains("# TYPE ecsite_events_incomplete gauge", "# TYPE ecsite_events_stopped gauge");
        }

        @Test
        @DisplayName("span がある場合、エラーレスポンスの traceId はそのトレース ID になる")
        void errorResponseCarriesCurrentTraceId() throws Exception {
            // 操作と確認：処理中のトレース ID を記録してから見つからないエラーにする API を呼ぶと、本文の traceId がその ID
            mockMvc.perform(get("/api/test-traced/not-found"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.traceId").value(TracedController.observedTraceId.get()));
        }
    }

    @Nested
    @DisplayName("運用ポート")
    class ManagementPort {

        @Test
        @DisplayName("AC-236 業務ポートでは、死活監視が出ない")
        void businessPortHidesHealth() throws Exception {
            // 確認（前提）：業務ポートと運用ポートが別
            assertThat(managementPort).isNotEqualTo(serverPort);

            // 操作と確認：業務ポートの死活監視は 404
            assertThat(send(serverPort, "/actuator/health").statusCode()).isEqualTo(404);
        }

        @Test
        @DisplayName("AC-236 業務ポートでは、メトリクスが出ない")
        void businessPortHidesMetrics() throws Exception {
            // 確認（前提）：業務ポートと運用ポートが別
            assertThat(managementPort).isNotEqualTo(serverPort);

            // 操作と確認：業務ポートのメトリクスは 404
            assertThat(send(serverPort, "/actuator/prometheus").statusCode()).isEqualTo(404);
        }

        @Test
        @DisplayName("AC-236 運用ポートでは、死活監視が出る")
        void managementPortServesHealth() throws Exception {
            // 操作と確認：運用ポートの死活監視は 200
            assertThat(send(managementPort, "/actuator/health").statusCode()).isEqualTo(200);
        }

        @Test
        @DisplayName("AC-236 運用ポートでは、メトリクスが出る")
        void managementPortServesMetrics() throws Exception {
            // 操作と確認：運用ポートのメトリクスは 200
            assertThat(send(managementPort, "/actuator/prometheus").statusCode()).isEqualTo(200);
        }
    }

    // ============================== 準備の補助 ==============================

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
