package com.ecsite.module.order.application.model;

import com.ecsite.common.model.value.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 注文の側の支払 1 回の金額の範囲の単体テスト
 *
 * <p>境界の 1 つ内と 1 つ外で判定が変わることを確認する。
 */
class PaymentAmountRangeTest {

    private final PaymentAmountRange range = new PaymentAmountRange(Money.of(50), Money.of(99_999_999));

    // ============================== テスト ==============================

    @ParameterizedTest(name = "「{0} 円」")
    @CsvSource({"50", "51", "99999998", "99999999"})
    @DisplayName("下限以上で上限以下の金額は、範囲の中と判定される")
    void containsAmountWithinRange(long yen) {
        // 操作と確認：下限ちょうど、下限の 1 円上、上限の 1 円下、上限ちょうどは範囲の中
        assertThat(range.contains(Money.of(yen))).isTrue();
    }

    @ParameterizedTest(name = "「{0} 円」")
    @CsvSource({"0", "1", "49", "100000000"})
    @DisplayName("下限より小さい、または上限より大きい金額は、範囲の外と判定される")
    void doesNotContainAmountOutsideRange(long yen) {
        // 操作と確認：下限の 1 円下、上限の 1 円上などは範囲の外
        assertThat(range.contains(Money.of(yen))).isFalse();
    }

    @Test
    @DisplayName("上限が下限より小さい範囲は作れない")
    void rejectsMaxBelowMin() {
        // 操作と確認：例外
        assertThatThrownBy(() -> new PaymentAmountRange(Money.of(10), Money.of(9)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
