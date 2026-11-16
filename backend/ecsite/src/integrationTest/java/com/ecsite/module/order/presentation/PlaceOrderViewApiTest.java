package com.ecsite.module.order.presentation;

import com.ecsite.module.catalog.infrastructure.directory.TaxRateScheduleCaches;
import com.ecsite.support.MutableClock;
import com.ecsite.support.config.IntegrationTest;
import com.ecsite.support.data.OrderTestData;
import com.ecsite.support.security.TestTokens;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.UUID;

import static com.ecsite.module.order.presentation.PlaceOrderFixture.SHIRT_PRICE_EXCL_TAX;
import static com.ecsite.module.order.presentation.PlaceOrderFixture.SHIRT_QUANTITY;
import static com.ecsite.module.order.presentation.PlaceOrderFixture.TOTAL;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 成立した注文の表示の受け入れテスト
 *
 * <p>注文の API が返す明細・金額・お届け先が注文時点の写しであること、注文の参照の範囲を確認する。
 * 明細を見積もりから写す規則そのものは {@code OrderTest} が確認する。
 *
 * <p>準備は {@link PlaceOrderFixture}（T シャツ 2 枚のカート）。お届け先は {@code OrderTestData.orderBody} の決まった値。
 * テストごとにロールバックする。
 */
@IntegrationTest
@Transactional
class PlaceOrderViewApiTest {

    // ============================== 定数 ==============================

    /**
     * 注文の後に変える、T シャツの税抜単価
     */
    private static final long CHANGED_PRICE_EXCL_TAX = 9999;

    /**
     * T シャツの税込単価
     */
    private static final long SHIRT_PRICE_INCL_TAX = 2970;

    /**
     * T シャツの税率（%）
     */
    private static final int RATE_PERCENT = 10;

    /**
     * 明細の小計（税込単価 × 数量）
     */
    private static final long SUBTOTAL = 5940;

    /**
     * 総額に含まれる消費税
     */
    private static final long TAX_AMOUNT = 630;

    /**
     * 注文の後に変える、T シャツの税区分の税率（%）
     */
    private static final String CHANGED_RATE_PERCENT = "20";

    /**
     * 税率を変えた後の、T シャツの税込単価
     *
     * <p>税抜単価 2,700 円に税率 20% を加えた額。
     */
    private static final long CHANGED_RATE_SHIRT_PRICE_INCL_TAX = 3240;

    /**
     * 注文の後に、変えた税率が有効になるまでの時間
     */
    private static final Duration RATE_CHANGE_DELAY = Duration.ofHours(1);

    // ============================== Bean ==============================

    @Autowired
    ApplicationContext applicationContext;

    @Autowired
    MutableClock clock;

    /**
     * 共有の準備
     */
    private PlaceOrderFixture fixture;

    @BeforeEach
    void setUp() throws Exception {
        fixture = PlaceOrderFixture.prepare(applicationContext);
    }

    // ============================== テスト ==============================

    @Nested
    @DisplayName("注文時点の写し")
    class AsPlaced {

        @Test
        @DisplayName("AC-92 明細は注文時点の写しで、後で商品の価格を変えても変わらない")
        void keepsLineAsPlaced() throws Exception {
            // 準備：注文した後に、T シャツの価格を変える
            var orderId = fixture.placeOrder();
            fixture.shirt.changePrice(fixture.standard, CHANGED_PRICE_EXCL_TAX);

            // 操作：注文を表示する
            var result = show(orderId);

            // 確認：明細が注文時点の写しのまま
            assertLineAsPlaced(result, fixture.shirt.productCode, fixture.shirt.variantCodes.getFirst());
        }

        @Test
        @DisplayName("AC-92 金額の内訳は注文時点のままで、後で商品の価格を変えても変わらない")
        void keepsAmountsAsPlaced() throws Exception {
            // 準備：注文した後に、T シャツの価格を変える
            var orderId = fixture.placeOrder();
            fixture.shirt.changePrice(fixture.standard, CHANGED_PRICE_EXCL_TAX);

            // 操作：注文を表示する
            var result = show(orderId);

            // 確認：金額の内訳が注文時点のまま
            assertAmountsAsPlaced(result);
        }

        @Test
        @DisplayName("AC-92 明細の税率と税込単価は注文時点の写しで、後で税率が変わっても変わらない")
        void keepsLineAsPlacedAfterRateChange() throws Exception {
            // 準備：注文した後に、T シャツの税区分の税率が変わる
            var orderId = fixture.placeOrder();
            changeStandardRate();

            // 確認（前提）：変わった税率が、今の商品の税込価格に反映されている
            expectShirtPriceInclTax(CHANGED_RATE_SHIRT_PRICE_INCL_TAX);

            // 操作：注文を表示する
            var result = show(orderId);

            // 確認：明細が注文時点の写しのまま
            assertLineAsPlaced(result, fixture.shirt.productCode, fixture.shirt.variantCodes.getFirst());
        }

        @Test
        @DisplayName("AC-92 金額の内訳は注文時点のままで、後で税率が変わっても変わらない")
        void keepsAmountsAsPlacedAfterRateChange() throws Exception {
            // 準備：注文した後に、T シャツの税区分の税率が変わる
            var orderId = fixture.placeOrder();
            changeStandardRate();

            // 確認（前提）：変わった税率が、今の商品の税込価格に反映されている
            expectShirtPriceInclTax(CHANGED_RATE_SHIRT_PRICE_INCL_TAX);

            // 操作：注文を表示する
            var result = show(orderId);

            // 確認：金額の内訳が注文時点のまま
            assertAmountsAsPlaced(result);
        }
    }

    @Nested
    @DisplayName("注文の項目")
    class Fields {

        @Test
        @DisplayName("AC-93 注文の表示に、注文番号・注文日時・連絡先・お届け先・状態を示す")
        void showsOrderFields() throws Exception {
            // 準備：T シャツ 2 枚を注文する
            var orderId = fixture.placeOrder();

            // 操作：注文を表示する
            var result = show(orderId);

            // 確認：注文の項目がある
            assertOrderFields(result, orderId);
        }

        @Test
        @DisplayName("AC-258 注文は、お届け先の都道府県と市区町村のコードと、地域のマスターデータの名前を持つ")
        void showsCodesAndNames() throws Exception {
            // 準備：東京都千代田区へ注文する
            var orderId = fixture.placeOrder();

            // 操作と確認：コードと名前と住所を返す
            show(orderId)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.shippingAddress.prefectureCode").value(OrderTestData.PREFECTURE_CODE))
                    .andExpect(jsonPath("$.shippingAddress.prefectureName").value("東京都"))
                    .andExpect(jsonPath("$.shippingAddress.municipalityCode").value(OrderTestData.MUNICIPALITY_CODE))
                    .andExpect(jsonPath("$.shippingAddress.municipalityName").value("千代田区"))
                    .andExpect(jsonPath("$.shippingAddress.address").value(OrderTestData.ADDRESS));
        }
    }

    @Nested
    @DisplayName("注文の参照の範囲")
    class Access {

        @Test
        @DisplayName("AC-94 存在しない注文は 404")
        void returnsNotFound() throws Exception {
            // 操作と確認：ない注文の ID は 404
            show(UUID.randomUUID()).andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("AC-94 他の会員の注文は 404")
        void hidesOrderFromOtherMembers() throws Exception {
            // 準備：既定の会員が注文する
            var orderId = fixture.placeOrder();

            // 操作と確認：別の会員が表示すると 404
            fixture.mockMvc.perform(get("/api/store/orders/" + orderId).with(TestTokens.member().subject(UUID.randomUUID()).bearer()))
                    .andExpect(status().isNotFound());
        }
    }

    // ============================== 確認の補助 ==============================

    /**
     * 明細が、注文時点の T シャツ 2 枚の写しであることを確認する。
     *
     * <p>確認すること（期待値は定数）：
     * <ul>
     *   <li>品番、バリアント品番</li>
     *   <li>商品名</li>
     *   <li>軸の値（並びを含む）</li>
     *   <li>税抜単価、税込単価</li>
     *   <li>税率</li>
     *   <li>数量、小計</li>
     * </ul>
     *
     * @param result      注文の表示の応答
     * @param productCode 品番（商品の作成で採番するので、定数にできない）
     * @param variantCode バリアント品番（バリアントの作成で採番するので、定数にできない）
     * @throws Exception 確認が通らないとき
     */
    private static void assertLineAsPlaced(ResultActions result, String productCode, String variantCode) throws Exception {
        result.andExpect(jsonPath("$.lines[0].productCode").value(productCode))
                .andExpect(jsonPath("$.lines[0].variantCode").value(variantCode))
                .andExpect(jsonPath("$.lines[0].productName").value("T シャツ"))
                .andExpect(jsonPath("$.lines[0].axisValues[*].value", contains("白", "M")))
                .andExpect(jsonPath("$.lines[0].unitPrice.exclTax").value(SHIRT_PRICE_EXCL_TAX))
                .andExpect(jsonPath("$.lines[0].unitPrice.inclTax").value(SHIRT_PRICE_INCL_TAX))
                .andExpect(jsonPath("$.lines[0].ratePercent").value(RATE_PERCENT))
                .andExpect(jsonPath("$.lines[0].quantity").value(SHIRT_QUANTITY))
                .andExpect(jsonPath("$.lines[0].subtotal").value(SUBTOTAL));
    }

    /**
     * 金額の内訳が、注文時点の T シャツ 2 枚の値であることを確認する。
     *
     * <p>確認すること（期待値は定数）：
     * <ul>
     *   <li>商品の合計</li>
     *   <li>送料</li>
     *   <li>総額</li>
     *   <li>消費税</li>
     * </ul>
     *
     * @param result 注文の表示の応答
     * @throws Exception 確認が通らないとき
     */
    private static void assertAmountsAsPlaced(ResultActions result) throws Exception {
        result.andExpect(jsonPath("$.amounts.itemsTotal").value(SUBTOTAL))
                .andExpect(jsonPath("$.amounts.shippingFee").value(OrderTestData.SHIPPING_FEE))
                .andExpect(jsonPath("$.amounts.total").value(TOTAL))
                .andExpect(jsonPath("$.amounts.taxes[0].taxAmount").value(TAX_AMOUNT));
    }

    /**
     * 注文の表示に、注文の項目があることを確認する。
     *
     * <p>確認すること：
     * <ul>
     *   <li>状態が 200</li>
     *   <li>注文 ID</li>
     *   <li>注文番号がある</li>
     *   <li>注文日時がある</li>
     *   <li>連絡先のメールアドレスが会員のもの</li>
     *   <li>お届け先の郵便番号と電話番号が、ハイフンを除いた数字</li>
     *   <li>状態が「支払待ち」</li>
     * </ul>
     *
     * @param result  注文の表示の応答
     * @param orderId 注文 ID（実行ごとに作るので、定数にできない）
     * @throws Exception 確認が通らないとき
     */
    private static void assertOrderFields(ResultActions result, UUID orderId) throws Exception {
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(orderId.toString()))
                .andExpect(jsonPath("$.orderNumber").exists())
                .andExpect(jsonPath("$.orderedAt").exists())
                .andExpect(jsonPath("$.email").value(TestTokens.MEMBER_EMAIL))
                .andExpect(jsonPath("$.shippingAddress.postalCode").value(OrderTestData.POSTAL_CODE.replace("-", "")))
                .andExpect(jsonPath("$.shippingAddress.phone").value(OrderTestData.PHONE.replace("-", "")))
                .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"));
    }

    /**
     * 店舗の商品の詳細で、T シャツの税込価格を確認する。
     *
     * @param priceInclTax 期待する税込価格
     * @throws Exception API の呼び出しに失敗したとき、または確認が通らないとき
     */
    private void expectShirtPriceInclTax(long priceInclTax) throws Exception {
        fixture.mockMvc.perform(get("/api/store/products/" + fixture.shirt.id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.price.inclTax").value(priceInclTax));
    }

    // ============================== 準備の補助 ==============================

    /**
     * 標準の税区分（T シャツの税区分）の税率を {@link #CHANGED_RATE_PERCENT} に変え、変えた税率が有効になる時刻まで時計を進める。
     *
     * <p>前の税率を読んだ税率のキャッシュを捨て、次の取得で変えた税率を読むようにする。
     *
     * @throws Exception API の呼び出しに失敗したとき
     */
    private void changeStandardRate() throws Exception {
        var effectiveFrom = clock.instant().plus(RATE_CHANGE_DELAY);
        fixture.catalog.changeRate(fixture.standard, CHANGED_RATE_PERCENT, effectiveFrom);
        clock.setInstant(effectiveFrom);
        TaxRateScheduleCaches.evict(applicationContext);
    }

    /**
     * 既定の会員で注文を表示する。
     *
     * @param orderId 注文 ID
     * @return 応答
     * @throws Exception API の呼び出しに失敗したとき
     */
    private ResultActions show(UUID orderId) throws Exception {
        return fixture.mockMvc.perform(get("/api/store/orders/" + orderId));
    }
}
