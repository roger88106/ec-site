package com.ecsite.module.order.domain.order;

import com.ecsite.module.order.domain.OrderErrorCode;
import com.ecsite.module.order.domain.order.vo.ShippingAddress;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static com.ecsite.BusinessErrorAssertions.assertBusinessError;
import static com.ecsite.module.order.domain.order.TestAreas.CHIYODA;
import static com.ecsite.module.order.domain.order.TestAreas.SAPPORO_CHUO;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * お届け先の単体テスト
 *
 * <p>お届け先の項目の形の検証と、持ち方だけを確認する。都道府県と市区町村がマスターデータにあるかは、注文の確定で
 * 地域の API が確認する（統合テスト）。
 */
class ShippingAddressTest {

    // ============================== 定数 ==============================

    /**
     * 氏名
     */
    private static final String NAME = "山田 太郎";

    /**
     * ハイフンを含む郵便番号
     */
    private static final String POSTAL_CODE_WITH_HYPHEN = "100-0001";

    /**
     * ハイフンを除いた郵便番号
     */
    private static final String POSTAL_CODE_DIGITS = "1000001";

    /**
     * 市区町村より下の住所
     */
    private static final String ADDRESS_LINE = "千代田 1-1";

    /**
     * ハイフンを含む電話番号（10 桁）
     */
    private static final String PHONE_WITH_HYPHENS = "03-1234-5678";

    /**
     * ハイフンを除いた電話番号（10 桁）
     */
    private static final String PHONE_DIGITS = "0312345678";

    /**
     * ハイフンのない電話番号（11 桁）
     */
    private static final String MOBILE_PHONE_DIGITS = "09012345678";

    // ============================== テスト ==============================

    @Nested
    @DisplayName("お届け先の持ち方")
    class Holding {

        @Test
        @DisplayName("AC-258 お届け先は、都道府県と市区町村のコードと名前、市区町村より下の住所を持つ")
        void holdsCodesAndNames() {
            // 操作：東京都千代田区のお届け先を作る
            var address = ShippingAddress.of(NAME, POSTAL_CODE_WITH_HYPHEN, CHIYODA, ADDRESS_LINE, PHONE_WITH_HYPHENS);

            // 確認：都道府県と市区町村のコードと名前、住所
            assertChiyodaArea(address);
        }

        @Test
        @DisplayName("AC-86 郵便番号は、ハイフンを除いた数字で持つ")
        void stripsHyphenFromPostalCode() {
            // 操作：ハイフンを含む郵便番号でお届け先を作る
            var address = ShippingAddress.of(NAME, POSTAL_CODE_WITH_HYPHEN, CHIYODA, ADDRESS_LINE, PHONE_WITH_HYPHENS);

            // 確認：数字だけ
            assertThat(address.postalCode()).isEqualTo(POSTAL_CODE_DIGITS);
        }

        @Test
        @DisplayName("AC-86 電話番号は、ハイフンを除いた数字で持つ")
        void stripsHyphensFromPhone() {
            // 操作：ハイフンを含む電話番号でお届け先を作る
            var address = ShippingAddress.of(NAME, POSTAL_CODE_WITH_HYPHEN, CHIYODA, ADDRESS_LINE, PHONE_WITH_HYPHENS);

            // 確認：数字だけ
            assertThat(address.phone()).isEqualTo(PHONE_DIGITS);
        }

        @Test
        @DisplayName("AC-86 ハイフンのない 11 桁の電話番号は、そのまま持つ")
        void keepsElevenDigitPhone() {
            // 操作：ハイフンのない 11 桁の電話番号でお届け先を作る
            var address = ShippingAddress.of(NAME, "0600001", SAPPORO_CHUO, "北一条西", MOBILE_PHONE_DIGITS);

            // 確認：入力のまま
            assertThat(address.phone()).isEqualTo(MOBILE_PHONE_DIGITS);
        }
    }

    @Nested
    @DisplayName("郵便番号と電話番号の検証")
    class NumberValidation {

        @ParameterizedTest(name = "「{0}」")
        @ValueSource(strings = {"100000", "10000011", "100-00a1", "1000-001", ""})
        @DisplayName("AC-86 数字 7 桁（ハイフンは 3 桁目の後だけ）でない郵便番号は拒否される")
        void rejectsInvalidPostalCode(String postalCode) {
            // 操作と確認：業務のエラー INVALID_POSTAL_CODE で拒否される
            assertBusinessError(() -> ShippingAddress.of(NAME, postalCode, CHIYODA, ADDRESS_LINE, PHONE_DIGITS),
                    OrderErrorCode.INVALID_POSTAL_CODE);
        }

        @ParameterizedTest(name = "「{0}」")
        @ValueSource(strings = {"031234567", "031234567890", "03-1234-567a", "+81312345678", ""})
        @DisplayName("AC-86 数字 10〜11 桁（ハイフンは任意）でない電話番号は拒否される")
        void rejectsInvalidPhone(String phone) {
            // 操作と確認：業務のエラー INVALID_PHONE_NUMBER で拒否される
            assertBusinessError(() -> ShippingAddress.of(NAME, POSTAL_CODE_DIGITS, CHIYODA, ADDRESS_LINE, phone),
                    OrderErrorCode.INVALID_PHONE_NUMBER);
        }
    }

    @Nested
    @DisplayName("氏名と住所の検証")
    class TextValidation {

        @ParameterizedTest(name = "「{0}」")
        @ValueSource(strings = {"", " "})
        @DisplayName("AC-86 空白の氏名は拒否される")
        void rejectsBlankName(String name) {
            // 操作と確認：業務のエラー INVALID_RECIPIENT_NAME で拒否される
            assertBusinessError(() -> ShippingAddress.of(name, POSTAL_CODE_DIGITS, CHIYODA, ADDRESS_LINE, PHONE_DIGITS),
                    OrderErrorCode.INVALID_RECIPIENT_NAME);
        }

        @Test
        @DisplayName("AC-86 51 文字の氏名は拒否される")
        void rejectsTooLongName() {
            // 操作と確認：業務のエラー INVALID_RECIPIENT_NAME で拒否される
            assertBusinessError(() -> ShippingAddress.of("あ".repeat(51), POSTAL_CODE_DIGITS, CHIYODA, ADDRESS_LINE, PHONE_DIGITS),
                    OrderErrorCode.INVALID_RECIPIENT_NAME);
        }

        @ParameterizedTest(name = "「{0}」")
        @ValueSource(strings = {"", " "})
        @DisplayName("AC-86 空白の住所は拒否される")
        void rejectsBlankAddress(String addressLine) {
            // 操作と確認：業務のエラー INVALID_ADDRESS で拒否される
            assertBusinessError(() -> ShippingAddress.of(NAME, POSTAL_CODE_DIGITS, CHIYODA, addressLine, PHONE_DIGITS),
                    OrderErrorCode.INVALID_ADDRESS);
        }

        @Test
        @DisplayName("AC-86 201 文字の住所は拒否される")
        void rejectsTooLongAddress() {
            // 操作と確認：業務のエラー INVALID_ADDRESS で拒否される
            assertBusinessError(() -> ShippingAddress.of(NAME, POSTAL_CODE_DIGITS, CHIYODA, "あ".repeat(201), PHONE_DIGITS),
                    OrderErrorCode.INVALID_ADDRESS);
        }

        @Test
        @DisplayName("AC-86 50 文字の氏名と 200 文字の住所は受け付ける")
        void acceptsLongestNameAndAddress() {
            // 操作：上限の長さの氏名と住所でお届け先を作る
            var address = ShippingAddress.of("あ".repeat(50), POSTAL_CODE_DIGITS, CHIYODA, "あ".repeat(200), PHONE_DIGITS);

            // 確認：切り詰めずに持つ
            assertThat(address.name()).hasSize(50);
            assertThat(address.address()).hasSize(200);
        }
    }

    // ============================== 確認の補助 ==============================

    /**
     * お届け先が、東京都千代田区と定数の住所を持つことを確認する。
     *
     * <p>確認すること：
     * <ul>
     *   <li>都道府県のコードが 13</li>
     *   <li>都道府県の名前が東京都</li>
     *   <li>市区町村のコードが 13101</li>
     *   <li>市区町村の名前が千代田区</li>
     *   <li>市区町村より下の住所が定数のまま</li>
     * </ul>
     *
     * @param address 確認するお届け先
     */
    private static void assertChiyodaArea(ShippingAddress address) {
        assertThat(address.area().prefectureCode()).isEqualTo("13");
        assertThat(address.area().prefectureName()).isEqualTo("東京都");
        assertThat(address.area().municipalityCode()).isEqualTo("13101");
        assertThat(address.area().municipalityName()).isEqualTo("千代田区");
        assertThat(address.address()).isEqualTo(ADDRESS_LINE);
    }
}
