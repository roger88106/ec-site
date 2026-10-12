package com.ecsite.module.order.domain.order;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static com.ecsite.BusinessErrorAssertions.assertBusinessError;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * お届け先の単体テスト（T2307、AC-86）
 */
class ShippingAddressTest {

    @Test
    @DisplayName("AC-86 郵便番号と電話番号はハイフンを除いた数字で持つ")
    void normalizesPostalCodeAndPhone() {
        var address = ShippingAddress.of("山田 太郎", "100-0001", "東京都", "千代田区千代田 1-1", "03-1234-5678");

        assertThat(address.postalCode()).isEqualTo("1000001");
        assertThat(address.phone()).isEqualTo("0312345678");
        assertThat(ShippingAddress.of("山田 太郎", "1000001", "北海道", "札幌市", "09012345678").phone())
                .isEqualTo("09012345678");
    }

    @ParameterizedTest
    @ValueSource(strings = {"100000", "10000011", "100-00a1", "1000-001", ""})
    @DisplayName("AC-86 郵便番号は数字 7 桁（ハイフンは 3 桁目の後だけ）")
    void rejectsInvalidPostalCode(String postalCode) {
        assertBusinessError(() -> ShippingAddress.of("山田 太郎", postalCode, "東京都", "千代田区", "0312345678"),
                OrderErrorCode.INVALID_POSTAL_CODE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"東京", "大阪", "トウキョウ都", ""})
    @DisplayName("AC-86 都道府県は 47 都道府県のいずれか")
    void rejectsUnknownPrefecture(String prefecture) {
        assertBusinessError(() -> ShippingAddress.of("山田 太郎", "1000001", prefecture, "千代田区", "0312345678"),
                OrderErrorCode.INVALID_PREFECTURE);
    }

    @Test
    @DisplayName("AC-86 47 都道府県を受け付ける")
    void acceptsAllPrefectures() {
        assertThat(ShippingAddress.PREFECTURES).hasSize(47).contains("北海道", "東京都", "京都府", "大阪府", "沖縄県");
    }

    @ParameterizedTest
    @ValueSource(strings = {"031234567", "031234567890", "03-1234-567a", "+81312345678", ""})
    @DisplayName("AC-86 電話番号は数字 10〜11 桁（ハイフンは任意）")
    void rejectsInvalidPhone(String phone) {
        assertBusinessError(() -> ShippingAddress.of("山田 太郎", "1000001", "東京都", "千代田区", phone),
                OrderErrorCode.INVALID_PHONE_NUMBER);
    }

    @Test
    @DisplayName("AC-86 氏名は空白不可で 50 文字まで、住所は空白不可で 200 文字まで")
    void validatesNameAndAddress() {
        assertBusinessError(() -> ShippingAddress.of(" ", "1000001", "東京都", "千代田区", "0312345678"),
                OrderErrorCode.INVALID_RECIPIENT_NAME);
        assertBusinessError(() -> ShippingAddress.of("あ".repeat(51), "1000001", "東京都", "千代田区", "0312345678"),
                OrderErrorCode.INVALID_RECIPIENT_NAME);
        assertBusinessError(() -> ShippingAddress.of("山田 太郎", "1000001", "東京都", "", "0312345678"),
                OrderErrorCode.INVALID_ADDRESS);
        assertBusinessError(() -> ShippingAddress.of("山田 太郎", "1000001", "東京都", "あ".repeat(201), "0312345678"),
                OrderErrorCode.INVALID_ADDRESS);
        assertThat(ShippingAddress.of("あ".repeat(50), "1000001", "東京都", "あ".repeat(200), "0312345678").name())
                .hasSize(50);
    }
}
