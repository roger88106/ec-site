package com.ecsite.module.order.domain.order;

import com.ecsite.module.order.event.OrderStatusChanged;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 注文の状態の単体テスト
 *
 * <p>イベントの状態への変換だけを確認する。状態の遷移は {@code OrderTest} が確認する。
 */
class OrderStatusTest {

    // ============================== テスト ==============================

    @ParameterizedTest(name = "「{0}」")
    @EnumSource(OrderStatus.class)
    @DisplayName("AC-277 注文の状態の全ての値を、イベントの状態の同じ名前の値に写す")
    void mapsEveryStatus(OrderStatus status) {
        // 操作と確認：同じ名前の値になる
        assertThat(status.toEventStatus()).isEqualTo(OrderStatusChanged.Status.valueOf(status.name()));
    }
}
