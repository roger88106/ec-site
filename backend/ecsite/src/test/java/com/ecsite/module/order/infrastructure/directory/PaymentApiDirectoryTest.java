package com.ecsite.module.order.infrastructure.directory;

import com.ecsite.module.order.application.model.view.OrderPayment;
import com.ecsite.module.payment.api.model.PaymentSummary;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 決済モジュールの型を、注文の型に変換するときの、列挙の変換の単体テスト
 *
 * <p>列挙の値の対応だけを確認する。
 */
class PaymentApiDirectoryTest {

    // ============================== テスト ==============================

    @ParameterizedTest(name = "「{0}」")
    @EnumSource(PaymentSummary.Status.class)
    @DisplayName("AC-277 決済の支払の状態の全ての値を、注文の支払の状態の同じ名前の値に写す")
    void mapsEveryStatus(PaymentSummary.Status status) {
        // 操作と確認：同じ名前の値になる
        assertThat(PaymentApiDirectory.toStatus(status)).isEqualTo(OrderPayment.Status.valueOf(status.name()));
    }
}
