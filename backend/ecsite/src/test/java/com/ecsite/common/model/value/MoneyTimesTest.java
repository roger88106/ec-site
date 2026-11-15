package com.ecsite.common.model.value;

import com.ecsite.common.error.CommonErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.ecsite.BusinessErrorAssertions.assertBusinessError;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 金額に数量を掛ける操作の単体テスト
 */
class MoneyTimesTest {

    // ============================== 定数 ==============================

    /**
     * 単価
     */
    private static final Money PRICE = Money.of(1_980);

    // ============================== テスト ==============================

    @Test
    @DisplayName("数量を掛けた金額を返す")
    void multipliesByQuantity() {
        // 操作と確認：1,980 円 × 3 = 5,940 円
        assertThat(PRICE.times(3)).isEqualTo(Money.of(5_940));
    }

    @Test
    @DisplayName("数量を掛けても、元の金額は変わらない")
    void keepsOriginal() {
        // 準備：1,980 円
        var price = Money.of(1_980);

        // 操作：3 を掛ける
        price.times(3);

        // 確認：元の金額は 1,980 円のまま
        assertThat(price).isEqualTo(Money.of(1_980));
    }

    @Test
    @DisplayName("数量が 0 なら 0 円")
    void multipliesByZero() {
        // 操作と確認：0 円
        assertThat(PRICE.times(0)).isEqualTo(Money.ZERO);
    }

    @Test
    @DisplayName("数量が 1 なら同じ金額")
    void multipliesByOne() {
        // 操作と確認：1,980 円
        assertThat(PRICE.times(1)).isEqualTo(PRICE);
    }

    @Test
    @DisplayName("桁あふれは例外になる")
    void rejectsOverflow() {
        // 準備：2 倍すると long の上限を超える金額
        var price = Money.of(Long.MAX_VALUE / 2 + 1);

        // 操作と確認：ArithmeticException
        assertThatThrownBy(() -> price.times(2)).isInstanceOf(ArithmeticException.class);
    }

    @Test
    @DisplayName("負の数量は、負の金額として拒否される")
    void rejectsNegativeQuantity() {
        // 操作と確認：業務のエラー INVALID_MONEY
        assertBusinessError(() -> Money.of(100).times(-1), CommonErrorCode.INVALID_MONEY);
    }
}
