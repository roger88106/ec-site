package com.ecsite.module.order.presentation;

import com.ecsite.support.config.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 注文のお届け先の検証の受け入れテスト
 *
 * <p>お届け先の形の検証と、都道府県・市区町村のコードの地域のマスターデータとの照合を、注文の API で確認する。
 * 郵便番号・電話番号・氏名・住所の個々の規則は {@code ShippingAddressTest} が確認する。
 *
 * <p>準備は {@link PlaceOrderFixture}（T シャツ 2 枚のカート）。テストごとにロールバックする。
 */
@IntegrationTest
@Transactional
class PlaceOrderAddressApiTest {

    // ============================== 定数 ==============================

    /**
     * 正しい郵便番号
     */
    private static final String POSTAL_CODE = "100-0001";

    /**
     * 東京都のコード
     */
    private static final String TOKYO = "13";

    /**
     * 千代田区のコード
     */
    private static final String CHIYODA = "13101";

    // ============================== Bean ==============================

    @Autowired
    ApplicationContext applicationContext;

    /**
     * 共有の準備
     */
    private PlaceOrderFixture fixture;

    /**
     * T シャツ 2 枚の注文手続きの ID
     */
    private UUID checkoutId;

    @BeforeEach
    void setUp() throws Exception {
        fixture = PlaceOrderFixture.prepare(applicationContext);
        checkoutId = fixture.orders.startCheckout(fixture.cartId);
    }

    // ============================== テスト ==============================

    @Nested
    @DisplayName("入力の形")
    class Format {

        @Test
        @DisplayName("AC-86 郵便番号の形が違えば 400")
        void rejectsMalformedPostalCode() throws Exception {
            // 操作と確認：数字 4 桁の郵便番号は 400
            fixture.placeWith(checkoutId, body("1000", TOKYO, CHIYODA))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("AC-86 都道府県のコードの形でなければ、入力の検証で 400 になり、都道府県の欄を示す")
        void rejectsMalformedPrefectureCode() throws Exception {
            // 操作と確認：都道府県の名前を送ると、入力の検証のエラーで、都道府県のコードの欄を示す
            fixture.placeWith(checkoutId, body(POSTAL_CODE, "東京都", CHIYODA))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"))
                    .andExpect(jsonPath("$.errors[0].field").value("shippingAddress.prefectureCode"));
        }

        @Test
        @DisplayName("AC-86 市区町村のコードの形でなければ、入力の検証で 400 になり、市区町村の欄を示す")
        void rejectsMalformedMunicipalityCode() throws Exception {
            // 操作と確認：3 桁のコードを送ると、入力の検証のエラーで、市区町村のコードの欄を示す
            fixture.placeWith(checkoutId, body(POSTAL_CODE, TOKYO, "131"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"))
                    .andExpect(jsonPath("$.errors[0].field").value("shippingAddress.municipalityCode"));
        }

        @Test
        @DisplayName("AC-265 郵便番号の形の誤りは、入力の検証で見つかっても、業務のエラーと同じ文を欄のエラーに返す")
        void returnsBusinessMessageForMalformedPostalCode() throws Exception {
            // 操作と確認：数字 4 桁の郵便番号は、郵便番号の欄に業務のエラーの文
            fixture.placeWith(checkoutId, body("1000", TOKYO, CHIYODA))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"))
                    .andExpect(jsonPath("$.errors[0].field").value("shippingAddress.postalCode"))
                    .andExpect(jsonPath("$.errors[0].message").value("郵便番号は数字 7 桁で入力してください。"));
        }

        @Test
        @DisplayName("AC-265 電話番号の形の誤りは、入力の検証で見つかっても、業務のエラーと同じ文を欄のエラーに返す")
        void returnsBusinessMessageForMalformedPhone() throws Exception {
            // 操作と確認：数字 5 桁の電話番号は、電話番号の欄に業務のエラーの文
            fixture.placeWith(checkoutId, body(POSTAL_CODE, TOKYO, CHIYODA, "03-12"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"))
                    .andExpect(jsonPath("$.errors[0].field").value("shippingAddress.phone"))
                    .andExpect(jsonPath("$.errors[0].message").value("電話番号は数字 10〜11 桁で入力してください。"));
        }
    }

    @Nested
    @DisplayName("地域のマスターデータとの照合")
    class Region {

        @Test
        @DisplayName("AC-251 マスターデータにない都道府県は、都道府県の誤りを示す 400")
        void rejectsUnknownPrefecture() throws Exception {
            // 操作と確認：ない都道府県のコードは 400 で、エラーコードが INVALID_PREFECTURE
            fixture.placeWith(checkoutId, body(POSTAL_CODE, "48", CHIYODA))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("INVALID_PREFECTURE"));
        }

        @Test
        @DisplayName("AC-251 マスターデータにない市区町村は、市区町村の誤りを示す 400")
        void rejectsUnknownMunicipality() throws Exception {
            // 操作と確認：マスターデータにない市区町村のコードは 400 で、エラーコードが INVALID_MUNICIPALITY
            fixture.placeWith(checkoutId, body(POSTAL_CODE, TOKYO, "13999"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("INVALID_MUNICIPALITY"));
        }

        @Test
        @DisplayName("AC-251 選んだ都道府県に属さない市区町村は、市区町村の誤りを示す 400")
        void rejectsMunicipalityOfOtherPrefecture() throws Exception {
            // 操作と確認：東京都に北海道の市区町村を組み合わせると 400 で、エラーコードが INVALID_MUNICIPALITY
            fixture.placeWith(checkoutId, body(POSTAL_CODE, TOKYO, "01101"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("INVALID_MUNICIPALITY"));
        }

        @Test
        @DisplayName("AC-251 地域の誤りで拒否したときは、注文を作らず、在庫数が変わらない")
        void writesNothingWhenRegionRejected() throws Exception {
            // 操作：マスターデータにない市区町村で注文して拒否される
            fixture.placeWith(checkoutId, body(POSTAL_CODE, TOKYO, "13999")).andExpect(status().isBadRequest());

            // 確認：注文がなく、T シャツの在庫数が準備の時点のまま
            fixture.expectNotPlaced(checkoutId);
            fixture.expectQuantity(PlaceOrderFixture.SHIRT_STOCK);
        }
    }

    @Nested
    @DisplayName("空白だけの氏名と住所")
    class Blank {

        @Test
        @DisplayName("AC-86 空白だけの氏名は、氏名の誤りを示す 400 で、注文を作らない")
        void rejectsBlankName() throws Exception {
            // 操作と確認：全角の空白だけの氏名は 400 で、エラーコードが INVALID_RECIPIENT_NAME
            fixture.placeWith(checkoutId, bodyWith("　　", "千代田 1-1"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("INVALID_RECIPIENT_NAME"));

            // 確認：注文がない
            fixture.expectNotPlaced(checkoutId);
        }

        @Test
        @DisplayName("AC-86 空白だけの住所は、住所の誤りを示す 400 で、注文を作らない")
        void rejectsBlankAddress() throws Exception {
            // 操作と確認：半角の空白だけの住所は 400 で、エラーコードが INVALID_ADDRESS
            fixture.placeWith(checkoutId, bodyWith("山田 太郎", "   "))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("INVALID_ADDRESS"));

            // 確認：注文がない
            fixture.expectNotPlaced(checkoutId);
        }
    }

    // ============================== 準備の補助 ==============================

    /**
     * 郵便番号と都道府県・市区町村のコードを指定した、注文のリクエストの本文を作る。
     *
     * @param postalCode       郵便番号
     * @param prefectureCode   都道府県のコード
     * @param municipalityCode 市区町村のコード
     * @return 注文のリクエストの本文
     */
    private static String body(String postalCode, String prefectureCode, String municipalityCode) {
        return body(postalCode, prefectureCode, municipalityCode, "03-1234-5678");
    }

    /**
     * 郵便番号と都道府県・市区町村のコードと電話番号を指定した、注文のリクエストの本文を作る。
     *
     * @param postalCode       郵便番号
     * @param prefectureCode   都道府県のコード
     * @param municipalityCode 市区町村のコード
     * @param phone            電話番号
     * @return 注文のリクエストの本文
     */
    private static String body(String postalCode, String prefectureCode, String municipalityCode, String phone) {
        return """
                {"expectedTotal": %d, "shippingAddress": {"name": "山田 太郎", "postalCode": "%s",
                 "prefectureCode": "%s", "municipalityCode": "%s", "address": "千代田 1-1", "phone": "%s"}}"""
                .formatted(PlaceOrderFixture.TOTAL, postalCode, prefectureCode, municipalityCode, phone);
    }

    /**
     * 氏名と住所を指定した、注文のリクエストの本文を作る。
     *
     * <p>郵便番号と地域と電話番号は、正しい値にする。
     *
     * @param name    氏名
     * @param address 市区町村より下の住所
     * @return 注文のリクエストの本文
     */
    private static String bodyWith(String name, String address) {
        return """
                {"expectedTotal": %d, "shippingAddress": {"name": "%s", "postalCode": "%s",
                 "prefectureCode": "%s", "municipalityCode": "%s", "address": "%s", "phone": "03-1234-5678"}}"""
                .formatted(PlaceOrderFixture.TOTAL, name, POSTAL_CODE, TOKYO, CHIYODA, address);
    }
}
