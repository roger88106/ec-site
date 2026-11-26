package com.ecsite.module.order.application;

import com.ecsite.support.TestSettings;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.bind.BindException;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 注文の設定の単体テスト
 *
 * <p>支払待ちの注文の上限の設定の読み込みと検証だけを確認する。上限で注文を拒否することは、注文の API の統合テストが
 * 確認する。
 */
class OrderPropertiesTest {

    // ============================== 定数 ==============================

    /**
     * 注文の設定の接頭辞
     */
    private static final String PREFIX = "ecsite.order";

    /**
     * 既定の値を読む設定ファイルの profile
     *
     * <p>{@code application.yml} だけでは、必須の送料の税区分がなく検証に失敗するので、それを書く profile を重ねる。
     */
    private static final String SETTINGS_PROFILE = "local";

    // ============================== テスト ==============================

    @Nested
    @DisplayName("支払待ちの注文の上限の値")
    class Value {

        @Test
        @DisplayName("NFR-411-2 支払待ちの注文の上限は、設定がなければ 3 件である")
        void defaultsPendingOrderLimitToThree() {
            // 操作：設定ファイルの値で読み込む（設定ファイルは上限を書かない。送料の税区分を書く local の profile を重ねる）
            var properties = TestSettings.loadAndBind(PREFIX, OrderProperties.class, SETTINGS_PROFILE);

            // 確認：3 件
            assertThat(properties.pendingOrderLimit()).isEqualTo(3);
        }

        @Test
        @DisplayName("NFR-411-2 支払待ちの注文の上限は、設定で変えられる")
        void overridesPendingOrderLimit() {
            // 操作：上限を 5 件に設定して読み込む
            var properties = TestSettings.bind(PREFIX, OrderProperties.class, Map.of("pending-order-limit", "5"));

            // 確認：設定の値
            assertThat(properties.pendingOrderLimit()).isEqualTo(5);
        }
    }

    @Nested
    @DisplayName("支払待ちの注文の上限の検証")
    class Validation {

        @Test
        @DisplayName("NFR-411-2 支払待ちの注文の上限が 1 未満なら、値の検証で拒否され、pendingOrderLimit が知らされる")
        void rejectsLimitBelowOne() {
            // 操作と確認：上限が 0 件は、値の検証で拒否され、原因に設定の名前がある
            assertThatThrownBy(() -> TestSettings.bindValidated(PREFIX, OrderProperties.class,
                    Map.of("shipping-tax-category-id", "00000000-0000-0000-0000-000000000001", "pending-order-limit", "0")))
                    .isInstanceOf(BindException.class)
                    .rootCause().hasMessageContaining("pendingOrderLimit");
        }

        @Test
        @DisplayName("NFR-411-2 支払待ちの注文の上限が下限の 1 なら、値の検証を通り、1 件になる")
        void acceptsLimitOfOne() {
            // 操作：上限を 1 件に、検証付きで当てはめる
            var properties = TestSettings.bindValidated(PREFIX, OrderProperties.class,
                    Map.of("shipping-tax-category-id", "00000000-0000-0000-0000-000000000001", "pending-order-limit", "1"));

            // 確認：1 件
            assertThat(properties.pendingOrderLimit()).isEqualTo(1);
        }
    }

    @ParameterizedTest(name = "「{0}」")
    @ValueSource(strings = {"0s", "-1s", "999us"})
    @DisplayName("在庫の引当を保持する時間が 0、負の値、下限の 1ms 未満（999us）なら、値の検証で拒否される")
    void rejectsReservationTtlBelowMinimum(String value) {
        // 操作と確認：必須の値と許す範囲の外の時間を渡すと、値の検証で拒否される
        assertThatThrownBy(() -> TestSettings.bindValidated(PREFIX, OrderProperties.class,
                Map.of("shipping-tax-category-id", "00000000-0000-0000-0000-000000000001", "reservation-ttl", value)))
                .isInstanceOf(BindException.class);
    }

    @Test
    @DisplayName("在庫の引当を保持する時間が下限の 1ms なら、値の検証を通り、その値になる")
    void acceptsReservationTtlAtMinimum() {
        // 操作：1ms を検証付きで当てはめる
        var properties = TestSettings.bindValidated(PREFIX, OrderProperties.class,
                Map.of("shipping-tax-category-id", "00000000-0000-0000-0000-000000000001", "reservation-ttl", "1ms"));

        // 確認：1ms になる
        assertThat(properties.reservationTtl()).isEqualTo(Duration.ofMillis(1));
    }
}
