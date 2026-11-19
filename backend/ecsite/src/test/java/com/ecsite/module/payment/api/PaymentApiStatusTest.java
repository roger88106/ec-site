package com.ecsite.module.payment.api;

import com.ecsite.module.payment.api.model.PaymentSummary;
import com.ecsite.module.payment.domain.payment.PaymentStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 支払の状態を、公開の型に変換するときの単体テスト
 */
class PaymentApiStatusTest {

    // ============================== テスト ==============================

    @ParameterizedTest(name = "「{0}」")
    @EnumSource(PaymentStatus.class)
    @DisplayName("AC-277 支払の状態の全ての値を、公開の型の同じ名前の値に写す")
    void mapsEveryStatus(PaymentStatus status) {
        // 操作と確認：同じ名前の値になる
        assertThat(PaymentApi.toStatus(status)).isEqualTo(PaymentSummary.Status.valueOf(status.name()));
    }
}
