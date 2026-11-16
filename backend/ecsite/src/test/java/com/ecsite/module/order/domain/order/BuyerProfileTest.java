package com.ecsite.module.order.domain.order;

import com.ecsite.module.order.domain.order.vo.BuyerProfile;
import com.ecsite.module.order.domain.order.vo.Gender;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 注文時の年齢と性別の単体テスト
 *
 * <p>token の生年月日と性別から、注文時の年齢と性別を求めることだけを確認する。成立した注文に記録することは
 * {@code PlaceOrderBuyerProfileApiTest}、token に載せることは {@code RealmSettingsContractTest} が確認する。
 */
class BuyerProfileTest {

    // ============================== 定数 ==============================

    /**
     * 注文日時
     *
     * <p>日本時間の 2026-10-08 00:30（UTC では前の日）。
     */
    private static final Instant ORDERED_AT = Instant.parse("2026-10-07T15:30:00Z");

    // ============================== テスト ==============================

    @Nested
    @DisplayName("年齢の数え方")
    class Age {

        @ParameterizedTest(name = "「{0}」")
        @CsvSource({
                "1990-10-09, 35",
                "1990-10-07, 36",
                "2026-10-08, 0",
        })
        @DisplayName("AC-212 年齢は、注文日（日本時間）の時点の満年齢")
        void isFullYearsAtJapaneseOrderDate(String birthdate, int age) {
            // 操作：日本時間の 2026-10-08 の注文で求める
            var profile = BuyerProfile.of(birthdate, null, ORDERED_AT);

            // 確認：日本時間の注文日の時点の満年齢
            assertThat(profile.age()).isEqualTo(age);
        }

        @Test
        @DisplayName("AC-212 誕生日の当日（日本時間）に、年齢が 1 つ上がる")
        void increasesOnBirthday() {
            // 操作：日本時間では誕生日の当日、UTC では前の日の注文で求める
            var profile = BuyerProfile.of("1990-10-08", null, ORDERED_AT);

            // 確認：誕生日を迎えた年齢
            assertThat(profile.age()).isEqualTo(36);
        }

        @ParameterizedTest(name = "「{0}」")
        @CsvSource({
                "2025-02-27T15:00:00Z, 24",
                "2025-02-28T15:00:00Z, 25",
                "2028-02-28T15:00:00Z, 28",
        })
        @DisplayName("AC-212 2 月 29 日生まれは、うるう年でない年は 3 月 1 日に年を取る")
        void leapDayBirthday(String orderedAt, int age) {
            // 操作：2000-02-29 生まれで求める（2025-02-28T15:00Z は日本時間の 2025-03-01、2028-02-28T15:00Z は 2028-02-29）
            var profile = BuyerProfile.of("2000-02-29", null, Instant.parse(orderedAt));

            // 確認：日本時間の注文日の時点の満年齢
            assertThat(profile.age()).isEqualTo(age);
        }
    }

    @Nested
    @DisplayName("年齢を不明にする生年月日")
    class UnknownAge {

        @ParameterizedTest(name = "「{0}」")
        @NullAndEmptySource
        @ValueSource(strings = {"1990/10/08", "19901008", "1990-02-30", "abc", " 1990-10-08"})
        @DisplayName("AC-212 生年月日がない・形が正しくないときは、年齢は不明")
        void unknownForMissingOrMalformed(String birthdate) {
            // 操作と確認：年齢がない
            assertThat(BuyerProfile.of(birthdate, null, ORDERED_AT).age()).isNull();
        }

        @Test
        @DisplayName("AC-213 注文日より後の生年月日は、年齢を不明にする")
        void unknownForFutureBirthdate() {
            // 操作と確認：注文日の翌日の生年月日では、年齢がない
            assertThat(BuyerProfile.of("2026-10-09", null, ORDERED_AT).age()).isNull();
        }

        @Test
        @DisplayName("AC-213 注文日と同じ日の生年月日は、0 歳として記録する")
        void keepsBirthdateOnOrderDate() {
            // 操作と確認：注文日（日本時間の 2026-10-08）の生年月日では、0 歳
            assertThat(BuyerProfile.of("2026-10-08", null, ORDERED_AT).age()).isZero();
        }

        @Test
        @DisplayName("AC-213 注文日の 120 年より前の生年月日は、年齢を不明にする")
        void unknownForTooOld() {
            // 操作と確認：120 年と 1 日前の生年月日では、年齢がない
            assertThat(BuyerProfile.of("1906-10-07", null, ORDERED_AT).age()).isNull();
        }

        @Test
        @DisplayName("AC-213 注文日のちょうど 120 年前の生年月日は、120 歳として記録する")
        void keepsExactly120Years() {
            // 操作と確認：120 年前の同じ日の生年月日では、120 歳
            assertThat(BuyerProfile.of("1906-10-08", null, ORDERED_AT).age()).isEqualTo(120);
        }
    }

    @Nested
    @DisplayName("性別")
    class GenderClaim {

        @ParameterizedTest(name = "「{0}」")
        @CsvSource({
                "male, MALE",
                "female, FEMALE",
                "other, OTHER",
                "undisclosed, UNDISCLOSED",
        })
        @DisplayName("AC-222、BR-29 性別は、回答しないを含む 4 つの値をそのまま記録する")
        void recordsClaim(String claim, Gender gender) {
            // 操作と確認：token の値に対応する性別
            assertThat(BuyerProfile.of(null, claim, ORDERED_AT).gender()).isEqualTo(gender);
        }

        @ParameterizedTest(name = "「{0}」")
        @NullAndEmptySource
        @ValueSource(strings = {"MALE", "unknown", " male"})
        @DisplayName("AC-222 性別の値がない・一覧にない値は、不明")
        void unknownForMissingOrOtherValue(String claim) {
            // 操作と確認：性別がない
            assertThat(BuyerProfile.of(null, claim, ORDERED_AT).gender()).isNull();
        }
    }
}
