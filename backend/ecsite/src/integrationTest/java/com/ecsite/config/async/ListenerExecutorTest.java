package com.ecsite.config.async;

import com.ecsite.support.config.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.core.MethodIntrospector;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.util.ReflectionUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * イベントのリスナーのスレッドプールの統合テスト
 *
 * <p>バックエンドのアーキテクチャの「水平スケール」に対応する。
 * 外部サービスを呼ぶリスナーは専用のスレッドプール（{@code externalServiceExecutor}）で動き、
 * それ以外のリスナーは既定のスレッドプール（{@code applicationTaskExecutor}）で動き続ける。
 * リスナーのメソッドが指すスレッドプールを、メソッドの {@code @Async} から読んで確認する。
 */
@IntegrationTest
class ListenerExecutorTest {

    // ============================== 定数 ==============================

    /**
     * 外部サービスを呼ぶリスナーのスレッドプールの Bean の名前
     */
    private static final String EXTERNAL_EXECUTOR = "externalServiceExecutor";

    /**
     * 外部サービスを呼ぶリスナー（クラス名#引数の型）
     */
    private static final List<String> EXTERNAL_LISTENERS = List.of(
            "OrderShippedHandler#OrderShipped",
            "AssetFileHandler#AssetReleased",
            "PaymentReversalHandler#VoidRequested",
            "PaymentReversalHandler#RefundRequested",
            "TrackingRequestedHandler#TrackingRequested");

    // ============================== 依存 ==============================

    @Autowired
    ApplicationContext context;

    @Autowired
    @Qualifier("applicationTaskExecutor")
    ThreadPoolTaskExecutor applicationExecutor;

    @Autowired
    @Qualifier(EXTERNAL_EXECUTOR)
    ThreadPoolTaskExecutor externalExecutor;

    // ============================== 補助 ==============================

    /**
     * モジュールのリスナーを、Async が指すスレッドプールの名前（既定は空）ごとに集める。
     *
     * @param external 外部サービスのスレッドプールを指すリスナーを集めるなら true、それ以外なら false
     * @return クラス名#引数の型の一覧
     */
    private List<String> listeners(boolean external) {
        var found = new TreeSet<String>();
        for (var name : context.getBeanDefinitionNames()) {
            var type = context.getType(name);
            if (type == null || !type.getName().startsWith("com.ecsite.module.")) {
                continue;
            }
            var target = AopUtils.getTargetClass(context.getBean(name));
            var methods = MethodIntrospector.selectMethods(target,
                    (java.lang.reflect.Method m) -> AnnotatedElementUtils.hasAnnotation(m, TransactionalEventListener.class));
            for (var method : methods) {
                var async = AnnotatedElementUtils.findMergedAnnotation(method, Async.class);
                var isExternal = async != null && EXTERNAL_EXECUTOR.equals(async.value());
                if (isExternal == external) {
                    found.add(target.getSimpleName() + "#" + method.getParameterTypes()[0].getSimpleName());
                }
            }
        }
        return new ArrayList<>(found);
    }

    private static Object field(ThreadPoolTaskExecutor executor, String name) {
        var field = ReflectionUtils.findField(executor.getClass(), name);
        ReflectionUtils.makeAccessible(field);
        return ReflectionUtils.getField(field, executor);
    }

    // ============================== テスト ==============================

    @Nested
    @DisplayName("リスナーのスレッドプール")
    class Listeners {

        @Test
        @DisplayName("DC-556-8 外部サービスを呼ぶ 4 つのクラスのリスナーは、外部サービス用のスレッドプールで動く")
        void externalListenersUseExternalExecutor() {
            // 操作：外部サービス用のスレッドプールを指すリスナーを集める
            var external = listeners(true);

            // 確認：外部を呼ぶリスナーだけが並ぶ
            assertThat(external).containsExactlyInAnyOrderElementsOf(EXTERNAL_LISTENERS);
        }

        @Test
        @DisplayName("DC-556-8 外部サービスを呼ばないリスナーは、既定のスレッドプールのまま動く")
        void otherListenersUseDefaultExecutor() {
            // 操作：外部サービス用でないリスナーを集める
            var others = listeners(false);

            // 確認：外部を呼ぶリスナーを含まず、外部を呼ばないリスナーがある
            assertThat(others).isNotEmpty().doesNotContainAnyElementsOf(EXTERNAL_LISTENERS);
        }
    }

    @Nested
    @DisplayName("スレッドプールの設定")
    class Pools {

        @Test
        @DisplayName("DC-556-8 外部サービス用のスレッドプールは、スレッド 4、キューの上限 1000、名前の接頭辞 external-")
        void externalPool() {
            // 確認：設定の値
            assertThat(externalExecutor.getThreadNamePrefix()).isEqualTo("external-");
            assertThat(externalExecutor.getCorePoolSize()).isEqualTo(4);
            assertThat(externalExecutor.getMaxPoolSize()).isEqualTo(4);
            assertThat(externalExecutor.getThreadPoolExecutor().getQueue().remainingCapacity()).isEqualTo(1000);
        }

        @Test
        @DisplayName("DC-556-8 既定のスレッドプールは、スレッド 8、キューの上限 1000")
        void defaultPool() {
            // 確認：設定の値
            assertThat(applicationExecutor.getCorePoolSize()).isEqualTo(8);
            assertThat(applicationExecutor.getMaxPoolSize()).isEqualTo(8);
            assertThat(applicationExecutor.getThreadPoolExecutor().getQueue().remainingCapacity()).isEqualTo(1000);
        }

        @Test
        @DisplayName("DC-556-9 両方のスレッドプールが、停止のときに処理中のタスクを 30 秒まで待つ")
        void awaitsTerminationOnBoth() {
            // 確認：両方のスレッドプールの停止の待ち
            for (var executor : List.of(applicationExecutor, externalExecutor)) {
                assertThat(field(executor, "waitForTasksToCompleteOnShutdown")).isEqualTo(true);
                assertThat(field(executor, "awaitTerminationMillis")).isEqualTo(30_000L);
            }
        }
    }
}
