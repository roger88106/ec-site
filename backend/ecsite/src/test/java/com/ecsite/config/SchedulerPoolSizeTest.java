package com.ecsite.config;

import com.ecsite.support.ProductionClasses;
import com.ecsite.support.TestSettings;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.task.TaskSchedulingProperties;
import org.springframework.scheduling.annotation.Scheduled;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * スケジューラーのスレッドの数の単体テスト
 *
 * <p>定期ジョブが互いを待たないよう、スケジューラーのスレッドの数は、定期ジョブ（{@code @Scheduled} のメソッド）の数に合わせる。
 * ジョブを加えて設定の値を増やし忘れると、このテストが失敗する。
 */
class SchedulerPoolSizeTest {

    @Test
    @DisplayName("スケジューラーのスレッドの数が、定期ジョブ（@Scheduled のメソッド）の数と等しい")
    void schedulerPoolSizeEqualsScheduledMethodCount() {
        // 準備：本番のクラスの @Scheduled のメソッドを数える
        var scheduledMethods = ProductionClasses.all().stream()
                .flatMap(javaClass -> javaClass.getMethods().stream())
                .filter(method -> method.isAnnotatedWith(Scheduled.class))
                .count();

        // 操作：設定ファイルのスケジューラーのスレッドの数を読む
        var poolSize = TestSettings.loadAndBind("spring.task.scheduling", TaskSchedulingProperties.class)
                .getPool().getSize();

        // 確認：等しい（定期ジョブが 1 つもない状態は、数え方の誤りなので失敗にする）
        assertThat(scheduledMethods).isPositive();
        assertThat(poolSize).isEqualTo(scheduledMethods);
    }
}
