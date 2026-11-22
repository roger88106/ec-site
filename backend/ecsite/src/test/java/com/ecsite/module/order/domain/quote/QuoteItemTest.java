package com.ecsite.module.order.domain.quote;

import com.ecsite.common.model.id.ProductId;
import com.ecsite.common.model.id.TaxCategoryId;
import com.ecsite.common.model.id.VariantId;
import com.ecsite.common.model.value.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 見積もりの品目の単体テスト
 *
 * <p>コンストラクターが、数量の下限を検査することだけを確認する。購入できるかの判定は {@code QuoteTest} が確認する。
 */
class QuoteItemTest {

    // ============================== 定数 ==============================

    /**
     * 税込単価
     */
    private static final Money UNIT_PRICE_INCL_TAX = Money.of(1100);

    // ============================== テスト ==============================

    @Test
    @DisplayName("数量 0 は拒否される")
    void rejectsZeroQuantity() {
        // 操作と確認：IllegalArgumentException で拒否される
        assertThatThrownBy(() -> new QuoteItem(variant(), 0, BigDecimal.TEN, UNIT_PRICE_INCL_TAX, Money.ZERO, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("数量 1 は受け付ける")
    void acceptsOne() {
        // 操作：数量 1 の品目を作る
        var item = new QuoteItem(variant(), 1, BigDecimal.TEN, UNIT_PRICE_INCL_TAX, UNIT_PRICE_INCL_TAX, null);

        // 確認：数量をそのまま持つ
        assertThat(item.quantity()).isEqualTo(1);
    }

    // ============================== 準備の補助 ==============================

    /**
     * 表示中で販売中のバリアントの情報を作る。
     *
     * @return バリアントの情報
     */
    private static VariantInfo variant() {
        return new VariantInfo(new VariantId(UUID.randomUUID()), new ProductId(UUID.randomUUID()), "P-1", "P-1-1", "商品", List.of(), Money.of(1000),
                new TaxCategoryId(UUID.randomUUID()), null, true, false);
    }
}
