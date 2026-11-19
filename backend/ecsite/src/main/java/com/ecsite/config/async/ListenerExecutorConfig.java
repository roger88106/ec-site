package com.ecsite.config.async;

import com.ecsite.common.event.ExternalServiceListener;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.boot.task.ThreadPoolTaskExecutorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.validation.annotation.Validated;

/**
 * イベントのリスナーのスレッドプールの設定
 *
 * <p>外部サービスを呼ぶリスナー（{@link ExternalServiceListener}）は、専用のスレッドプールで動かす。
 * 外部サービスが遅いとき、外部を呼ばないリスナーのスレッドを使い切らないため。
 * 外部を呼ばないリスナーのスレッドプール（{@code applicationTaskExecutor}）は、{@code spring.task.execution} で設定する。
 * 別のスレッドプールの Bean があると {@code applicationTaskExecutor} が作られなくなるので、{@code spring.task.execution.mode=force} で作らせる。
 * 両方のスレッドプールを、Spring Boot のビルダーで作るので、停止のときの待ち（{@code spring.task.execution.shutdown}）が両方に反映される。
 */
@Configuration
class ListenerExecutorConfig {

    /**
     * 外部サービスを呼ぶリスナーのスレッドプールを作る。
     *
     * <p>キューが満杯で拒否されたイベントは、未完了のまま残り、再発行のジョブが処理する。
     *
     * @param builder    Spring Boot のスレッドプールのビルダー（停止の待ちの設定を含む）
     * @param properties スレッドの数とキューの上限
     * @return スレッドプール
     */
    @Bean(ExternalServiceListener.EXECUTOR)
    ThreadPoolTaskExecutor externalServiceExecutor(ThreadPoolTaskExecutorBuilder builder, Properties properties) {
        return builder
                .corePoolSize(properties.threads())
                .maxPoolSize(properties.threads())
                .queueCapacity(properties.queueCapacity())
                .threadNamePrefix("external-")
                .build();
    }

    /**
     * 外部サービスを呼ぶリスナーのスレッドプールの設定
     *
     * <p>環境変数で上書きする（例：ECSITE_ASYNC_EXTERNAL_SERVICE_THREADS）。
     *
     * @param threads       スレッドの数
     * @param queueCapacity キューの上限
     */
    @Validated
    @ConfigurationProperties("ecsite.async.external-service")
    record Properties(
            @DefaultValue("4") @Positive int threads,
            @DefaultValue("1000") @Positive int queueCapacity) {
    }
}
