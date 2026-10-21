package com.ecsite.module.shipping.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static com.ecsite.BusinessErrorAssertions.assertBusinessError;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 送り状番号の単体テスト（T9203、AC-173）
 */
class TrackingNumberTest {

    @ParameterizedTest(name = "「{0}」は「{1}」として保存する")
    @CsvSource(delimiter = '|', value = {
            "123456789012|123456789012",
            "1234-5678-9012|123456789012",
            "  1234-5678-9012  |123456789012",
            "0|0",
    })
    @DisplayName("AC-173 送り状番号は、前後の空白とハイフンを除いて保存する")
    void normalizes(String input, String expected) {
        assertThat(TrackingNumber.of(input).value()).isEqualTo(expected);
    }

    @Test
    @DisplayName("AC-173 送り状番号は 30 文字（ハイフンを含む）まで受け付ける")
    void acceptsThirtyCharacters() {
        var input = "1234-5678-9012-3456-7890-1234-";

        assertThat(input).hasSize(30);
        assertThat(TrackingNumber.of(input).value()).isEqualTo("123456789012345678901234");
    }

    @Test
    @DisplayName("AC-173 送り状番号が 31 文字になると拒否される")
    void rejectsThirtyOneCharacters() {
        assertBusinessError(() -> TrackingNumber.of("1".repeat(31)), ShippingErrorCode.TRACKING_NUMBER_INVALID);
    }

    @ParameterizedTest(name = "「{0}」は拒否される")
    @ValueSource(strings = {"", "   ", "-", "---", "ABC123", "1234 5678", "１２３４", "123_456", "123.456", "123-456\n789"})
    @DisplayName("AC-173 送り状番号は、必須で、半角の数字とハイフンだけ。数字がなければ拒否される")
    void rejectsInvalidForms(String input) {
        assertBusinessError(() -> TrackingNumber.of(input), ShippingErrorCode.TRACKING_NUMBER_INVALID);
    }

    @Test
    @DisplayName("AC-173 送り状番号がないと拒否される")
    void rejectsNull() {
        assertBusinessError(() -> TrackingNumber.of(null), ShippingErrorCode.TRACKING_NUMBER_INVALID);
    }
}
