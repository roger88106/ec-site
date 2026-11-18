package com.ecsite.module.shipping.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static com.ecsite.support.BusinessErrorAssertions.assertBusinessError;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 送り状番号の単体テスト
 *
 * <p>送り状番号の形の検証と、保存する値への整形だけを確認する。出荷の登録で拒否することは、出荷の API の統合テストが
 * 確認する。
 */
class TrackingNumberTest {

    // ============================== テスト ==============================

    @Nested
    @DisplayName("保存する値")
    class Normalization {

        @ParameterizedTest(name = "「{0}」")
        @CsvSource(delimiter = '|', value = {
                "123456789012|123456789012",
                "1234-5678-9012|123456789012",
                "  1234-5678-9012  |123456789012",
                "0|0",
        })
        @DisplayName("AC-173 送り状番号は、前後の空白とハイフンを除いて保存する")
        void normalizes(String input, String expected) {
            // 操作と確認：空白とハイフンを除いた値
            assertThat(TrackingNumber.of(input).value()).isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("長さ")
    class Length {

        @Test
        @DisplayName("AC-173 送り状番号は、ハイフンを含めて 30 文字まで受け付ける")
        void acceptsThirtyCharacters() {
            // 準備：ハイフンを含む 30 文字
            var input = "1234-5678-9012-3456-7890-1234-";

            // 確認（前提）：30 文字
            assertThat(input).hasSize(30);

            // 操作と確認：ハイフンを除いた値で受け付ける
            assertThat(TrackingNumber.of(input).value()).isEqualTo("123456789012345678901234");
        }

        @Test
        @DisplayName("AC-173 31 文字の送り状番号は拒否される")
        void rejectsThirtyOneCharacters() {
            // 操作と確認：業務のエラー TRACKING_NUMBER_INVALID で拒否される
            assertBusinessError(() -> TrackingNumber.of("1".repeat(31)), ShippingErrorCode.TRACKING_NUMBER_INVALID);
        }
    }

    @Nested
    @DisplayName("必須と使える文字")
    class Characters {

        @Test
        @DisplayName("AC-173 送り状番号がないと拒否される")
        void rejectsNull() {
            // 操作と確認：業務のエラー TRACKING_NUMBER_INVALID で拒否される
            assertBusinessError(() -> TrackingNumber.of(null), ShippingErrorCode.TRACKING_NUMBER_INVALID);
        }

        @ParameterizedTest(name = "「{0}」")
        @ValueSource(strings = {"", "   "})
        @DisplayName("AC-173 空白だけの送り状番号は拒否される")
        void rejectsBlank(String input) {
            // 操作と確認：業務のエラー TRACKING_NUMBER_INVALID で拒否される
            assertBusinessError(() -> TrackingNumber.of(input), ShippingErrorCode.TRACKING_NUMBER_INVALID);
        }

        @ParameterizedTest(name = "「{0}」")
        @ValueSource(strings = {"-", "---"})
        @DisplayName("AC-173 数字のない送り状番号は拒否される")
        void rejectsWithoutDigits(String input) {
            // 操作と確認：業務のエラー TRACKING_NUMBER_INVALID で拒否される
            assertBusinessError(() -> TrackingNumber.of(input), ShippingErrorCode.TRACKING_NUMBER_INVALID);
        }

        @ParameterizedTest(name = "「{0}」")
        @ValueSource(strings = {"ABC123", "1234 5678", "１２３４", "123_456", "123.456", "123-456\n789"})
        @DisplayName("AC-173 半角の数字とハイフンの他の文字を含む送り状番号は拒否される")
        void rejectsOtherCharacters(String input) {
            // 操作と確認：業務のエラー TRACKING_NUMBER_INVALID で拒否される
            assertBusinessError(() -> TrackingNumber.of(input), ShippingErrorCode.TRACKING_NUMBER_INVALID);
        }
    }

    @Nested
    @DisplayName("コンストラクターの検査")
    class Constructor {

        @ParameterizedTest(name = "「{0}」")
        @ValueSource(strings = {"", "-", "12-34", "ABC", "12 34", "１２３", "1234567890123456789012345678901"})
        @DisplayName("復元でも、数字だけでない値と 31 桁以上は、業務のエラー TRACKING_NUMBER_INVALID で拒否される")
        void rejectsInvalidForm(String value) {
            // 操作と確認：業務のエラー TRACKING_NUMBER_INVALID で拒否される
            assertBusinessError(() -> new TrackingNumber(value), ShippingErrorCode.TRACKING_NUMBER_INVALID);
        }

        @Test
        @DisplayName("数字だけの 30 桁は作れる")
        void acceptsThirtyDigits() {
            // 操作と確認：そのままの値を持つ
            assertThat(new TrackingNumber("1".repeat(30)).value()).isEqualTo("1".repeat(30));
        }
    }
}
