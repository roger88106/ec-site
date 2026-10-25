package com.ecsite.module.tax.domain.taxcategory;

import com.ecsite.common.model.value.Money;
import com.ecsite.module.tax.domain.taxcategory.TaxCalculator.ConsumptionTax;
import com.ecsite.module.tax.domain.taxcategory.TaxCalculator.Taxable;
import com.ecsite.module.tax.domain.taxcategory.vo.RatePercent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 税率ごとの消費税額の計算の単体テスト
 */
class ConsumptionTaxCalculationTest {

    @Test
    @DisplayName("BR-8 spec の例：10% の対象 2,970 円＋送料 1,000 円は 360 円、8% の対象 1,332 円は 98 円")
    void specExample() {
        var taxes = TaxCalculator.consumptionTaxes(List.of(
                taxable("10", 1980), taxable("8", 1332), taxable("10", 990), taxable("10", 1000)));

        assertThat(taxes).containsExactly(
                new ConsumptionTax(rate("10"), Money.of(3970), Money.of(360)),
                new ConsumptionTax(rate("8"), Money.of(1332), Money.of(98)));
    }

    @Test
    @DisplayName("BR-8 品目ごとには端数処理せず、税率ごとの合計から 1 回だけ切り捨てる")
    void roundsOncePerRate() {
        // 品目ごとに切り捨てると 109 × 10 / 110 = 9.9 → 9 が 3 つで 27 円。合計からなら 327 × 10 / 110 = 29.7 → 29 円
        var taxes = TaxCalculator.consumptionTaxes(List.of(taxable("10", 109), taxable("10", 109), taxable("10", 109)));

        assertThat(taxes).containsExactly(new ConsumptionTax(rate("10"), Money.of(327), Money.of(29)));
    }

    @Test
    @DisplayName("BR-8 税率の高い順に並べる。書き方の違う同じ率（10 と 10.00）は 1 つにまとめる")
    void groupsByRateInDescendingOrder() {
        var taxes = TaxCalculator.consumptionTaxes(List.of(
                taxable("8", 108), taxable("10.00", 110), taxable("0", 500), taxable("10", 220)));

        assertThat(taxes).containsExactly(
                new ConsumptionTax(rate("10"), Money.of(330), Money.of(30)),
                new ConsumptionTax(rate("8"), Money.of(108), Money.of(8)),
                new ConsumptionTax(rate("0"), Money.of(500), Money.ZERO));
    }

    @Test
    @DisplayName("BR-8 小数第 2 位までの率でも誤差なく切り捨てる")
    void handlesFractionalRate() {
        // 1082 × 8.25 / 108.25 = 82.46... → 82 円
        var taxes = TaxCalculator.consumptionTaxes(List.of(taxable("8.25", 1082)));

        assertThat(taxes).containsExactly(new ConsumptionTax(rate("8.25"), Money.of(1082), Money.of(82)));
    }

    @Test
    @DisplayName("BR-8 対象がなければ空")
    void emptyWhenNoTaxable() {
        assertThat(TaxCalculator.consumptionTaxes(List.of())).isEmpty();
    }

    private static Taxable taxable(String ratePercent, long amountInclTax) {
        return new Taxable(rate(ratePercent), Money.of(amountInclTax));
    }

    private static RatePercent rate(String value) {
        return RatePercent.of(new BigDecimal(value));
    }
}
