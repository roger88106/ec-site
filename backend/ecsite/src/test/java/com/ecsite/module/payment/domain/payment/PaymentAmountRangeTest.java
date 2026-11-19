package com.ecsite.module.payment.domain.payment;

import com.ecsite.common.model.value.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 支払 1 回の金額の範囲の単体テスト
 *
 * <p>範囲は 50 円以上 99,999,999 円以下で、境界の 1 つ内と 1 つ外で判定が変わることを確認する。
 */
class PaymentAmountRangeTest {

    // ============================== テスト ==============================

    @Test
    @DisplayName("BR-518-1 範囲の下限は 50 円、上限は 99,999,999 円である")
    void hasMinAndMax() {
        // 操作：決済の範囲を取る
        var range = PaymentAmountRange.STANDARD;

        // 確認：下限と上限
        assertThat(range.min()).isEqualTo(Money.of(50));
        assertThat(range.max()).isEqualTo(Money.of(99_999_999));
    }

    @ParameterizedTest(name = "「{0} 円」")
    @CsvSource({"50", "51", "99999998", "99999999"})
    @DisplayName("BR-518-1 下限以上で上限以下の金額は、範囲の中と判定される")
    void containsAmountWithinRange(long yen) {
        // 操作と確認：下限ちょうど、下限の 1 円上、上限の 1 円下、上限ちょうどは範囲の中
        assertThat(PaymentAmountRange.STANDARD.contains(Money.of(yen))).isTrue();
    }

    @ParameterizedTest(name = "「{0} 円」")
    @CsvSource({"0", "49", "100000000", "100000001"})
    @DisplayName("BR-518-1 下限より小さいか上限より大きい金額は、範囲の外と判定される")
    void rejectsAmountOutOfRange(long yen) {
        // 操作と確認：0 円、下限の 1 円下、上限の 1 円上とその次は範囲の外
        assertThat(PaymentAmountRange.STANDARD.contains(Money.of(yen))).isFalse();
    }

    @Test
    @DisplayName("下限が上限より大きい範囲は作れない")
    void rejectsMinGreaterThanMax() {
        // 操作と確認：下限が上限より 1 円大きいと例外
        assertThatThrownBy(() -> new PaymentAmountRange(Money.of(101), Money.of(100)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("下限と上限が同じ範囲は作れる")
    void allowsMinEqualToMax() {
        // 操作と確認：下限と上限が同じなら、その金額だけが範囲の中
        var range = new PaymentAmountRange(Money.of(100), Money.of(100));
        assertThat(range.contains(Money.of(100))).isTrue();
        assertThat(range.contains(Money.of(101))).isFalse();
    }
}
