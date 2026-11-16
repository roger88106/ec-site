package com.ecsite.common.error.handler;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * トレース ID の解決の単体テスト
 *
 * <p>トレースの span があればその ID を返し、なければ新しい ID を作ることを確認する。
 * エラーの応答に入ることは {@code ErrorResponseTest} が確認する。
 */
class TraceIdResolverTest {

    // ============================== 定数 ==============================

    /**
     * span のトレース ID
     */
    private static final String SPAN_TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";

    /**
     * 新しく作るトレース ID の形（W3C のトレース ID と同じ 32 桁の 16 進数）
     */
    private static final String NEW_TRACE_ID_FORM = "[0-9a-f]{32}";

    // ============================== テスト ==============================

    @Test
    @DisplayName("トレースの span があれば、その span のトレース ID を返す")
    void returnsSpanTraceId() {
        // 準備：トレース ID を持つ span のあるトレーサー
        var resolver = new TraceIdResolver(provider(tracerWithTraceId(SPAN_TRACE_ID)));

        // 操作と確認：span のトレース ID
        assertThat(resolver.resolve()).isEqualTo(SPAN_TRACE_ID);
    }

    @Nested
    @DisplayName("新しいトレース ID を作るとき")
    class NewTraceId {

        @Test
        @DisplayName("トレーサーがなければ（トレーシングが無効）、32 桁の 16 進数を新しく作り、呼ぶたびに違う")
        void createsWithoutTracer() {
            // 準備：トレーサーのない resolver
            var resolver = new TraceIdResolver(provider(null));

            // 操作と確認：新しい ID
            assertNewTraceIds(resolver);
        }

        @Test
        @DisplayName("今の span がなければ、32 桁の 16 進数を新しく作り、呼ぶたびに違う")
        void createsWithoutCurrentSpan() {
            // 準備：今の span を返さないトレーサー
            var tracer = mock(Tracer.class);
            var resolver = new TraceIdResolver(provider(tracer));

            // 操作と確認：新しい ID
            assertNewTraceIds(resolver);
        }

        @ParameterizedTest(name = "「{0}」")
        @ValueSource(strings = {"", "   "})
        @DisplayName("span のトレース ID が空白なら、32 桁の 16 進数を新しく作り、呼ぶたびに違う")
        void createsForBlankTraceId(String blank) {
            // 準備：空白のトレース ID の span のあるトレーサー
            var resolver = new TraceIdResolver(provider(tracerWithTraceId(blank)));

            // 操作と確認：新しい ID
            assertNewTraceIds(resolver);
        }
    }

    // ============================== 確認の補助 ==============================

    /**
     * 新しいトレース ID を作ることを確認する。
     *
     * <p>確認すること：
     * <ul>
     *   <li>32 桁の 16 進数</li>
     *   <li>2 回呼ぶと、違う ID</li>
     * </ul>
     *
     * @param resolver 確認する resolver
     */
    private static void assertNewTraceIds(TraceIdResolver resolver) {
        var first = resolver.resolve();
        var second = resolver.resolve();
        assertThat(first).matches(NEW_TRACE_ID_FORM);
        assertThat(second).matches(NEW_TRACE_ID_FORM).isNotEqualTo(first);
    }

    // ============================== 準備の補助 ==============================

    /**
     * トレーサーを返す provider を作る。
     *
     * @param tracer トレーサー。null ならトレーサーのない provider
     * @return provider
     */
    private static ObjectProvider<Tracer> provider(Tracer tracer) {
        var beans = new StaticListableBeanFactory(tracer == null ? Map.of() : Map.of("tracer", tracer));
        return beans.getBeanProvider(Tracer.class);
    }

    /**
     * 指定のトレース ID の span を、今の span として返すトレーサーを作る。
     *
     * @param traceId トレース ID
     * @return トレーサー
     */
    private static Tracer tracerWithTraceId(String traceId) {
        var context = mock(TraceContext.class);
        when(context.traceId()).thenReturn(traceId);
        var span = mock(Span.class);
        when(span.context()).thenReturn(context);
        var tracer = mock(Tracer.class);
        when(tracer.currentSpan()).thenReturn(span);
        return tracer;
    }
}
