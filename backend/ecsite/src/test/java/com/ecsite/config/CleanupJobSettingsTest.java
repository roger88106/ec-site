package com.ecsite.config;

import com.ecsite.module.cart.application.job.CartCleanupProperties;
import com.ecsite.module.inventory.application.job.StockReservationCleanupProperties;
import com.ecsite.module.media.application.job.MediaCleanupProperties;
import com.ecsite.module.order.application.job.CheckoutCleanupProperties;
import com.ecsite.support.TestSettings;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.bind.BindException;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
     * 仮のアセットの片付けの設定の接頭辞
     */
    private static final String PENDING_ASSETS = "ecsite.media.cleanup";

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

    /**
     * 設定で変えた件数の上限
     */
    private static final int OVERRIDDEN_BATCH_SIZE = 50;

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

        @Test
        @DisplayName("DC-489-4 仮のアセットの片付けの既定は、間隔 1 時間、保存期間 1 日")
        void pendingAssets() {
            // 操作：設定ファイルを読んで当てはめる
            var properties = TestSettings.loadAndBind(PENDING_ASSETS, MediaCleanupProperties.class);

            // 確認：既定の値
            assertThat(properties.interval()).isEqualTo(DEFAULT_INTERVAL);
            assertThat(properties.retention()).isEqualTo(Duration.ofDays(1));
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

        @Test
        @DisplayName("DC-489-4 仮のアセットの片付けの間隔、保存期間、件数の上限は、設定で変えられる")
        void pendingAssets() {
            // 操作：間隔、保存期間、件数の上限を渡して当てはめる
            var properties = TestSettings.bind(PENDING_ASSETS, MediaCleanupProperties.class,
                    Map.of("interval", "10m", "retention", "3d", "batch-size", "50"));

            // 確認：渡した値になる
            assertThat(properties.interval()).isEqualTo(OVERRIDDEN_INTERVAL);
            assertThat(properties.retention()).isEqualTo(OVERRIDDEN_RETENTION);
            assertThat(properties.batchSize()).isEqualTo(OVERRIDDEN_BATCH_SIZE);
        }
    }

    @Nested
    @DisplayName("仮のアセットの保存期間の下限")
    class PendingAssetRetentionMinimum {

        @Test
        @DisplayName("DC-489-4 仮のアセットの保存期間が下限の 1 分なら、値の検証を通り、1 分になる")
        void acceptsOneMinute() {
            // 操作：1 分を検証付きで当てはめる
            var properties = TestSettings.bindValidated(PENDING_ASSETS, MediaCleanupProperties.class, Map.of("retention", "1m"));

            // 確認：1 分
            assertThat(properties.retention()).isEqualTo(Duration.ofMinutes(1));
        }

        @ParameterizedTest(name = "「{0}」")
        @ValueSource(strings = {"59999999999ns", "0s", "-1s"})
        @DisplayName("DC-489-4 仮のアセットの保存期間が 1 分未満なら、値の検証で拒否される")
        void rejectsBelowOneMinute(String value) {
            // 操作と確認：値の検証で拒否される（起動に失敗する）
            assertThatThrownBy(() -> TestSettings.bindValidated(PENDING_ASSETS, MediaCleanupProperties.class, Map.of("retention", value)))
                    .isInstanceOf(BindException.class);
        }
    }

    @Nested
    @DisplayName("1 回に消す件数の下限")
    class BatchSizeMinimum {

        @ParameterizedTest(name = "「{0}」")
        @CsvSource({
                "ecsite.inventory.cleanup, com.ecsite.module.inventory.application.job.StockReservationCleanupProperties",
                "ecsite.order.cleanup, com.ecsite.module.order.application.job.CheckoutCleanupProperties",
                "ecsite.cart.cleanup, com.ecsite.module.cart.application.job.CartCleanupProperties",
                "ecsite.media.cleanup, com.ecsite.module.media.application.job.MediaCleanupProperties"})
        @DisplayName("1 回に消す件数が下限の 1 なら、検証を通り、1 件になる")
        void acceptsBatchSizeAtMinimum(String prefix, Class<?> type) {
            // 操作：1 件を検証付きで当てはめる
            var properties = TestSettings.bindValidated(prefix, type, Map.of("batch-size", "1"));

            // 確認：1 件
            assertThat(properties).extracting("batchSize").isEqualTo(1);
        }

        @ParameterizedTest(name = "「{0}」")
        @CsvSource({
                "ecsite.inventory.cleanup, com.ecsite.module.inventory.application.job.StockReservationCleanupProperties",
                "ecsite.order.cleanup, com.ecsite.module.order.application.job.CheckoutCleanupProperties",
                "ecsite.cart.cleanup, com.ecsite.module.cart.application.job.CartCleanupProperties",
                "ecsite.media.cleanup, com.ecsite.module.media.application.job.MediaCleanupProperties"})
        @DisplayName("1 回に消す件数が下限より 1 小さい 0 なら、値の検証で拒否される")
        void rejectsBatchSizeBelowMinimum(String prefix, Class<?> type) {
            // 操作と確認：0 件は拒否される
            assertThatThrownBy(() -> TestSettings.bindValidated(prefix, type, Map.of("batch-size", "0")))
                    .isInstanceOf(BindException.class);
        }
    }

    @Nested
    @DisplayName("時間の設定の下限")
    class DurationBounds {

        @ParameterizedTest(name = "「{0}」の間隔が {2}")
        @CsvSource({
                "ecsite.inventory.cleanup, com.ecsite.module.inventory.application.job.StockReservationCleanupProperties, 0s",
                "ecsite.inventory.cleanup, com.ecsite.module.inventory.application.job.StockReservationCleanupProperties, -1s",
                "ecsite.order.cleanup, com.ecsite.module.order.application.job.CheckoutCleanupProperties, 0s",
                "ecsite.order.cleanup, com.ecsite.module.order.application.job.CheckoutCleanupProperties, -1s",
                "ecsite.cart.cleanup, com.ecsite.module.cart.application.job.CartCleanupProperties, 0s",
                "ecsite.cart.cleanup, com.ecsite.module.cart.application.job.CartCleanupProperties, -1s",
                "ecsite.inventory.cleanup, com.ecsite.module.inventory.application.job.StockReservationCleanupProperties, 999us",
                "ecsite.order.cleanup, com.ecsite.module.order.application.job.CheckoutCleanupProperties, 999us",
                "ecsite.cart.cleanup, com.ecsite.module.cart.application.job.CartCleanupProperties, 999us",
                "ecsite.media.cleanup, com.ecsite.module.media.application.job.MediaCleanupProperties, 0s",
                "ecsite.media.cleanup, com.ecsite.module.media.application.job.MediaCleanupProperties, -1s",
                "ecsite.media.cleanup, com.ecsite.module.media.application.job.MediaCleanupProperties, 999us"})
        @DisplayName("片付けの間隔が 0、負の値、下限の 1ms 未満（999us）なら、値の検証で拒否される")
        void rejectsIntervalBelowMinimum(String prefix, Class<?> type, String value) {
            // 操作と確認：値の検証で拒否される
            assertThatThrownBy(() -> TestSettings.bindValidated(prefix, type, Map.of("interval", value)))
                    .isInstanceOf(BindException.class);
        }

        @ParameterizedTest(name = "「{0}」")
        @CsvSource({
                "ecsite.inventory.cleanup, com.ecsite.module.inventory.application.job.StockReservationCleanupProperties",
                "ecsite.order.cleanup, com.ecsite.module.order.application.job.CheckoutCleanupProperties",
                "ecsite.cart.cleanup, com.ecsite.module.cart.application.job.CartCleanupProperties",
                "ecsite.media.cleanup, com.ecsite.module.media.application.job.MediaCleanupProperties"})
        @DisplayName("片付けの間隔が下限の 1ms なら、値の検証を通り、その値になる")
        void acceptsIntervalAtMinimum(String prefix, Class<?> type) {
            // 操作：1ms を検証付きで当てはめる
            var properties = TestSettings.bindValidated(prefix, type, Map.of("interval", "1ms"));

            // 確認：1ms になる
            assertThat(properties).extracting("interval").isEqualTo(Duration.ofMillis(1));
        }

        @ParameterizedTest(name = "「{0}」")
        @CsvSource({
                "ecsite.inventory.cleanup, com.ecsite.module.inventory.application.job.StockReservationCleanupProperties",
                "ecsite.order.cleanup, com.ecsite.module.order.application.job.CheckoutCleanupProperties",
                "ecsite.cart.cleanup, com.ecsite.module.cart.application.job.CartCleanupProperties"})
        @DisplayName("保存期間が負なら、値の検証で拒否される")
        void rejectsNegativeRetention(String prefix, Class<?> type) {
            // 操作と確認：-1s は値の検証で拒否される
            assertThatThrownBy(() -> TestSettings.bindValidated(prefix, type, Map.of("retention", "-1s")))
                    .isInstanceOf(BindException.class);
        }

        @ParameterizedTest(name = "「{0}」")
        @CsvSource({
                "ecsite.inventory.cleanup, com.ecsite.module.inventory.application.job.StockReservationCleanupProperties",
                "ecsite.order.cleanup, com.ecsite.module.order.application.job.CheckoutCleanupProperties",
                "ecsite.cart.cleanup, com.ecsite.module.cart.application.job.CartCleanupProperties"})
        @DisplayName("保存期間が 0 なら、値の検証を通り、0 になる")
        void acceptsZeroRetention(String prefix, Class<?> type) {
            // 操作：0s を検証付きで当てはめる
            var properties = TestSettings.bindValidated(prefix, type, Map.of("retention", "0s"));

            // 確認：0 になる
            assertThat(properties).extracting("retention").isEqualTo(Duration.ZERO);
        }
    }

    @Nested
    @DisplayName("分散ロック")
    class Lock {

        @ParameterizedTest(name = "「{0}」")
        @CsvSource({
                "com.ecsite.module.inventory.application.job.StockReservationCleanupJob, stock-reservation-cleanup",
                "com.ecsite.module.order.application.job.CheckoutCleanupJob, checkout-cleanup",
                "com.ecsite.module.cart.application.job.CartCleanupJob, cart-cleanup",
                "com.ecsite.module.media.application.job.MediaCleanupJob, media-cleanup"})
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
