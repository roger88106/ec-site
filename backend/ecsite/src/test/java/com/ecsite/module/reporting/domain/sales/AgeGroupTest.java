package com.ecsite.module.reporting.domain.sales;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 年齢層の単体テスト
 */
class AgeGroupTest {

    @ParameterizedTest(name = "{0} 歳は「{1}」")
    @CsvSource({
            "0, TEENS_OR_YOUNGER",
            "19, TEENS_OR_YOUNGER",
            "20, TWENTIES",
            "29, TWENTIES",
            "30, THIRTIES",
            "39, THIRTIES",
            "40, FORTIES",
            "45, FORTIES",
            "49, FORTIES",
            "50, FIFTIES",
            "59, FIFTIES",
            "60, SIXTIES_OR_OLDER",
            "120, SIXTIES_OR_OLDER",
    })
    @DisplayName("BR-27 年齢を 10 歳ごとの層に分け、19 歳以下と 60 歳以上はそれぞれ 1 つの層にまとめる")
    void groupsByAge(int age, AgeGroup group) {
        // 操作と確認：年齢の入る層になる
        assertThat(AgeGroup.of(age)).isEqualTo(group);
    }

    @Test
    @DisplayName("AC-208 年齢が分からなければ不明")
    void unknownAge() {
        // 操作と確認：年齢がなければ「不明」
        assertThat(AgeGroup.of(null)).isEqualTo(AgeGroup.UNKNOWN);
    }

    @Test
    @DisplayName("BR-27 全ての年齢層で、下限の年齢を渡すとその年齢層になる（宣言の順に頼らない）")
    void minAgeReturnsItself() {
        // 操作と確認：不明を除く全ての要素で、下限の年齢からその要素自身が返る
        for (var group : AgeGroup.values()) {
            if (group == AgeGroup.UNKNOWN) {
                continue;
            }
            assertThat(AgeGroup.of(group.minAge())).isEqualTo(group);
        }
    }

    @Test
    @DisplayName("BR-27 年齢層の並びは、若い順で、不明が最後")
    void order() {
        // 確認：列挙の並びが、若い順の後に「不明」
        assertThat(AgeGroup.values()).containsExactly(AgeGroup.TEENS_OR_YOUNGER, AgeGroup.TWENTIES, AgeGroup.THIRTIES,
                AgeGroup.FORTIES, AgeGroup.FIFTIES, AgeGroup.SIXTIES_OR_OLDER, AgeGroup.UNKNOWN);
    }
}
