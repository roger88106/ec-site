package com.ecsite.module.order.domain.quote;

import com.ecsite.common.model.value.Money;
import com.ecsite.module.order.domain.quote.port.TaxDirectory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 見積もりの単体テスト
 *
 * <p>品目と送料から金額の内訳を求めることと、購入できない品目の扱いと、注文できるかの判定だけを確認する。
 * 税込価格と消費税額の式は税モジュールのテスト、カートと注文手続きの画面に返す金額は API の統合テストが確認する。
 */
class QuoteTest {

    // ============================== 定数 ==============================

    /**
     * 標準税率（%）
     */
    private static final BigDecimal STANDARD = new BigDecimal("10");

    /**
     * 軽減税率（%）
     */
    private static final BigDecimal REDUCED = new BigDecimal("8");

    /**
     * 送料（1,000 円、標準税率）
     */
    private static final Shipping SHIPPING = new Shipping(Money.of(1000), STANDARD);

    /**
     * 税込価格と消費税額を、税モジュールと同じ式で計算する税の腐敗防止層
     */
    private static final TaxDirectory TAX = new TaxDirectory() {

        @Override
        public Optional<BigDecimal> rateAt(UUID taxCategoryId, Instant instant) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Map<UUID, BigDecimal> ratesAt(Collection<UUID> taxCategoryIds, Instant instant) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Money priceInclTax(Money priceExclTax, BigDecimal ratePercent) {
            return Money.of(BigDecimal.valueOf(priceExclTax.yen()).multiply(BigDecimal.valueOf(100).add(ratePercent))
                    .divide(BigDecimal.valueOf(100), 0, RoundingMode.DOWN).longValueExact());
        }

        @Override
        public List<TaxAmount> consumptionTaxes(List<Taxable> taxables) {
            // 税率ごとに税込金額を合計し、高い税率から並べる
            var totals = new TreeMap<BigDecimal, Long>(Comparator.reverseOrder());
            taxables.forEach(taxable -> totals.merge(taxable.ratePercent(), taxable.amountInclTax().yen(), Long::sum));
            return totals.entrySet().stream()
                    .map(entry -> new TaxAmount(entry.getKey(), Money.of(entry.getValue()),
                            Money.of(BigDecimal.valueOf(entry.getValue()).multiply(entry.getKey())
                                    .divide(BigDecimal.valueOf(100).add(entry.getKey()), 0, RoundingMode.DOWN).longValueExact())))
                    .toList();
        }
    };

    /**
     * シャツの税抜単価（税込 1,980 円）
     */
    private static final long SHIRT_PRICE_EXCL_TAX = 1800;

    /**
     * お茶の税抜単価（軽減税率で税込 1,332 円）
     */
    private static final long TEA_PRICE_EXCL_TAX = 1234;

    // ============================== テスト ==============================

    @Nested
    @DisplayName("金額の内訳")
    class AmountBreakdown {

        @Test
        @DisplayName("BR-7 品目の小計は、税込単価 × 数量")
        void subtotalIsUnitPriceTimesQuantity() {
            // 操作：シャツ 2 枚の品目を作る
            var shirt = item(variant(SHIRT_PRICE_EXCL_TAX, true, false), 2, 10, STANDARD);

            // 確認（前提）：税込単価が 1,980 円
            assertThat(shirt.unitPriceInclTax()).isEqualTo(Money.of(1980));

            // 確認：小計が 2 枚分
            assertThat(shirt.subtotal()).isEqualTo(Money.of(3960));
        }

        @Test
        @DisplayName("BR-7 商品の合計は、品目の小計の合計")
        void itemsTotalIsSumOfSubtotals() {
            // 操作：シャツ 2 枚とお茶 1 個の見積もりを作る
            var quote = shirtsAndTea();

            // 確認：3,960 円と 1,332 円の合計
            assertThat(quote.amounts().itemsTotal()).isEqualTo(Money.of(3960 + 1332));
        }

        @Test
        @DisplayName("BR-6 購入できる品目があれば、設定の送料がかかる")
        void chargesShippingFee() {
            // 操作：シャツ 2 枚とお茶 1 個の見積もりを作る
            var quote = shirtsAndTea();

            // 確認：送料が 1,000 円
            assertThat(quote.amounts().shippingFee()).isEqualTo(Money.of(1000));
        }

        @Test
        @DisplayName("BR-7 総額は、商品の合計に送料を加えた額")
        void totalIncludesShippingFee() {
            // 操作：シャツ 2 枚とお茶 1 個の見積もりを作る
            var quote = shirtsAndTea();

            // 確認：商品の合計と送料の和
            assertThat(quote.amounts().total()).isEqualTo(Money.of(3960 + 1332 + 1000));
        }

        @Test
        @DisplayName("BR-8 消費税額は税率ごとに求め、送料は標準税率の対象に含める")
        void calculatesTaxesIncludingShipping() {
            // 準備：機能仕様の例の、標準税率の T シャツと軽減税率のお茶
            var tShirt = item(variant(2700, true, false), 1, 10, STANDARD);
            var tea = item(variant(TEA_PRICE_EXCL_TAX, true, false), 1, 10, REDUCED);

            // 操作：見積もりを作る
            var quote = Quote.of(List.of(tShirt, tea), false, SHIPPING, TAX);

            // 確認：標準税率は T シャツと送料、軽減税率はお茶の消費税額
            assertThat(quote.amounts().taxes()).containsExactly(
                    new TaxAmount(STANDARD, Money.of(3970), Money.of(360)),
                    new TaxAmount(REDUCED, Money.of(1332), Money.of(98)));
        }
    }

    @Nested
    @DisplayName("送料の消費税")
    class ShippingTax {

        @Test
        @DisplayName("DC-484-3 送料があれば、送料の消費税は、送料だけを課税対象として求めた額")
        void calculatesFromShippingFeeOnly() {
            // 操作：シャツ 2 枚とお茶 1 個と、送料 1,000 円（標準税率）の見積もりを作る
            var quote = shirtsAndTea();

            // 確認：品目を含めず、送料 1,000 円だけの消費税額（1,000 × 10 / 110 の切り捨て）
            assertThat(quote.shippingTax()).isEqualTo(Money.of(90));
        }

        @Test
        @DisplayName("DC-484-3 設定の送料が 0 なら、購入できる品目があっても、送料の消費税は 0")
        void zeroWhenShippingFeeIsZero() {
            // 準備：購入できる品目と、0 円の送料
            var ok = item(variant(1000, true, false), 1, 10, STANDARD);
            var free = new Shipping(Money.ZERO, STANDARD);

            // 操作：見積もりを作る
            var quote = Quote.of(List.of(ok), false, free, TAX);

            // 確認：送料の消費税が 0
            assertThat(quote.shippingTax()).isEqualTo(Money.ZERO);
        }

        @Test
        @DisplayName("DC-484-3 購入できる品目がなく送料をかけないなら、送料の消費税は 0")
        void zeroWithoutPurchasableItems() {
            // 操作：在庫切れの品目だけの見積もりを作る
            var allSoldOut = Quote.of(List.of(item(variant(1000, true, false), 1, 0, STANDARD)), false, SHIPPING, TAX);

            // 確認：送料の消費税が 0
            assertThat(allSoldOut.shippingTax()).isEqualTo(Money.ZERO);
        }
    }

    @Nested
    @DisplayName("購入できない品目の理由")
    class UnpurchasableReasons {

        @Test
        @DisplayName("AC-80 表示されない商品の品目は、表示されないことを理由に購入できない")
        void notShown() {
            // 操作と確認：理由が表示されない
            assertThat(item(variant(1000, false, false), 1, 10, STANDARD).unpurchasableReason())
                    .isEqualTo(UnpurchasableReason.NOT_SHOWN);
        }

        @Test
        @DisplayName("AC-80 有効な税率のない商品の品目は、表示されないものとして扱う")
        void treatsItemWithoutRateAsNotShown() {
            // 操作と確認：理由が表示されない
            assertThat(item(variant(1000, true, false), 1, 10, null).unpurchasableReason())
                    .isEqualTo(UnpurchasableReason.NOT_SHOWN);
        }

        @Test
        @DisplayName("AC-80 販売停止のバリアントの品目は、販売停止を理由に購入できない")
        void salesSuspended() {
            // 操作と確認：理由が販売停止
            assertThat(item(variant(1000, true, true), 1, 10, STANDARD).unpurchasableReason())
                    .isEqualTo(UnpurchasableReason.SALES_SUSPENDED);
        }

        @Test
        @DisplayName("AC-80 販売可能数がない品目は、在庫切れを理由に購入できない")
        void outOfStock() {
            // 操作と確認：理由が在庫切れ
            assertThat(item(variant(1000, true, false), 1, 0, STANDARD).unpurchasableReason())
                    .isEqualTo(UnpurchasableReason.OUT_OF_STOCK);
        }

        @Test
        @DisplayName("AC-80 販売可能数がカートの数量より少ない品目は、在庫不足を理由に購入できない")
        void insufficientStock() {
            // 操作と確認：3 個に対して 2 個しかなく、理由が在庫不足
            assertThat(item(variant(1000, true, false), 3, 2, STANDARD).unpurchasableReason())
                    .isEqualTo(UnpurchasableReason.INSUFFICIENT_STOCK);
        }

        @Test
        @DisplayName("AC-80 見積もりは、品目の並びのまま、品目ごとに購入できない理由を持つ")
        void keepsReasonsInItemOrder() {
            // 操作：購入できない 4 品目と購入できる 1 品目の見積もりを作る
            var quote = withUnpurchasableItems();

            // 確認：品目の並びのとおりの理由で、購入できる品目は理由がない
            assertThat(quote.items()).extracting(QuoteItem::unpurchasableReason).containsExactly(
                    UnpurchasableReason.NOT_SHOWN, UnpurchasableReason.SALES_SUSPENDED, UnpurchasableReason.OUT_OF_STOCK,
                    UnpurchasableReason.INSUFFICIENT_STOCK, null);
        }
    }

    @Nested
    @DisplayName("購入できない品目の金額")
    class UnpurchasableAmounts {

        @Test
        @DisplayName("AC-80 購入できない品目は、商品の合計と総額に含めない")
        void excludesFromTotals() {
            // 操作：購入できない 4 品目と、1,100 円 2 個の購入できる品目の見積もりを作る
            var quote = withUnpurchasableItems();

            // 確認：購入できる品目と送料だけ
            assertThat(quote.amounts().itemsTotal()).isEqualTo(Money.of(2200));
            assertThat(quote.amounts().total()).isEqualTo(Money.of(3200));
        }

        @Test
        @DisplayName("AC-80 購入できない品目は、消費税額に含めない")
        void excludesFromTaxes() {
            // 操作：購入できない 4 品目と、1,100 円 2 個の購入できる品目の見積もりを作る
            var quote = withUnpurchasableItems();

            // 確認：購入できる品目と送料だけの消費税額
            assertThat(quote.amounts().taxes()).containsExactly(new TaxAmount(STANDARD, Money.of(3200), Money.of(290)));
        }

        @Test
        @DisplayName("BR-6 購入できる品目がなければ、送料をかけない")
        void noShippingWithoutPurchasableItems() {
            // 操作：在庫切れの品目だけの見積もりを作る
            var allSoldOut = Quote.of(List.of(item(variant(1000, true, false), 1, 0, STANDARD)), false, SHIPPING, TAX);

            // 確認：送料が 0
            assertThat(allSoldOut.amounts().shippingFee()).isEqualTo(Money.ZERO);
        }

        @Test
        @DisplayName("BR-6 空の見積もりは、送料も総額も 0 で、消費税額がない")
        void emptyQuoteHasNoAmounts() {
            // 操作：品目のない見積もりを作る
            var empty = Quote.of(List.of(), false, SHIPPING, TAX);

            // 確認：金額が全て 0
            assertThat(empty.amounts().shippingFee()).isEqualTo(Money.ZERO);
            assertThat(empty.amounts().total()).isEqualTo(Money.ZERO);
            assertThat(empty.amounts().taxes()).isEmpty();
        }
    }

    @Nested
    @DisplayName("注文できるかの判定")
    class Orderable {

        @Test
        @DisplayName("AC-88 購入できる品目だけの見積もりは、注文できる")
        void orderableWhenAllPurchasable() {
            // 操作と確認：シャツ 2 枚とお茶 1 個の見積もりは注文できる
            assertThat(shirtsAndTea().orderable()).isTrue();
        }

        @Test
        @DisplayName("AC-88 購入できない品目を含む見積もりは、注文できない")
        void notOrderableWithUnpurchasableItem() {
            // 操作と確認：注文できない
            assertThat(withUnpurchasableItems().orderable()).isFalse();
        }

        @Test
        @DisplayName("AC-88 空の見積もりは、注文できない")
        void notOrderableWhenEmpty() {
            // 操作と確認：注文できない
            assertThat(Quote.of(List.of(), false, SHIPPING, TAX).orderable()).isFalse();
        }

        @Test
        @DisplayName("存在しなくなったバリアントがあれば、注文できない")
        void notOrderableWithMissingVariants() {
            // 準備：購入できる品目
            var ok = item(variant(1000, true, false), 1, 10, STANDARD);

            // 操作と確認：存在しなくなったバリアントがあるときは注文できない
            assertThat(Quote.of(List.of(ok), true, SHIPPING, TAX).orderable()).isFalse();
        }
    }

    @Nested
    @DisplayName("在庫だけが足りないかの判定（在庫不足は 409、それ以外は 400 にするため）")
    class ShortOfStockOnly {

        @Test
        @DisplayName("購入できない理由が在庫切れだけなら、在庫だけが足りない")
        void trueWhenOnlyStockIsShort() {
            // 準備：購入できる品目と在庫切れの品目
            var ok = item(variant(1000, true, false), 1, 10, STANDARD);
            var soldOut = item(variant(1000, true, false), 1, 0, STANDARD);

            // 操作と確認：在庫だけが足りない
            assertThat(Quote.of(List.of(ok, soldOut), false, SHIPPING, TAX).shortOfStockOnly()).isTrue();
        }

        @Test
        @DisplayName("購入できない理由が在庫不足だけなら、在庫だけが足りない")
        void trueWhenOnlyInsufficient() {
            // 準備：購入できる品目と、販売可能数 1 に 2 個の在庫不足の品目
            var ok = item(variant(1000, true, false), 1, 10, STANDARD);
            var insufficient = item(variant(1000, true, false), 2, 1, STANDARD);

            // 確認（前提）：在庫不足の品目の理由は在庫不足
            assertThat(insufficient.unpurchasableReason()).isEqualTo(UnpurchasableReason.INSUFFICIENT_STOCK);

            // 操作と確認：在庫だけが足りない
            assertThat(Quote.of(List.of(ok, insufficient), false, SHIPPING, TAX).shortOfStockOnly()).isTrue();
        }

        @Test
        @DisplayName("表示されない品目があれば、在庫だけが足りないとはしない")
        void falseWithNotShown() {
            // 準備：在庫切れの品目と表示されない品目
            var soldOut = item(variant(1000, true, false), 1, 0, STANDARD);
            var hidden = item(variant(1000, false, false), 1, 10, STANDARD);

            // 操作と確認：在庫だけが足りないとはしない
            assertThat(Quote.of(List.of(soldOut, hidden), false, SHIPPING, TAX).shortOfStockOnly()).isFalse();
        }

        @Test
        @DisplayName("在庫以外の理由で購入できない品目があれば、在庫だけが足りないとはしない")
        void falseWithOtherReason() {
            // 準備：在庫切れの品目と販売停止の品目
            var soldOut = item(variant(1000, true, false), 1, 0, STANDARD);
            var suspended = item(variant(1000, true, true), 1, 10, STANDARD);

            // 操作と確認：在庫だけが足りないとはしない
            assertThat(Quote.of(List.of(soldOut, suspended), false, SHIPPING, TAX).shortOfStockOnly()).isFalse();
        }

        @Test
        @DisplayName("全ての品目が購入できれば、在庫だけが足りないとはしない")
        void falseWhenAllPurchasable() {
            // 準備：購入できる品目
            var ok = item(variant(1000, true, false), 1, 10, STANDARD);

            // 操作と確認：在庫だけが足りないとはしない
            assertThat(Quote.of(List.of(ok), false, SHIPPING, TAX).shortOfStockOnly()).isFalse();
        }
    }

    // ============================== 準備の補助 ==============================

    /**
     * シャツ 2 枚（標準税率）とお茶 1 個（軽減税率）の、購入できる見積もりを作る。
     *
     * @return 見積もり
     */
    private static Quote shirtsAndTea() {
        var shirt = item(variant(SHIRT_PRICE_EXCL_TAX, true, false), 2, 10, STANDARD);
        var tea = item(variant(TEA_PRICE_EXCL_TAX, true, false), 1, 10, REDUCED);
        return Quote.of(List.of(shirt, tea), false, SHIPPING, TAX);
    }

    /**
     * 購入できない 4 品目と、購入できる 1 品目の見積もりを作る。
     *
     * <p>品目の並びは、表示されない、販売停止、在庫切れ、在庫不足、購入できる（税抜 1,000 円 × 2 個）。
     *
     * @return 見積もり
     */
    private static Quote withUnpurchasableItems() {
        var hidden = item(variant(1000, false, false), 1, 10, STANDARD);
        var suspended = item(variant(1000, true, true), 1, 10, STANDARD);
        var soldOut = item(variant(1000, true, false), 1, 0, STANDARD);
        var insufficient = item(variant(1000, true, false), 3, 2, STANDARD);
        var ok = item(variant(1000, true, false), 2, 2, STANDARD);
        return Quote.of(List.of(hidden, suspended, soldOut, insufficient, ok), false, SHIPPING, TAX);
    }

    /**
     * 税抜価格・表示されるか・販売停止かを指定したバリアントを作る。
     *
     * @param priceExclTax   税抜価格
     * @param visible        店舗に表示されるか
     * @param salesSuspended 販売停止か
     * @return バリアント
     */
    private static VariantInfo variant(long priceExclTax, boolean visible, boolean salesSuspended) {
        return new VariantInfo(UUID.randomUUID(), UUID.randomUUID(), "P-1", "P-1-1", "商品", List.of(),
                Money.of(priceExclTax), UUID.randomUUID(), null, visible, salesSuspended);
    }

    /**
     * 見積もりの品目を作る。
     *
     * <p>税率がなければ、税込単価もない。
     *
     * @param variant     バリアント
     * @param quantity    カートの数量
     * @param sellable    販売可能数
     * @param ratePercent 税率（%）。有効な税率がなければ null
     * @return 品目
     */
    private static QuoteItem item(VariantInfo variant, int quantity, int sellable, BigDecimal ratePercent) {
        var priceInclTax = ratePercent == null ? null : TAX.priceInclTax(variant.priceExclTax(), ratePercent);
        return QuoteItem.of(variant, quantity, sellable, ratePercent, priceInclTax);
    }
}
