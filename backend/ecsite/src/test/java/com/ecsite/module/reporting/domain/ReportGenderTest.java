package com.ecsite.module.reporting.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 集計の性別の区分の単体テスト（AC-277、AC-278）
 */
class ReportGenderTest {

    @ParameterizedTest(name = "注文時の性別 {0} は {1}")
    @CsvSource({
            "MALE, MALE",
            "FEMALE, FEMALE",
            "OTHER, OTHER",
            "UNDISCLOSED, UNKNOWN",
    })
    @DisplayName("AC-277、AC-278 注文時の性別は、回答しないを不明にまとめて集計の区分にする")
    void of(String gender, ReportGender expected) {
        assertThat(ReportGender.of(gender)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "記録がなければ不明")
    @CsvSource(value = "null", nullValues = "null")
    @DisplayName("AC-277、AC-278 注文時の性別の記録がなければ不明にする")
    void ofNull(String gender) {
        assertThat(ReportGender.of(gender)).isEqualTo(ReportGender.UNKNOWN);
    }
}
