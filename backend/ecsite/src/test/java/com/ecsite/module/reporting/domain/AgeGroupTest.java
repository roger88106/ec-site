package com.ecsite.module.reporting.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 年齢層の単体テスト（T0207、BR-27、AC-208）
 */
class AgeGroupTest {

    @ParameterizedTest(name = "{0} 歳は {1}")
    @CsvSource({
            "0, TEENS_OR_YOUNGER",
            "19, TEENS_OR_YOUNGER",
            "20, TWENTIES",
            "29, TWENTIES",
            "30, THIRTIES",
            "45, FORTIES",
            "59, FIFTIES",
            "60, SIXTIES_OR_OLDER",
            "120, SIXTIES_OR_OLDER",
    })
    @DisplayName("BR-27 10 代以下、20 代、30 代、40 代、50 代、60 代以上に分ける")
    void groupsByAge(int age, AgeGroup group) {
        assertThat(AgeGroup.of(age)).isEqualTo(group);
    }

    @Test
    @DisplayName("AC-208 年齢が分からなければ不明")
    void unknownAge() {
        assertThat(AgeGroup.of(null)).isEqualTo(AgeGroup.UNKNOWN);
    }

    @Test
    @DisplayName("BR-27 年齢層の並びは、若い順で、不明が最後")
    void order() {
        assertThat(AgeGroup.values()).containsExactly(AgeGroup.TEENS_OR_YOUNGER, AgeGroup.TWENTIES, AgeGroup.THIRTIES,
                AgeGroup.FORTIES, AgeGroup.FIFTIES, AgeGroup.SIXTIES_OR_OLDER, AgeGroup.UNKNOWN);
    }
}
