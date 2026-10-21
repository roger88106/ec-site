package com.ecsite.observability;

import com.ecsite.IntegrationTest;
import com.ecsite.common.error.CommonErrorCode;
import com.ecsite.common.error.exception.BusinessException;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.springframework.boot.micrometer.tracing.test.autoconfigure.AutoConfigureTracing;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.atomic.AtomicReference;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 可観測性の検証（T019、バックエンドのアーキテクチャ 第 5 節）。<br />
 * テストでは通常トレーシングとメトリクスが無効なので、このテストでは有効にする（送信はしない）。
 */
@IntegrationTest
@AutoConfigureMockMvc
@AutoConfigureTracing
@AutoConfigureMetrics
@TestPropertySource(properties = {
        // 全リクエストを記録する。Zipkin には送らない
        "management.tracing.sampling.probability=1.0",
        "management.tracing.export.zipkin.enabled=false"
})
@Import(ObservabilityTest.TracedController.class)
class ObservabilityTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    @DisplayName("liveness が 200 を返す")
    void liveness() throws Exception {
        mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @DisplayName("readiness が 200 を返す")
    void readiness() throws Exception {
        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @DisplayName("Prometheus の形式でメトリクスを返す")
    void prometheus() throws Exception {
        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("jvm_memory_used_bytes")));
    }

    @Test
    @DisplayName("span がある場合、エラーレスポンスの traceId はそのトレース ID になる")
    void errorResponseCarriesCurrentTraceId() throws Exception {
        mockMvc.perform(get("/api/test-traced/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.traceId").value(TracedController.observedTraceId.get()));
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
