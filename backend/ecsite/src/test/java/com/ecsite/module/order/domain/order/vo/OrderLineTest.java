package com.ecsite.module.order.domain.order.vo;

import com.ecsite.common.model.value.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 注文明細の単体テスト
 *
 * <p>コンストラクターが、行番号と数量の下限と、小計が税込単価と数量の積であることを検査することだけを確認する。
 */
class OrderLineTest {

    // ============================== 定数 ==============================

    /**
     * 税抜単価
     */
    private static final Money UNIT_PRICE_EXCL_TAX = Money.of(1000);

    /**
     * 率（%）
     */
    private static final BigDecimal RATE_PERCENT = BigDecimal.TEN;

    /**
     * 税込単価
     */
    private static final Money UNIT_PRICE_INCL_TAX = Money.of(1100);

    /**
     * 小計を確認する明細の数量
     */
    private static final int QUANTITY = 3;

    /**
     * 小計を確認する明細の小計
     *
     * <p>税込単価 1,100 円 × 数量 3 個。
     */
    private static final Money SUBTOTAL = Money.of(3300);

    // ============================== テスト ==============================

    @Nested
    @DisplayName("行番号と数量")
    class LineNoAndQuantity {

        @Test
        @DisplayName("行番号 0 は拒否される")
        void rejectsZeroLineNo() {
            // 操作と確認：IllegalArgumentException で拒否される
            assertThatThrownBy(() -> line(0, 1, UNIT_PRICE_INCL_TAX)).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("数量 0 は拒否される")
        void rejectsZeroQuantity() {
            // 操作と確認：IllegalArgumentException で拒否される
            assertThatThrownBy(() -> line(1, 0, Money.ZERO)).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("行番号 1 と数量 1 は受け付ける")
        void acceptsOne() {
            // 操作：行番号 1、数量 1 の明細を作る
            var line = line(1, 1, UNIT_PRICE_INCL_TAX);

            // 確認：行番号と数量をそのまま持つ
            assertThat(line.lineNo()).isEqualTo(1);
            assertThat(line.quantity()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("小計")
    class Subtotal {

        @ParameterizedTest(name = "積との差 {0} 円")
        @ValueSource(longs = {-1, 1})
        @DisplayName("税込単価と数量の積と 1 円違う小計は拒否される")
        void rejectsSubtotalOffByOneYen(long difference) {
            // 操作と確認：積と 1 円違う小計は、IllegalArgumentException で拒否される
            assertThatThrownBy(() -> line(1, QUANTITY, Money.of(SUBTOTAL.yen() + difference)))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("税込単価と数量の積に等しい小計は受け付ける")
        void acceptsProduct() {
            // 操作：小計が税込単価と数量の積の明細を作る
            var line = line(1, QUANTITY, SUBTOTAL);

            // 確認：小計をそのまま持つ
            assertThat(line.subtotal()).isEqualTo(SUBTOTAL);
        }
    }

    // ============================== 準備の補助 ==============================

    /**
     * 税込単価が定数の明細を、コンストラクターで作る。
     *
     * @param lineNo   行番号
     * @param quantity 数量
     * @param subtotal 小計
     * @return 明細
     */
    private static OrderLine line(int lineNo, int quantity, Money subtotal) {
        return new OrderLine(lineNo, UUID.randomUUID(), "P-1", "P-1-1", "商品", List.of(), UNIT_PRICE_EXCL_TAX, RATE_PERCENT,
                UNIT_PRICE_INCL_TAX, quantity, subtotal);
    }
}
