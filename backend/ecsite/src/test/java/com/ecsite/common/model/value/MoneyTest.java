package com.ecsite.common.model.value;

import com.ecsite.common.error.CommonErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static com.ecsite.BusinessErrorAssertions.assertBusinessError;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 金額の単体テスト
 */
class MoneyTest {

    // ============================== テスト ==============================

    @Nested
    @DisplayName("作成")
    class Creation {

        @Test
        @DisplayName("円単位の整数で表す")
        void holdsYenAsInteger() {
            // 操作：1,980 円を作る
            var money = Money.of(1_980);

            // 確認：円の値が 1,980
            assertThat(money.yen()).isEqualTo(1_980L);
        }

        @Test
        @DisplayName("0 円を表せる")
        void allowsZero() {
            // 操作と確認：0 円は 0 円の定数と等しい
            assertThat(Money.of(0)).isEqualTo(Money.ZERO);
        }

        @Test
        @DisplayName("負の金額は拒否される")
        void rejectsNegative() {
            // 操作と確認：業務のエラー INVALID_MONEY
            assertBusinessError(() -> Money.of(-1), CommonErrorCode.INVALID_MONEY);
        }
    }

    @Nested
    @DisplayName("加算")
    class Addition {

        @Test
        @DisplayName("加算した金額を返す")
        void adds() {
            // 操作と確認：1,800 円 + 180 円 = 1,980 円
            assertThat(Money.of(1_800).plus(Money.of(180))).isEqualTo(Money.of(1_980));
        }

        @Test
        @DisplayName("加算しても、元の金額は変わらない")
        void keepsOriginal() {
            // 準備：1,800 円
            var original = Money.of(1_800);

            // 操作：180 円を加算する
            original.plus(Money.of(180));

            // 確認：元の金額は 1,800 円のまま
            assertThat(original).isEqualTo(Money.of(1_800));
        }
    }

    @Nested
    @DisplayName("比較")
    class Equality {

        @Test
        @DisplayName("同じ金額は等しく、ハッシュ値も同じ")
        void equalsByValue() {
            // 操作と確認：100 円どうし
            assertThat(Money.of(100)).isEqualTo(Money.of(100)).hasSameHashCodeAs(Money.of(100));
        }

        @Test
        @DisplayName("違う金額は等しくない")
        void differsByValue() {
            // 操作と確認：100 円と 101 円
            assertThat(Money.of(100)).isNotEqualTo(Money.of(101));
        }
    }
}
