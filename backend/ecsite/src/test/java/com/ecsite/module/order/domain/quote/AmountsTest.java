package com.ecsite.module.order.domain.quote;

import com.ecsite.common.model.value.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 金額の内訳の単体テスト
 *
 * <p>コンストラクターが、総額が商品の合計と送料の和であることを検査することだけを確認する。
 */
class AmountsTest {

    // ============================== 定数 ==============================

    /**
     * 商品の合計
     */
    private static final Money ITEMS_TOTAL = Money.of(2200);

    /**
     * 送料
     */
    private static final Money SHIPPING_FEE = Money.of(1000);

    /**
     * 総額
     *
     * <p>商品の合計 2,200 円 + 送料 1,000 円。
     */
    private static final Money TOTAL = Money.of(3200);

    // ============================== テスト ==============================

    @ParameterizedTest(name = "和との差 {0} 円")
    @ValueSource(longs = {-1, 1})
    @DisplayName("商品の合計と送料の和と 1 円違う総額は拒否される")
    void rejectsTotalOffByOneYen(long difference) {
        // 操作と確認：和と 1 円違う総額は、IllegalArgumentException で拒否される
        assertThatThrownBy(() -> new Amounts(ITEMS_TOTAL, SHIPPING_FEE, Money.of(TOTAL.yen() + difference), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("商品の合計と送料の和に等しい総額は受け付ける")
    void acceptsSum() {
        // 操作：総額が商品の合計と送料の和の内訳を作る
        var amounts = new Amounts(ITEMS_TOTAL, SHIPPING_FEE, TOTAL, List.of());

        // 確認：総額をそのまま持つ
        assertThat(amounts.total()).isEqualTo(TOTAL);
    }

    @Test
    @DisplayName("全て 0 円の内訳は受け付ける")
    void acceptsZero() {
        // 操作：購入できる品目がないときの、全て 0 円の内訳を作る
        var amounts = new Amounts(Money.ZERO, Money.ZERO, Money.ZERO, List.of());

        // 確認：総額が 0 円
        assertThat(amounts.total()).isEqualTo(Money.ZERO);
    }
}
