package com.ecsite.module.tax.domain.taxcategory;

import com.ecsite.common.model.value.Money;
import com.ecsite.module.tax.domain.taxcategory.vo.RatePercent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 税込価格の計算の単体テスト
 *
 * <p>税込価格は、税抜価格 ×（100 ＋ 率）÷ 100 の 1 円未満を切り捨てた額。
 */
class TaxCalculatorTest {

    // ============================== テスト ==============================

    @ParameterizedTest(name = "{0} 円・{1}% → {2} 円")
    @CsvSource({
            "1800, 10, 1980",
            "1234, 8, 1332",
            "1000, 10, 1100",
    })
    @DisplayName("BR-1 税込価格は、税抜価格に（100 ＋ 率）÷ 100 を掛けた額になる")
    void multipliesByRate(long exclTax, String ratePercent, long expected) {
        // 操作：税込価格を計算する
        var inclTax = priceInclTax(exclTax, ratePercent);

        // 確認：計算どおりの額
        assertThat(inclTax).isEqualTo(Money.of(expected));
    }

    @ParameterizedTest(name = "{0} 円・{1}% → {2} 円")
    @CsvSource({
            "999, 10, 1098",
            "1, 8, 1",
            "13, 8, 14",
            "12, 8, 12",
    })
    @DisplayName("BR-1 1 円未満の端数は切り捨てる")
    void truncatesFraction(long exclTax, String ratePercent, long expected) {
        // 操作：端数の出る組で税込価格を計算する
        var inclTax = priceInclTax(exclTax, ratePercent);

        // 確認：端数を切り捨てた額
        assertThat(inclTax).isEqualTo(Money.of(expected));
    }

    @ParameterizedTest(name = "{0} 円・{1}% → {2} 円")
    @CsvSource({
            "1000, 8.25, 1082",
            "100, 99.99, 199",
    })
    @DisplayName("BR-1 小数第 2 位までの率でも、誤差なく計算する")
    void handlesFractionalRate(long exclTax, String ratePercent, long expected) {
        // 操作：小数を含む率で税込価格を計算する
        var inclTax = priceInclTax(exclTax, ratePercent);

        // 確認：十進数で計算した額
        assertThat(inclTax).isEqualTo(Money.of(expected));
    }

    @Test
    @DisplayName("BR-1 税抜価格が 0 円なら、税込価格も 0 円")
    void zeroPriceStaysZero() {
        // 操作：0 円の税込価格を計算する
        var inclTax = priceInclTax(0, "10");

        // 確認：0 円
        assertThat(inclTax).isEqualTo(Money.of(0));
    }

    @Test
    @DisplayName("BR-1 率が 0% なら、税込価格は税抜価格と同じ")
    void zeroRateKeepsPrice() {
        // 操作：0% で税込価格を計算する
        var inclTax = priceInclTax(1980, "0");

        // 確認：税抜価格のまま
        assertThat(inclTax).isEqualTo(Money.of(1980));
    }

    @Test
    @DisplayName("BR-1 大きな金額でも誤差が出ない")
    void handlesLargeAmount() {
        // 操作：12 桁の税抜価格で計算する
        var inclTax = priceInclTax(999_999_999_999L, "10");

        // 確認：1 円の誤差もない
        assertThat(inclTax).isEqualTo(Money.of(1_099_999_999_998L));
    }

    // ============================== 準備の補助 ==============================

    /**
     * 税込価格を計算する。
     *
     * @param exclTax     税抜価格（円）
     * @param ratePercent 率（%）
     * @return 税込価格
     */
    private static Money priceInclTax(long exclTax, String ratePercent) {
        return TaxCalculator.priceInclTax(Money.of(exclTax), RatePercent.of(new BigDecimal(ratePercent)));
    }
}
