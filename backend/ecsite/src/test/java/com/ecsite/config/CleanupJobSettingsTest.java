package com.ecsite.config;

import com.ecsite.module.cart.application.job.CartCleanupProperties;
import com.ecsite.module.inventory.application.job.StockReservationCleanupProperties;
import com.ecsite.module.order.application.job.CheckoutCleanupProperties;
import com.ecsite.support.TestSettings;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 片付けのジョブの設定と分散ロックの単体テスト
 *
 * <p>設定は Spring を起動せずに確認する。既定は、設定ファイル（{@code application.yml}）の値を含めて、起動したアプリが使う値を確認する。
 * 処理の済んだイベントの片付けは {@code EventCleanupJobSettingsTest} が確認する。
 */
class CleanupJobSettingsTest {

    // ============================== 定数 ==============================

    /**
     * 引当の片付けの設定の接頭辞
     */
    private static final String RESERVATIONS = "ecsite.inventory.cleanup";

    /**
     * 注文手続きの片付けの設定の接頭辞
     */
    private static final String CHECKOUTS = "ecsite.order.cleanup";

    /**
     * カートの片付けの設定の接頭辞
     */
    private static final String CARTS = "ecsite.cart.cleanup";

    /**
     * 片付けの間隔の既定
     */
    private static final Duration DEFAULT_INTERVAL = Duration.ofHours(1);

    /**
     * 1 回に消す件数の上限の既定
     */
    private static final int DEFAULT_BATCH_SIZE = 1000;

    /**
     * 設定で変える値（間隔 10 分、保存期間 3 日）
     */
    private static final Map<String, String> OVERRIDES = Map.of("interval", "10m", "retention", "3d");

    /**
     * 設定で変えた間隔
     */
    private static final Duration OVERRIDDEN_INTERVAL = Duration.ofMinutes(10);

    /**
     * 設定で変えた保存期間
     */
    private static final Duration OVERRIDDEN_RETENTION = Duration.ofDays(3);

    // ============================== テスト ==============================

    @Nested
    @DisplayName("既定の値")
    class Defaults {

        @Test
        @DisplayName("AC-285 引当の片付けの既定は、間隔 1 時間、保存期間 1 日")
        void reservations() {
            // 操作：設定ファイルを読んで当てはめる
            var properties = TestSettings.loadAndBind(RESERVATIONS, StockReservationCleanupProperties.class);

            // 確認：既定の値
            assertThat(properties.interval()).isEqualTo(DEFAULT_INTERVAL);
            assertThat(properties.retention()).isEqualTo(Duration.ofDays(1));
            assertThat(properties.batchSize()).isEqualTo(DEFAULT_BATCH_SIZE);
        }

        @Test
        @DisplayName("AC-284、285 注文手続きの片付けの既定は、間隔 1 時間、保存期間 1 日")
        void checkouts() {
            // 操作：設定ファイルを読んで当てはめる
            var properties = TestSettings.loadAndBind(CHECKOUTS, CheckoutCleanupProperties.class);

            // 確認：既定の値
            assertThat(properties.interval()).isEqualTo(DEFAULT_INTERVAL);
            assertThat(properties.retention()).isEqualTo(Duration.ofDays(1));
            assertThat(properties.batchSize()).isEqualTo(DEFAULT_BATCH_SIZE);
        }

        @Test
        @DisplayName("AC-285 カートの片付けの既定は、間隔 1 時間、保存期間 0")
        void carts() {
            // 操作：設定ファイルを読んで当てはめる
            var properties = TestSettings.loadAndBind(CARTS, CartCleanupProperties.class);

            // 確認：既定の値
            assertThat(properties.interval()).isEqualTo(DEFAULT_INTERVAL);
            assertThat(properties.retention()).isZero();
            assertThat(properties.batchSize()).isEqualTo(DEFAULT_BATCH_SIZE);
        }
    }

    @Nested
    @DisplayName("設定で変える")
    class Overrides {

        @Test
        @DisplayName("AC-285 引当の片付けの間隔と保存期間は、設定で変えられる")
        void reservations() {
            // 操作：間隔と保存期間を渡して当てはめる
            var properties = TestSettings.bind(RESERVATIONS, StockReservationCleanupProperties.class, OVERRIDES);

            // 確認：渡した値になる
            assertThat(properties.interval()).isEqualTo(OVERRIDDEN_INTERVAL);
            assertThat(properties.retention()).isEqualTo(OVERRIDDEN_RETENTION);
        }

        @Test
        @DisplayName("AC-285 注文手続きの片付けの間隔と保存期間は、設定で変えられる")
        void checkouts() {
            // 操作：間隔と保存期間を渡して当てはめる
            var properties = TestSettings.bind(CHECKOUTS, CheckoutCleanupProperties.class, OVERRIDES);

            // 確認：渡した値になる
            assertThat(properties.interval()).isEqualTo(OVERRIDDEN_INTERVAL);
            assertThat(properties.retention()).isEqualTo(OVERRIDDEN_RETENTION);
        }

        @Test
        @DisplayName("AC-285 カートの片付けの間隔と保存期間は、設定で変えられる")
        void carts() {
            // 操作：間隔と保存期間を渡して当てはめる
            var properties = TestSettings.bind(CARTS, CartCleanupProperties.class, OVERRIDES);

            // 確認：渡した値になる
            assertThat(properties.interval()).isEqualTo(OVERRIDDEN_INTERVAL);
            assertThat(properties.retention()).isEqualTo(OVERRIDDEN_RETENTION);
        }
    }

    @Nested
    @DisplayName("分散ロック")
    class Lock {

        @ParameterizedTest(name = "「{0}」")
        @CsvSource({
                "com.ecsite.module.inventory.application.job.StockReservationCleanupJob, stock-reservation-cleanup",
                "com.ecsite.module.order.application.job.CheckoutCleanupJob, checkout-cleanup",
                "com.ecsite.module.cart.application.job.CartCleanupJob, cart-cleanup"})
        @DisplayName("AC-287 片付けのジョブは、定期に動き、分散ロックで同時に 1 つのインスタンスだけが動く")
        void runsWithDistributedLock(String jobClass, String lockName) throws Exception {
            // 準備：ジョブの片付けのメソッド
            var method = Class.forName(jobClass).getDeclaredMethod("cleanUp");

            // 確認：定期に動き、ジョブごとの名前の分散ロックを取る
            assertThat(method.getAnnotation(Scheduled.class)).isNotNull();
            assertThat(method.getAnnotation(SchedulerLock.class)).isNotNull()
                    .extracting(SchedulerLock::name).isEqualTo(lockName);
        }
    }
}
