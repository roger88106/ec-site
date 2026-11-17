package com.ecsite.module.order.domain.order;

import com.ecsite.module.order.domain.order.vo.OrderNumber;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.random.RandomGenerator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 注文番号の単体テスト
 *
 * <p>注文番号の作り方（{@link OrderNumber#generate}）と形の検証だけを確認する。注文に番号を付けることは
 * {@code PlaceOrderPlacementApiTest} などの統合テストが確認する。
 */
class OrderNumberTest {

    // ============================== 定数 ==============================

    /**
     * 注文日時
     *
     * <p>日本時間の 2026-10-08 00:30（UTC では前の日）。
     */
    private static final Instant ORDERED_AT = Instant.parse("2026-10-07T15:30:00Z");

    // ============================== テスト ==============================

    @Nested
    @DisplayName("注文番号の作り方")
    class Generation {

        @Test
        @DisplayName("BR-10 注文番号は、注文日（日本時間）の 8 桁、ハイフン、ランダムな数字 6 桁")
        void joinsJapaneseDateAndRandomDigits() {
            // 操作：乱数が 482915 を返すときに、注文番号を作る
            var number = OrderNumber.generate(ORDERED_AT, fixed(482915));

            // 確認：日本時間の日付とランダムな数字
            assertThat(number.value()).isEqualTo("20261008-482915");
        }

        @Test
        @DisplayName("BR-10 ランダムな数字が 6 桁に満たないときは、先頭を 0 で埋める")
        void padsRandomDigitsWithZeros() {
            // 操作：乱数が 7 を返すときに、注文番号を作る
            var number = OrderNumber.generate(ORDERED_AT, fixed(7));

            // 確認：ランダムな部分が 6 桁
            assertThat(number.value()).isEqualTo("20261008-000007");
        }
    }

    @Nested
    @DisplayName("注文番号の形の検証")
    class FormatValidation {

        @ParameterizedTest(name = "「{0}」")
        @ValueSource(strings = {"2026100-482915", "20261008482915", "20261008-48291", "20261008-48291a"})
        @DisplayName("BR-10 形の違う注文番号は作れない")
        void rejectsInvalidFormat(String value) {
            // 操作と確認：プログラムの誤りとして拒否される
            assertThatThrownBy(() -> OrderNumber.of(value)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    // ============================== 準備の補助 ==============================

    /**
     * 決まった値を返す乱数を作る。
     *
     * @param value 返す値
     * @return 乱数
     */
    private static RandomGenerator fixed(int value) {
        return new RandomGenerator() {
            @Override
            public long nextLong() {
                return value;
            }

            @Override
            public int nextInt(int bound) {
                return value;
            }
        };
    }
}
