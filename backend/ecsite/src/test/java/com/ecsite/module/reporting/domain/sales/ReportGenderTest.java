package com.ecsite.module.reporting.domain.sales;

import com.ecsite.module.reporting.domain.snapshot.ReportBuyerGender;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 集計の性別の区分の単体テスト
 */
class ReportGenderTest {

    @ParameterizedTest(name = "注文時の性別 {0} は {1}")
    @CsvSource({
            "MALE, MALE",
            "FEMALE, FEMALE",
            "OTHER, OTHER",
            "UNDISCLOSED, UNKNOWN",
    })
    @DisplayName("AC-219、BR-29 注文時の性別は、回答しないを不明にまとめて集計の区分にする")
    void of(ReportBuyerGender gender, ReportGender expected) {
        assertThat(ReportGender.of(gender)).isEqualTo(expected);
    }

    @Test
    @DisplayName("AC-219、BR-29 注文時の性別の記録がなければ不明にする")
    void ofNull() {
        assertThat(ReportGender.of(null)).isEqualTo(ReportGender.UNKNOWN);
    }
}
