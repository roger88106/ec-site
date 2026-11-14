package com.ecsite.module.tax.domain.taxcategory;

import com.ecsite.common.model.value.Money;
import com.ecsite.module.tax.domain.taxcategory.TaxCalculator.ConsumptionTax;
import com.ecsite.module.tax.domain.taxcategory.TaxCalculator.Taxable;
import com.ecsite.module.tax.domain.taxcategory.vo.RatePercent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 税率ごとの消費税額の計算の単体テスト
 *
 * <p>税率ごとに税込金額を合計し、合計 × 率 ÷（100 ＋ 率）の 1 円未満を切り捨てる。
 */
class ConsumptionTaxCalculationTest {

    // ============================== テスト ==============================

    @Nested
    @DisplayName("消費税額の計算")
    class Calculation {

        @Test
        @DisplayName("BR-8 税率ごとに税込金額を合計し、合計から消費税額を計算する")
        void calculatesPerRate() {
            // 準備：10% の品目 1,980 円と 990 円、8% の品目 1,332 円、10% の送料 1,000 円
            var taxables = List.of(taxable("10", 1980), taxable("8", 1332), taxable("10", 990), taxable("10", 1000));

            // 操作：消費税額を計算する
            var taxes = TaxCalculator.consumptionTaxes(taxables);

            // 確認：10% は 3,970 円から 360 円、8% は 1,332 円から 98 円
            assertThat(taxes).containsExactly(
                    new ConsumptionTax(rate("10"), Money.of(3970), Money.of(360)),
                    new ConsumptionTax(rate("8"), Money.of(1332), Money.of(98)));
        }

        @Test
        @DisplayName("BR-8 品目ごとには端数処理せず、税率ごとの合計から 1 回だけ切り捨てる")
        void roundsOncePerRate() {
            // 準備：品目ごとに切り捨てると 9 円 × 3 で 27 円、合計からなら 327 × 10 / 110 = 29.7 で 29 円になる品目
            var taxables = List.of(taxable("10", 109), taxable("10", 109), taxable("10", 109));

            // 操作：消費税額を計算する
            var taxes = TaxCalculator.consumptionTaxes(taxables);

            // 確認：合計から切り捨てた 29 円
            assertThat(taxes).containsExactly(new ConsumptionTax(rate("10"), Money.of(327), Money.of(29)));
        }

        @Test
        @DisplayName("BR-8 小数第 2 位までの率でも誤差なく切り捨てる")
        void handlesFractionalRate() {
            // 操作：8.25% の 1,082 円の消費税額を計算する
            var taxes = TaxCalculator.consumptionTaxes(List.of(taxable("8.25", 1082)));

            // 確認：1082 × 8.25 / 108.25 = 82.46... を切り捨てた 82 円
            assertThat(taxes).containsExactly(new ConsumptionTax(rate("8.25"), Money.of(1082), Money.of(82)));
        }

        @Test
        @DisplayName("BR-8 対象がなければ空")
        void emptyWhenNoTaxable() {
            // 操作と確認：空の入力からは空の一覧
            assertThat(TaxCalculator.consumptionTaxes(List.of())).isEmpty();
        }
    }

    @Nested
    @DisplayName("税率のまとめ方と並び")
    class Grouping {

        @Test
        @DisplayName("BR-8 税率の高い順に並べる")
        void sortsByRateDescending() {
            // 準備：8%、10%、0% の順の入力
            var taxables = List.of(taxable("8", 108), taxable("10", 330), taxable("0", 500));

            // 操作：消費税額を計算する
            var taxes = TaxCalculator.consumptionTaxes(taxables);

            // 確認：10%、8%、0% の順。0% の消費税額は 0 円
            assertThat(taxes).containsExactly(
                    new ConsumptionTax(rate("10"), Money.of(330), Money.of(30)),
                    new ConsumptionTax(rate("8"), Money.of(108), Money.of(8)),
                    new ConsumptionTax(rate("0"), Money.of(500), Money.ZERO));
        }

        @Test
        @DisplayName("BR-8 書き方の違う同じ率は、1 つの税率にまとめる")
        void mergesSameRateWrittenDifferently() {
            // 準備：10.00% と 10% の入力
            var taxables = List.of(taxable("10.00", 110), taxable("10", 220));

            // 操作：消費税額を計算する
            var taxes = TaxCalculator.consumptionTaxes(taxables);

            // 確認：10% の 1 件に合計される
            assertThat(taxes).containsExactly(new ConsumptionTax(rate("10"), Money.of(330), Money.of(30)));
        }
    }

    // ============================== 準備の補助 ==============================

    /**
     * 課税の対象を作る。
     *
     * @param ratePercent   率（%）
     * @param amountInclTax 税込金額（円）
     * @return 課税の対象
     */
    private static Taxable taxable(String ratePercent, long amountInclTax) {
        return new Taxable(rate(ratePercent), Money.of(amountInclTax));
    }

    /**
     * 率を作る。
     *
     * @param value 率（%）
     * @return 率
     */
    private static RatePercent rate(String value) {
        return RatePercent.of(new BigDecimal(value));
    }
}
