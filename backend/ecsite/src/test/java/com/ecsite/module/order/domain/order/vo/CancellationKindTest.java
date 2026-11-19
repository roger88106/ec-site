package com.ecsite.module.order.domain.order.vo;

import com.ecsite.module.order.event.OrderStatusChanged;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * キャンセルの種類の単体テスト
 *
 * <p>イベントの種類への変換だけを確認する。キャンセルの流れは {@code OrderCancellationTest} が確認する。
 */
class CancellationKindTest {

    // ============================== テスト ==============================

    @ParameterizedTest(name = "「{0}」")
    @EnumSource(CancellationKind.class)
    @DisplayName("AC-277 キャンセルの種類の全ての値を、イベントの種類の同じ名前の値に写す")
    void mapsEveryKind(CancellationKind kind) {
        // 操作と確認：同じ名前の値になる
        assertThat(kind.toEventKind()).isEqualTo(OrderStatusChanged.CancellationKind.valueOf(kind.name()));
    }
}
