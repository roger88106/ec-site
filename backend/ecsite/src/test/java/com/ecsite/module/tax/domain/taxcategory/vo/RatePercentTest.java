package com.ecsite.module.tax.domain.taxcategory.vo;

import com.ecsite.module.tax.domain.taxcategory.TaxErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static com.ecsite.BusinessErrorAssertions.assertBusinessError;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 率の単体テスト（T102）
 */
class RatePercentTest {

    @ParameterizedTest
    @ValueSource(strings = {"0", "8", "10", "8.5", "12.34", "99.99"})
    @DisplayName("AC-7 0 以上 100 未満、小数第 2 位までの率を表せる")
    void acceptsValidRate(String value) {
        var rate = RatePercent.of(new BigDecimal(value));

        assertThat(rate.value()).isEqualByComparingTo(value);
    }

    @ParameterizedTest
    @ValueSource(strings = {"-0.01", "-1", "100", "100.00", "150"})
    @DisplayName("AC-7 0 未満、100 以上の率は拒否される")
    void rejectsOutOfRange(String value) {
        assertBusinessError(() -> RatePercent.of(new BigDecimal(value)), TaxErrorCode.INVALID_RATE_PERCENT);
    }

    @Test
    @DisplayName("小数第 3 位以下を持つ率は拒否される")
    void rejectsMoreThanTwoDecimalPlaces() {
        assertBusinessError(() -> RatePercent.of(new BigDecimal("8.125")), TaxErrorCode.INVALID_RATE_PERCENT);
    }

    @Test
    @DisplayName("末尾の 0 は小数の桁に数えない")
    void ignoresTrailingZeros() {
        assertThat(RatePercent.of(new BigDecimal("8.100")).value()).isEqualByComparingTo("8.1");
    }

    @Test
    @DisplayName("同じ値の率は、書き方（桁数）によらず等しい")
    void equalsByValue() {
        assertThat(RatePercent.of(new BigDecimal("10")))
                .isEqualTo(RatePercent.of(new BigDecimal("10.00")))
                .hasSameHashCodeAs(RatePercent.of(new BigDecimal("10.00")));
        assertThat(RatePercent.of(new BigDecimal("10"))).isNotEqualTo(RatePercent.of(new BigDecimal("8")));
    }

    @Test
    @DisplayName("値は指数表記にならない（10 は 10、1E+1 ではない）")
    void valueIsPlain() {
        assertThat(RatePercent.of(new BigDecimal("10.00")).value().toString()).isEqualTo("10");
        assertThat(RatePercent.of(new BigDecimal("8.50")).value().toString()).isEqualTo("8.5");
    }
}
