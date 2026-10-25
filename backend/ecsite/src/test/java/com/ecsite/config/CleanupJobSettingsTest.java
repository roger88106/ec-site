package com.ecsite.config;

import com.ecsite.TestSettings;
import com.ecsite.module.cart.application.CartCleanupProperties;
import com.ecsite.module.inventory.application.StockReservationCleanupProperties;
import com.ecsite.module.order.application.checkout.CheckoutCleanupProperties;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 片付けのジョブの設定と分散ロックの単体テスト。<br />
 * 設定は Spring を起動せずに、設定の型へ値を当てはめて確認する。処理の済んだイベントの片付けは {@code EventCleanupJobSettingsTest} が確認する。
 */
class CleanupJobSettingsTest {

    @Test
    @DisplayName("AC-285 片付けのジョブの間隔は既定で 1 時間、保存期間は引当 1 日・注文手続き 1 日・カート 0")
    void defaults() {
        var reservations = TestSettings.bind("ecsite.inventory.cleanup", StockReservationCleanupProperties.class, Map.of());
        var checkouts = TestSettings.bind("ecsite.order.cleanup", CheckoutCleanupProperties.class, Map.of());
        var carts = TestSettings.bind("ecsite.cart.cleanup", CartCleanupProperties.class, Map.of());

        assertThat(reservations.interval()).isEqualTo(Duration.ofHours(1));
        assertThat(reservations.retention()).isEqualTo(Duration.ofDays(1));
        assertThat(checkouts.interval()).isEqualTo(Duration.ofHours(1));
        assertThat(checkouts.retention()).isEqualTo(Duration.ofDays(1));
        assertThat(carts.interval()).isEqualTo(Duration.ofHours(1));
        assertThat(carts.retention()).isZero();
        assertThat(reservations.batchSize()).isEqualTo(1000);
        assertThat(checkouts.batchSize()).isEqualTo(1000);
        assertThat(carts.batchSize()).isEqualTo(1000);
    }

    @Test
    @DisplayName("AC-285 間隔と保存期間は、設定で変えられる")
    void overridesBySettings() {
        var values = Map.of("interval", "10m", "retention", "3d");

        assertThat(TestSettings.bind("ecsite.inventory.cleanup", StockReservationCleanupProperties.class, values))
                .extracting(StockReservationCleanupProperties::interval, StockReservationCleanupProperties::retention)
                .containsExactly(Duration.ofMinutes(10), Duration.ofDays(3));
        assertThat(TestSettings.bind("ecsite.order.cleanup", CheckoutCleanupProperties.class, values))
                .extracting(CheckoutCleanupProperties::interval, CheckoutCleanupProperties::retention)
                .containsExactly(Duration.ofMinutes(10), Duration.ofDays(3));
        assertThat(TestSettings.bind("ecsite.cart.cleanup", CartCleanupProperties.class, values))
                .extracting(CartCleanupProperties::interval, CartCleanupProperties::retention)
                .containsExactly(Duration.ofMinutes(10), Duration.ofDays(3));
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({
            "com.ecsite.module.inventory.application.StockReservationCleanupJob, stock-reservation-cleanup",
            "com.ecsite.module.order.application.checkout.CheckoutCleanupJob, checkout-cleanup",
            "com.ecsite.module.cart.application.CartCleanupJob, cart-cleanup"})
    @DisplayName("AC-287 片付けのジョブは、定期に動き、分散ロックで同時に 1 つのインスタンスだけが動く")
    void runsWithDistributedLock(String jobClass, String lockName) throws Exception {
        var method = Class.forName(jobClass).getDeclaredMethod("cleanUp");

        assertThat(method.getAnnotation(Scheduled.class)).isNotNull();
        assertThat(method.getAnnotation(SchedulerLock.class)).isNotNull()
                .extracting(SchedulerLock::name).isEqualTo(lockName);
    }
}
