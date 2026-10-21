package com.ecsite.module.order.domain.order;

import com.ecsite.module.order.domain.order.vo.ShippingAddress;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static com.ecsite.BusinessErrorAssertions.assertBusinessError;
import static com.ecsite.module.order.domain.order.TestAreas.CHIYODA;
import static com.ecsite.module.order.domain.order.TestAreas.SAPPORO_CHUO;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * お届け先の単体テスト（T2307、AC-86。T016-02-02、AC-258）。<br />
 * 都道府県と市区町村がマスターデータにあるかは、注文の確定で地域の API が確認する（統合テスト）。
 */
class ShippingAddressTest {

    @Test
    @DisplayName("AC-258 お届け先は、都道府県と市区町村のコードと名前、市区町村より下の住所を持つ")
    void holdsCodesAndNames() {
        var address = ShippingAddress.of("山田 太郎", "100-0001", CHIYODA, "千代田 1-1", "03-1234-5678");

        assertThat(address.area().prefectureCode()).isEqualTo("13");
        assertThat(address.area().prefectureName()).isEqualTo("東京都");
        assertThat(address.area().municipalityCode()).isEqualTo("13101");
        assertThat(address.area().municipalityName()).isEqualTo("千代田区");
        assertThat(address.address()).isEqualTo("千代田 1-1");
    }

    @Test
    @DisplayName("AC-86 郵便番号と電話番号はハイフンを除いた数字で持つ")
    void normalizesPostalCodeAndPhone() {
        var address = ShippingAddress.of("山田 太郎", "100-0001", CHIYODA, "千代田 1-1", "03-1234-5678");

        assertThat(address.postalCode()).isEqualTo("1000001");
        assertThat(address.phone()).isEqualTo("0312345678");
        assertThat(ShippingAddress.of("山田 太郎", "0600001", SAPPORO_CHUO, "北一条西", "09012345678").phone())
                .isEqualTo("09012345678");
    }

    @ParameterizedTest
    @ValueSource(strings = {"100000", "10000011", "100-00a1", "1000-001", ""})
    @DisplayName("AC-86 郵便番号は数字 7 桁（ハイフンは 3 桁目の後だけ）")
    void rejectsInvalidPostalCode(String postalCode) {
        assertBusinessError(() -> ShippingAddress.of("山田 太郎", postalCode, CHIYODA, "千代田", "0312345678"),
                OrderErrorCode.INVALID_POSTAL_CODE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"031234567", "031234567890", "03-1234-567a", "+81312345678", ""})
    @DisplayName("AC-86 電話番号は数字 10〜11 桁（ハイフンは任意）")
    void rejectsInvalidPhone(String phone) {
        assertBusinessError(() -> ShippingAddress.of("山田 太郎", "1000001", CHIYODA, "千代田", phone),
                OrderErrorCode.INVALID_PHONE_NUMBER);
    }

    @Test
    @DisplayName("AC-86 氏名は空白不可で 50 文字まで、住所は空白不可で 200 文字まで")
    void validatesNameAndAddress() {
        assertBusinessError(() -> ShippingAddress.of(" ", "1000001", CHIYODA, "千代田", "0312345678"),
                OrderErrorCode.INVALID_RECIPIENT_NAME);
        assertBusinessError(() -> ShippingAddress.of("あ".repeat(51), "1000001", CHIYODA, "千代田", "0312345678"),
                OrderErrorCode.INVALID_RECIPIENT_NAME);
        assertBusinessError(() -> ShippingAddress.of("山田 太郎", "1000001", CHIYODA, "", "0312345678"),
                OrderErrorCode.INVALID_ADDRESS);
        assertBusinessError(() -> ShippingAddress.of("山田 太郎", "1000001", CHIYODA, "あ".repeat(201), "0312345678"),
                OrderErrorCode.INVALID_ADDRESS);
        assertThat(ShippingAddress.of("あ".repeat(50), "1000001", CHIYODA, "あ".repeat(200), "0312345678").name())
                .hasSize(50);
    }
}
