package com.ecsite.common.error.handler;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * エラーレスポンスに入れるトレース ID を決める。<br />
 * 現在の span があればそのトレース ID を使う。span がない場合（トレーシングが無効、サンプリングの対象外など）は、
 * 新しい ID を作る。どちらの場合も、エラーのログに同じ ID を出すことで、問い合わせとログを結び付けられるようにする。
 */
@Component
@RequiredArgsConstructor
class TraceIdResolver {

    /**
     * トレーサー（トレーシングが構成されていない場合は無い）
     */
    private final ObjectProvider<Tracer> tracer;

    /**
     * トレース ID を返す。
     *
     * @return トレース ID（16 進数の文字列）
     */
    String resolve() {
        return Optional.ofNullable(tracer.getIfAvailable())
                .map(Tracer::currentSpan)
                .map(Span::context)
                .map(TraceContext::traceId)
                .filter(traceId -> !traceId.isBlank())
                .orElseGet(TraceIdResolver::newTraceId);
    }

    /**
     * 新しいトレース ID を作る。W3C のトレース ID と同じ 32 桁の 16 進数にする。
     *
     * @return トレース ID
     */
    private static String newTraceId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
