package com.ecsite.module.order.domain.quote;

import com.ecsite.common.model.value.Money;
import com.ecsite.module.order.domain.quote.port.TaxDirectory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 見積もりの単体テスト
 */
class QuoteTest {

    private static final BigDecimal STANDARD = new BigDecimal("10");

    private static final BigDecimal REDUCED = new BigDecimal("8");

    /**
     * 送料（1,000 円、10%）
     */
    private static final Shipping SHIPPING = new Shipping(Money.of(1000), STANDARD);

    @Test
    @DisplayName("BR-7 小計は税込単価 × 数量。商品の合計に送料を加えて総額になる")
    void calculatesAmounts() {
        var shirt = item(variant(1800, true, false), 2, 10, STANDARD);
        var tea = item(variant(1234, true, false), 1, 10, REDUCED);

        var quote = Quote.of(List.of(shirt, tea), false, SHIPPING, TAX);

        assertThat(shirt.unitPriceInclTax()).isEqualTo(Money.of(1980));
        assertThat(shirt.subtotal()).isEqualTo(Money.of(3960));
        assertThat(quote.amounts().itemsTotal()).isEqualTo(Money.of(3960 + 1332));
        assertThat(quote.amounts().shippingFee()).isEqualTo(Money.of(1000));
        assertThat(quote.amounts().total()).isEqualTo(Money.of(3960 + 1332 + 1000));
        assertThat(quote.orderable()).isTrue();
    }

    @Test
    @DisplayName("BR-8 税率ごとの消費税額は、送料を標準税率の対象に含めて計算する（spec の例）")
    void calculatesTaxesIncludingShipping() {
        var tShirt = item(variant(2700, true, false), 1, 10, STANDARD);
        var tea = item(variant(1234, true, false), 1, 10, REDUCED);

        var quote = Quote.of(List.of(tShirt, tea), false, SHIPPING, TAX);

        assertThat(quote.amounts().taxes()).containsExactly(
                new TaxAmount(STANDARD, Money.of(3970), Money.of(360)),
                new TaxAmount(REDUCED, Money.of(1332), Money.of(98)));
    }

    @Test
    @DisplayName("AC-80 購入できない品目には理由を付け、合計・消費税額に含めない")
    void excludesUnpurchasableItems() {
        var hidden = item(variant(1000, false, false), 1, 10, STANDARD);
        var suspended = item(variant(1000, true, true), 1, 10, STANDARD);
        var soldOut = item(variant(1000, true, false), 1, 0, STANDARD);
        var short_ = item(variant(1000, true, false), 3, 2, STANDARD);
        var ok = item(variant(1000, true, false), 2, 2, STANDARD);

        var quote = Quote.of(List.of(hidden, suspended, soldOut, short_, ok), false, SHIPPING, TAX);

        assertThat(quote.items()).extracting(QuoteItem::unpurchasableReason).containsExactly(
                UnpurchasableReason.NOT_SHOWN, UnpurchasableReason.SALES_SUSPENDED, UnpurchasableReason.OUT_OF_STOCK,
                UnpurchasableReason.INSUFFICIENT_STOCK, null);
        assertThat(quote.amounts().itemsTotal()).isEqualTo(Money.of(2200));
        assertThat(quote.amounts().total()).isEqualTo(Money.of(3200));
        assertThat(quote.amounts().taxes()).containsExactly(new TaxAmount(STANDARD, Money.of(3200), Money.of(290)));
        assertThat(quote.orderable()).isFalse();
    }

    @Test
    @DisplayName("AC-80 有効な税率のない商品は、表示されないものとして扱う")
    void treatsItemWithoutRateAsNotShown() {
        var noRate = item(variant(1000, true, false), 1, 10, null);

        assertThat(noRate.unpurchasableReason()).isEqualTo(UnpurchasableReason.NOT_SHOWN);
    }

    @Test
    @DisplayName("購入できない理由が在庫だけかを判定できる（在庫不足は 409、それ以外は 400 にするため）")
    void tellsWhetherOnlyStockIsShort() {
        var ok = item(variant(1000, true, false), 1, 10, STANDARD);
        var soldOut = item(variant(1000, true, false), 1, 0, STANDARD);
        var suspended = item(variant(1000, true, true), 1, 10, STANDARD);

        assertThat(Quote.of(List.of(ok, soldOut), false, SHIPPING, TAX).shortOfStockOnly()).isTrue();
        assertThat(Quote.of(List.of(soldOut, suspended), false, SHIPPING, TAX).shortOfStockOnly()).isFalse();
        assertThat(Quote.of(List.of(ok), false, SHIPPING, TAX).shortOfStockOnly()).isFalse();
    }

    @Test
    @DisplayName("BR-6 購入できる品目がなければ送料は 0 で、空のカートは注文できない")
    void noShippingWithoutPurchasableItems() {
        var empty = Quote.of(List.of(), false, SHIPPING, TAX);
        var allSoldOut = Quote.of(List.of(item(variant(1000, true, false), 1, 0, STANDARD)), false, SHIPPING, TAX);

        assertThat(empty.amounts().shippingFee()).isEqualTo(Money.ZERO);
        assertThat(empty.amounts().total()).isEqualTo(Money.ZERO);
        assertThat(empty.amounts().taxes()).isEmpty();
        assertThat(empty.orderable()).isFalse();
        assertThat(allSoldOut.amounts().shippingFee()).isEqualTo(Money.ZERO);
    }

    @Test
    @DisplayName("存在しなくなったバリアントがあれば、注文できない")
    void notOrderableWithMissingVariants() {
        var ok = item(variant(1000, true, false), 1, 10, STANDARD);

        assertThat(Quote.of(List.of(ok), true, SHIPPING, TAX).orderable()).isFalse();
    }

    // ============================== 補助 ==============================

    /**
     * 税込価格と消費税額を、税モジュールと同じ式で計算する税の腐敗防止層
     */
    private static final TaxDirectory TAX = new TaxDirectory() {

        @Override
        public Optional<BigDecimal> rateAt(UUID taxCategoryId, Instant instant) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Money priceInclTax(Money priceExclTax, BigDecimal ratePercent) {
            return Money.of(BigDecimal.valueOf(priceExclTax.yen()).multiply(BigDecimal.valueOf(100).add(ratePercent))
                    .divide(BigDecimal.valueOf(100), 0, RoundingMode.DOWN).longValueExact());
        }

        @Override
        public List<TaxAmount> consumptionTaxes(List<Taxable> taxables) {
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
     * 税抜価格・表示されるか・販売停止かを指定したバリアント
     */
    private static VariantInfo variant(long priceExclTax, boolean visible, boolean salesSuspended) {
        return new VariantInfo(UUID.randomUUID(), UUID.randomUUID(), "P-1", "P-1-1", "商品", List.of(),
                Money.of(priceExclTax), UUID.randomUUID(), null, visible, salesSuspended);
    }

    /**
     * 見積もりの品目。率がなければ税込単価もない
     */
    private static QuoteItem item(VariantInfo variant, int quantity, int sellable, BigDecimal ratePercent) {
        var priceInclTax = ratePercent == null ? null : TAX.priceInclTax(variant.priceExclTax(), ratePercent);
        return QuoteItem.of(variant, quantity, sellable, ratePercent, priceInclTax);
    }
}
