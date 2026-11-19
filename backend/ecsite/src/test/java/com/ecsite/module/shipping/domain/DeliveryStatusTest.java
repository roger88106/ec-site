package com.ecsite.module.shipping.domain;

import com.ecsite.module.shipping.event.DeliveryStatusChanged;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 配送状態の単体テスト
 *
 * <p>イベントの配送状態への変換だけを確認する。
 */
class DeliveryStatusTest {

    // ============================== テスト ==============================

    @ParameterizedTest(name = "「{0}」")
    @EnumSource(DeliveryStatus.class)
    @DisplayName("AC-277 配送状態の全ての値を、イベントの配送状態の同じ名前の値に写す")
    void mapsEveryStatus(DeliveryStatus status) {
        // 操作と確認：同じ名前の値になる
        assertThat(status.toEventStatus()).isEqualTo(DeliveryStatusChanged.Status.valueOf(status.name()));
    }
}
