package com.ecsite.module.tax.domain.taxcategory.vo;

import com.ecsite.module.tax.domain.TaxErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static com.ecsite.BusinessErrorAssertions.assertBusinessError;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 率の単体テスト
 */
class RatePercentTest {

    // ============================== テスト ==============================

    @Nested
    @DisplayName("率の範囲と桁")
    class Range {

        @ParameterizedTest(name = "「{0}」")
        @ValueSource(strings = {"0", "8", "10", "8.5", "12.34", "99.99"})
        @DisplayName("AC-7 0 以上 100 未満、小数第 2 位までの率を表せる")
        void acceptsValidRate(String value) {
            // 操作：率を作る
            var rate = RatePercent.of(new BigDecimal(value));

            // 確認：渡した値と等しい
            assertThat(rate.value()).isEqualByComparingTo(value);
        }

        @ParameterizedTest(name = "「{0}」")
        @ValueSource(strings = {"-0.01", "-1", "100", "100.00", "150"})
        @DisplayName("AC-7 0 未満、100 以上の率は拒否される")
        void rejectsOutOfRange(String value) {
            // 操作と確認：業務のエラー INVALID_RATE_PERCENT で拒否される
            assertBusinessError(() -> RatePercent.of(new BigDecimal(value)), TaxErrorCode.INVALID_RATE_PERCENT);
        }

        @Test
        @DisplayName("小数第 3 位以下を持つ率は拒否される")
        void rejectsMoreThanTwoDecimalPlaces() {
            // 操作と確認：業務のエラー INVALID_RATE_PERCENT で拒否される
            assertBusinessError(() -> RatePercent.of(new BigDecimal("8.125")), TaxErrorCode.INVALID_RATE_PERCENT);
        }

        @Test
        @DisplayName("末尾の 0 は小数の桁に数えない")
        void ignoresTrailingZeros() {
            // 操作：小数第 3 位が 0 の率を作る
            var rate = RatePercent.of(new BigDecimal("8.100"));

            // 確認：拒否されず、末尾の 0 を除いた値になる
            assertThat(rate.value()).isEqualByComparingTo("8.1");
        }
    }

    @Nested
    @DisplayName("等しさと値の形")
    class Equality {

        @Test
        @DisplayName("同じ値の率は、書き方（桁数）によらず等しい")
        void equalsBySameValue() {
            // 準備：桁数の違う、同じ値の率
            var rate = RatePercent.of(new BigDecimal("10"));
            var sameValue = RatePercent.of(new BigDecimal("10.00"));

            // 確認：等しく、ハッシュ値も同じ
            assertThat(rate).isEqualTo(sameValue).hasSameHashCodeAs(sameValue);
        }

        @Test
        @DisplayName("値の違う率は等しくない")
        void notEqualsByDifferentValue() {
            // 操作と確認：10% と 8% は等しくない
            assertThat(RatePercent.of(new BigDecimal("10"))).isNotEqualTo(RatePercent.of(new BigDecimal("8")));
        }

        @Test
        @DisplayName("値は指数表記にならない")
        void valueIsPlain() {
            // 操作と確認：10.00 は 1E+1 でなく 10、8.50 は 8.5 と書く
            assertThat(RatePercent.of(new BigDecimal("10.00")).value().toString()).isEqualTo("10");
            assertThat(RatePercent.of(new BigDecimal("8.50")).value().toString()).isEqualTo("8.5");
        }
    }
}
