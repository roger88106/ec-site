package com.ecsite.module.tax.domain.taxcategory;

import com.ecsite.common.model.Money;
import com.ecsite.module.tax.domain.taxcategory.vo.RatePercent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 税込価格の計算の単体テスト（T108、BR-1）
 */
class TaxCalculatorTest {

    @ParameterizedTest(name = "{0} 円・{1}% → {2} 円")
    @CsvSource({
            // spec の例
            "1800, 10, 1980",
            "1234, 8, 1332",
            // 割り切れる
            "1000, 10, 1100",
            // 1 円未満の切り捨て（端数の境界）
            "999, 10, 1098",
            "1, 8, 1",
            "13, 8, 14",
            "12, 8, 12",
            // 小数第 2 位までの率
            "1000, 8.25, 1082",
            "100, 99.99, 199",
            // 0 円、0%
            "0, 10, 0",
            "1980, 0, 1980",
            // 大きな金額でも誤差が出ない
            "999999999999, 10, 1099999999998",
    })
    @DisplayName("BR-1 税込価格は 税抜価格 ×（1 ＋ 税率）の 1 円未満を切り捨てる")
    void calculatesPriceInclTax(long exclTax, String ratePercent, long expected) {
        var inclTax = TaxCalculator.priceInclTax(Money.of(exclTax), RatePercent.of(new BigDecimal(ratePercent)));

        assertThat(inclTax).isEqualTo(Money.of(expected));
    }
}
