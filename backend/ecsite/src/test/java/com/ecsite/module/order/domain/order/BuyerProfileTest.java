package com.ecsite.module.order.domain.order;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 注文時の年齢と性別の単体テスト（T0106、AC-212、213、222、BR-29）
 */
class BuyerProfileTest {

    /**
     * 2026-10-07 15:30 UTC は、日本時間では 2026-10-08 00:30
     */
    private static final Instant ORDERED_AT = Instant.parse("2026-10-07T15:30:00Z");

    @ParameterizedTest(name = "{0} 生まれは {1} 歳")
    @CsvSource({
            "1990-10-08, 36",
            "1990-10-09, 35",
            "1990-10-07, 36",
            "2026-10-08, 0",
    })
    @DisplayName("AC-212 年齢は、注文日（日本時間）の時点の満年齢。誕生日の当日に 1 つ上がる")
    void ageAtJapaneseOrderDate(String birthdate, int age) {
        assertThat(BuyerProfile.of(birthdate, null, ORDERED_AT).age()).isEqualTo(age);
    }

    @ParameterizedTest(name = "{0} に注文すると {1} 歳")
    @CsvSource({
            "2025-02-27T15:00:00Z, 24",
            "2025-02-28T15:00:00Z, 25",
            "2028-02-28T15:00:00Z, 28",
    })
    @DisplayName("AC-212 2 月 29 日生まれは、うるう年でない年は 3 月 1 日に年を取る")
    void leapDayBirthday(String orderedAt, int age) {
        // 2025-02-28T15:00Z は日本時間の 2025-03-01、2028-02-28T15:00Z は 2028-02-29
        assertThat(BuyerProfile.of("2000-02-29", null, Instant.parse(orderedAt)).age()).isEqualTo(age);
    }

    @ParameterizedTest(name = "\"{0}\"")
    @NullAndEmptySource
    @ValueSource(strings = {"1990/10/08", "19901008", "1990-02-30", "abc", " 1990-10-08"})
    @DisplayName("AC-212 生年月日がない・形が正しくないときは、年齢は不明")
    void unknownAgeForMissingOrMalformed(String birthdate) {
        assertThat(BuyerProfile.of(birthdate, null, ORDERED_AT).age()).isNull();
    }

    @Test
    @DisplayName("AC-213 注文日より後の生年月日は、年齢を不明にする")
    void unknownAgeForFutureBirthdate() {
        assertThat(BuyerProfile.of("2026-10-09", null, ORDERED_AT).age()).isNull();
    }

    @Test
    @DisplayName("AC-213 注文日の 120 年より前の生年月日は、年齢を不明にする。ちょうど 120 年前は 120 歳")
    void unknownAgeForTooOld() {
        assertThat(BuyerProfile.of("1906-10-08", null, ORDERED_AT).age()).isEqualTo(120);
        assertThat(BuyerProfile.of("1906-10-07", null, ORDERED_AT).age()).isNull();
    }

    @ParameterizedTest(name = "{0} は {1}")
    @CsvSource({
            "male, MALE",
            "female, FEMALE",
            "other, OTHER",
            "undisclosed, UNDISCLOSED",
    })
    @DisplayName("AC-222、BR-29 性別は 4 つの値をそのまま記録する（回答しないも記録する）")
    void genderFromClaim(String claim, Gender gender) {
        assertThat(BuyerProfile.of(null, claim, ORDERED_AT).gender()).isEqualTo(gender);
    }

    @ParameterizedTest(name = "\"{0}\"")
    @NullAndEmptySource
    @ValueSource(strings = {"MALE", "unknown", " male"})
    @DisplayName("AC-222 性別の値がない・一覧にない値は、不明")
    void unknownGender(String claim) {
        assertThat(BuyerProfile.of(null, claim, ORDERED_AT).gender()).isNull();
    }
}
