package com.ecsite.common.model.value;

import com.ecsite.common.error.CommonErrorCode;
import com.ecsite.common.error.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 金額に数量を掛ける操作の単体テスト（バックエンドの書き方 第 1.5 節）。<br />
 * 金額に数量を掛ける操作は、金額の基本の単体テストとは別のファイルに置く。
 */
class MoneyTimesTest {

    @Test
    @DisplayName("数量を掛けた金額を返す。元の金額は変わらない")
    void multipliesByQuantity() {
        var price = Money.of(1_980);

        assertThat(price.times(3)).isEqualTo(Money.of(5_940));
        assertThat(price).isEqualTo(Money.of(1_980));
    }

    @Test
    @DisplayName("数量が 0 なら 0 円、1 なら同じ金額")
    void multipliesByZeroAndOne() {
        var price = Money.of(1_980);

        assertThat(price.times(0)).isEqualTo(Money.ZERO);
        assertThat(price.times(1)).isEqualTo(price);
    }

    @Test
    @DisplayName("桁あふれは例外になる")
    void rejectsOverflow() {
        var price = Money.of(Long.MAX_VALUE / 2 + 1);

        assertThatThrownBy(() -> price.times(2)).isInstanceOf(ArithmeticException.class);
    }

    @Test
    @DisplayName("負の数量は、負の金額として拒否される")
    void rejectsNegativeQuantity() {
        assertThatThrownBy(() -> Money.of(100).times(-1))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getErrorDetail().errorCode()).isEqualTo(CommonErrorCode.INVALID_MONEY.getCode()));
    }
}
