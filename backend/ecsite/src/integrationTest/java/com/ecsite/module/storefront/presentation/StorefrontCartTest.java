package com.ecsite.module.storefront.presentation;

import com.ecsite.CatalogTestData;
import com.ecsite.CatalogTestData.TestProduct;
import com.ecsite.IntegrationTest;
import com.ecsite.InventoryTestData;
import com.ecsite.MutableClock;
import com.ecsite.OrderTestData;
import com.ecsite.ShippingTaxCategory;
import com.ecsite.TestJson;
import com.ecsite.module.inventory.domain.stock.port.StockItemRepository;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * カートの表示の受け入れテスト
 *
 * <p>カートの品目の項目、金額、画像、現在の価格、購入できない品目、期限の切れたカートの表示を、店舗の API を通して確認する。
 * カートの変更は {@code CartApiTest}、金額の計算と購入できるかの判定そのものは {@code QuoteTest} が確認する。
 *
 * <p>商品は B 側の API で作り、在庫は在庫モジュールのリポジトリで直接作る。テストごとにロールバックする。
 */
@IntegrationTest
@Transactional
class StorefrontCartTest {

    // ============================== 定数 ==============================

    /**
     * テストの現在時刻
     *
     * <p>日本時間の 2026-10-08 00:00。
     */
    private static final Instant NOW = Instant.parse("2026-10-07T15:00:00Z");

    /**
     * カートの API のパス
     */
    private static final String CARTS = "/api/store/carts";

    /**
     * T シャツの商品名
     */
    private static final String SHIRT_NAME = "T シャツ";

    /**
     * T シャツの税抜単価
     */
    private static final int SHIRT_PRICE_EXCL_TAX = 2700;

    /**
     * T シャツの税込単価（税率 10%）
     */
    private static final int SHIRT_PRICE_INCL_TAX = 2970;

    /**
     * お茶の税抜単価
     */
    private static final int TEA_PRICE_EXCL_TAX = 1234;

    /**
     * お茶の税込単価（税率 8%。1 円未満を切り捨てる）
     */
    private static final int TEA_PRICE_INCL_TAX = 1332;

    /**
     * 送料
     */
    private static final int SHIPPING_FEE = Math.toIntExact(OrderTestData.SHIPPING_FEE);

    // ============================== Bean ==============================

    /**
     * API を呼ぶ MockMvc
     */
    @Autowired
    MockMvc mockMvc;

    /**
     * テストの時計
     */
    @Autowired
    MutableClock clock;

    /**
     * 在庫のリポジトリ（在庫数を増減する）
     */
    @Autowired
    StockItemRepository stockItemRepository;

    /**
     * 送料の税区分を用意する jOOQ
     */
    @Autowired
    DSLContext dsl;

    /**
     * 在庫を作る補助
     */
    private InventoryTestData inventory;

    /**
     * 税区分・カテゴリ・商品を作る補助
     */
    private CatalogTestData catalog;

    /**
     * 税率 10% の税区分の ID
     */
    private UUID standard;

    /**
     * 税率 8% の税区分の ID
     */
    private UUID reduced;

    /**
     * テストごとに作るカートの ID
     */
    private UUID cartId;

    @BeforeEach
    void setUp() throws Exception {
        inventory = new InventoryTestData(stockItemRepository, NOW);
        ShippingTaxCategory.ensure(dsl);
        catalog = new CatalogTestData(mockMvc, clock);
        standard = catalog.taxCategory("標準", "10");
        reduced = catalog.taxCategory("軽減", "8");
        var body = mockMvc.perform(post(CARTS)).andReturn().getResponse().getContentAsString();
        cartId = TestJson.createdId(body);
    }

    // ============================== テスト ==============================

    @Nested
    @DisplayName("品目の項目")
    class ItemFields {

        @Test
        @DisplayName("AC-77 品目は入れた順に、商品 ID・商品名・軸の値を持つ")
        void returnsItemIdentity() throws Exception {
            // 準備：T シャツ（白・M）とお茶を 1 つずつ入れたカート
            var cart = shirtAndTea();

            // 操作と確認：入れた順で、T シャツの商品と軸の値
            showCart()
                    .andExpect(jsonPath("$.id").value(cartId.toString()))
                    .andExpect(jsonPath("$.items[*].variantId",
                            contains(cart.shirt().variantIds.getFirst().toString(), cart.tea().variantIds.getFirst().toString())))
                    .andExpect(jsonPath("$.items[0].productId").value(cart.shirt().id.toString()))
                    .andExpect(jsonPath("$.items[0].productName").value(SHIRT_NAME))
                    .andExpect(jsonPath("$.items[0].axisValues[*].name", contains("色", "サイズ")))
                    .andExpect(jsonPath("$.items[0].axisValues[*].value", contains("白", "M")));
        }

        @Test
        @DisplayName("AC-77 品目は、税込・税抜の単価、数量、小計を持つ")
        void returnsItemAmounts() throws Exception {
            // 準備：T シャツとお茶を 1 つずつ入れたカート（先頭の品目が T シャツ）
            shirtAndTea();

            // 操作と確認：税込 2,970 円・税抜 2,700 円で 1 つ、小計 2,970 円
            showCart()
                    .andExpect(jsonPath("$.items[0].unitPrice.inclTax").value(SHIRT_PRICE_INCL_TAX))
                    .andExpect(jsonPath("$.items[0].unitPrice.exclTax").value(SHIRT_PRICE_EXCL_TAX))
                    .andExpect(jsonPath("$.items[0].quantity").value(1))
                    .andExpect(jsonPath("$.items[0].subtotal").value(SHIRT_PRICE_INCL_TAX));
        }

        @Test
        @DisplayName("AC-77 購入できる品目は、購入できると示され、理由を持たない")
        void marksPurchasableItem() throws Exception {
            // 準備：在庫のある T シャツとお茶を 1 つずつ入れたカート（先頭の品目が T シャツ）
            shirtAndTea();

            // 操作と確認：購入でき、理由は null
            showCart()
                    .andExpect(jsonPath("$.items[0].purchasable").value(true))
                    .andExpect(jsonPath("$.items[0].unpurchasableReason").value(nullValue()));
        }
    }

    @Nested
    @DisplayName("金額")
    class Amounts {

        @Test
        @DisplayName("AC-77 商品の合計・送料・総額を返す")
        void returnsTotals() throws Exception {
            // 準備：T シャツとお茶を 1 つずつ入れたカート
            shirtAndTea();

            // 操作と確認：商品の合計は 2 つの小計の和、総額はそれに送料を足した額
            showCart()
                    .andExpect(jsonPath("$.amounts.itemsTotal").value(SHIRT_PRICE_INCL_TAX + TEA_PRICE_INCL_TAX))
                    .andExpect(jsonPath("$.amounts.shippingFee").value(SHIPPING_FEE))
                    .andExpect(jsonPath("$.amounts.total").value(SHIRT_PRICE_INCL_TAX + TEA_PRICE_INCL_TAX + SHIPPING_FEE));
        }

        @Test
        @DisplayName("AC-77 消費税額は、税率ごとに対象の額を合計してから求める")
        void returnsTaxesPerRate() throws Exception {
            // 準備：10% の T シャツと 8% のお茶を 1 つずつ入れたカート
            shirtAndTea();

            // 操作と確認：10% の対象 3,970 円（送料を含む）→ 360 円、8% の対象 1,332 円 → 98 円
            showCart()
                    .andExpect(jsonPath("$.amounts.taxes[*].ratePercent", contains(10, 8)))
                    .andExpect(jsonPath("$.amounts.taxes[*].taxableAmount", contains(3970, 1332)))
                    .andExpect(jsonPath("$.amounts.taxes[*].taxAmount", contains(360, 98)));
        }

        @Test
        @DisplayName("AC-77 購入できない品目がなければ、注文できる")
        void isOrderable() throws Exception {
            // 準備：購入できる品目だけのカート
            shirtAndTea();

            // 操作と確認：注文できる
            showCart()
                    .andExpect(jsonPath("$.orderable").value(true));
        }
    }

    @Nested
    @DisplayName("品目の画像")
    class ItemImages {

        @Test
        @DisplayName("AC-77、38 バリアントの画像があれば、その URL を返す")
        void returnsVariantImage() throws Exception {
            // 準備：白に 2 枚目の画像を指定した T シャツの、白を入れたカート
            var shirt = shirtWithVariantImage();
            add(shirt.variantIds.getFirst(), 1);

            // 操作と確認：2 枚目の画像の URL
            showCart()
                    .andExpect(jsonPath("$.items[0].imageUrl").value(shirt.imageUrls.get(1)));
        }

        @Test
        @DisplayName("AC-77、38 バリアントの画像がなければ、商品の代表画像（先頭）の URL を返す")
        void returnsRepresentativeImage() throws Exception {
            // 準備：画像を指定していない黒を入れたカート
            var shirt = shirtWithVariantImage();
            add(shirt.variantIds.get(1), 1);

            // 操作と確認：先頭の画像の URL
            showCart()
                    .andExpect(jsonPath("$.items[0].imageUrl").value(shirt.imageUrls.getFirst()));
        }

        @Test
        @DisplayName("AC-77 商品に画像がなければ、品目の画像は null")
        void returnsNullWithoutImages() throws Exception {
            // 準備：画像のない T シャツとお茶を 1 つずつ入れたカート（先頭の品目が T シャツ）
            shirtAndTea();

            // 操作と確認：null
            showCart()
                    .andExpect(jsonPath("$.items[0].imageUrl").value(nullValue()));
        }
    }

    @Nested
    @DisplayName("現在の価格")
    class CurrentPrice {

        @Test
        @DisplayName("AC-79 単価は常に現在の価格で表示する")
        void showsCurrentPrice() throws Exception {
            // 準備：税抜 1,000 円の商品を 2 つ入れた後、税抜 2,000 円に変える
            var product = catalog.product(standard, "商品", 1000).publish();
            inventory.stock(product.variantIds.getFirst(), 5);
            add(product.variantIds.getFirst(), 2);
            product.changePrice(standard, 2000);

            // 操作と確認：単価は税込 2,200 円、小計は 4,400 円
            showCart()
                    .andExpect(jsonPath("$.items[0].unitPrice.inclTax").value(2200))
                    .andExpect(jsonPath("$.items[0].subtotal").value(4400));
        }
    }

    @Nested
    @DisplayName("購入できなくなった品目")
    class Unpurchasable {

        @Test
        @DisplayName("AC-80 購入できなくなった品目は、購入できないと示され、理由を持つ")
        void marksWithReason() throws Exception {
            // 準備：非公開・販売停止・売り切れ・数量より少ない在庫の品目と、買える品目のカート
            unpurchasableCart();

            // 操作と確認：入れた順に、4 つの理由と、買える品目
            showCart()
                    .andExpect(jsonPath("$.items[*].purchasable", contains(false, false, false, false, true)))
                    .andExpect(jsonPath("$.items[*].unpurchasableReason",
                            contains("NOT_SHOWN", "SALES_SUSPENDED", "OUT_OF_STOCK", "INSUFFICIENT_STOCK", null)));
        }

        @Test
        @DisplayName("AC-80 購入できなくなった品目は、合計に含めない")
        void excludesFromTotals() throws Exception {
            // 準備：購入できない 4 品目と、税込 1,100 円を 2 つの買える品目のカート
            unpurchasableCart();

            // 操作と確認：商品の合計は買える品目の 2,200 円だけ、総額は送料を足した 3,200 円
            showCart()
                    .andExpect(jsonPath("$.amounts.itemsTotal").value(2200))
                    .andExpect(jsonPath("$.amounts.total").value(3200));
        }

        @Test
        @DisplayName("AC-80 購入できなくなった品目があると、注文できない")
        void isNotOrderable() throws Exception {
            // 準備：購入できない品目を含むカート
            unpurchasableCart();

            // 操作と確認：注文できない
            showCart()
                    .andExpect(jsonPath("$.orderable").value(false));
        }
    }

    @Nested
    @DisplayName("期限")
    class Expiry {

        @Test
        @DisplayName("AC-81 最後の変更から 30 日の直前は、品目が残り注文できる")
        void keepsItemsJustBeforeExpiry() throws Exception {
            // 準備：品目を入れた時刻から 30 日の 1 ミリ秒前
            var changedAt = cartWithOneItem();
            clock.setInstant(changedAt.plus(Duration.ofDays(30)).minusMillis(1));

            // 操作と確認：品目が 1 つで、注文できる
            showCart()
                    .andExpect(jsonPath("$.items", hasSize(1)))
                    .andExpect(jsonPath("$.orderable").value(true));
        }

        @Test
        @DisplayName("AC-81 最後の変更から 30 日たったカートは、空として返す")
        void returnsExpiredCartAsEmpty() throws Exception {
            // 準備：品目を入れた時刻から 30 日たつ
            var changedAt = cartWithOneItem();
            clock.setInstant(changedAt.plus(Duration.ofDays(30)));

            // 操作と確認：品目がない
            showCart()
                    .andExpect(jsonPath("$.items").isEmpty());
        }

        @Test
        @DisplayName("AC-81 期限の切れて空になったカートは、送料・総額が 0 で、消費税額がない")
        void returnsZeroAmountsForExpiredCart() throws Exception {
            // 準備：品目を入れた時刻から 30 日たつ
            var changedAt = cartWithOneItem();
            clock.setInstant(changedAt.plus(Duration.ofDays(30)));

            // 操作と確認：送料 0、総額 0、消費税額なし
            showCart()
                    .andExpect(jsonPath("$.amounts.shippingFee").value(0))
                    .andExpect(jsonPath("$.amounts.total").value(0))
                    .andExpect(jsonPath("$.amounts.taxes").isEmpty());
        }

        @Test
        @DisplayName("AC-81 期限の切れて空になったカートは、注文できない")
        void expiredCartIsNotOrderable() throws Exception {
            // 準備：品目を入れた時刻から 30 日たつ
            var changedAt = cartWithOneItem();
            clock.setInstant(changedAt.plus(Duration.ofDays(30)));

            // 操作と確認：注文できない
            showCart()
                    .andExpect(jsonPath("$.orderable").value(false));
        }
    }

    @Nested
    @DisplayName("ないカート")
    class UnknownCart {

        @Test
        @DisplayName("存在しないカートは見つからない")
        void returnsNotFoundForUnknownCart() throws Exception {
            // 操作と確認：見つからない
            mockMvc.perform(get(CARTS + "/" + UUID.randomUUID())).andExpect(status().isNotFound());
        }
    }

    // ============================== 準備の補助 ==============================

    /**
     * T シャツとお茶を入れたカートの商品
     *
     * @param shirt T シャツ（10%、白・M）
     * @param tea   お茶（8%、軸なし）
     */
    private record ShirtAndTea(TestProduct shirt, TestProduct tea) {
    }

    /**
     * T シャツ（白・M）とお茶を 1 つずつ、この順にカートに入れる。
     *
     * <p>どちらも画像はなく、在庫は 5。
     *
     * @return 入れた商品
     */
    private ShirtAndTea shirtAndTea() throws Exception {
        var shirt = catalog.product(standard, SHIRT_NAME, SHIRT_PRICE_EXCL_TAX, "色", "サイズ").variant("白", "M").publish();
        var tea = catalog.product(reduced, "お茶", TEA_PRICE_EXCL_TAX).publish();
        inventory.stock(shirt.variantIds.getFirst(), 5);
        inventory.stock(tea.variantIds.getFirst(), 5);
        add(shirt.variantIds.getFirst(), 1);
        add(tea.variantIds.getFirst(), 1);
        return new ShirtAndTea(shirt, tea);
    }

    /**
     * 白と黒のバリアントと 2 枚の画像を持ち、白に 2 枚目の画像を指定した T シャツを作る（在庫は 5 ずつ）。
     *
     * @return 商品
     */
    private TestProduct shirtWithVariantImage() throws Exception {
        var shirt = catalog.product(standard, SHIRT_NAME, SHIRT_PRICE_EXCL_TAX, "色").variant("白").variant("黒")
                .image().image().variantImage(0, 1).publish();
        inventory.stock(shirt.variantIds.getFirst(), 5);
        inventory.stock(shirt.variantIds.get(1), 5);
        return shirt;
    }

    /**
     * 税抜 1,000 円の 5 つの商品を 2 つずつ入れ（在庫は 3 ずつ）、先頭の 4 つを購入できなくする。
     *
     * <p>入れた順に、非公開にする、販売停止にする、在庫を 0 にする、在庫を 1（カートの数量より少ない）にする、そのまま。
     */
    private void unpurchasableCart() throws Exception {
        var hidden = catalog.product(standard, "非公開にする", 1000).publish();
        var suspended = catalog.product(standard, "販売停止にする", 1000, "色").variant("白").publish();
        var soldOut = catalog.product(standard, "売り切れる", 1000).publish();
        var short_ = catalog.product(standard, "足りなくなる", 1000).publish();
        var ok = catalog.product(standard, "買える", 1000).publish();
        for (var product : new TestProduct[]{hidden, suspended, soldOut, short_, ok}) {
            inventory.stock(product.variantIds.getFirst(), 3);
            add(product.variantIds.getFirst(), 2);
        }
        hidden.unpublish();
        suspended.suspend(0);
        inventory.adjust(soldOut.variantIds.getFirst(), -3);
        inventory.adjust(short_.variantIds.getFirst(), -2);
    }

    /**
     * 在庫のある商品を 1 つ入れる。
     *
     * @return 入れた時刻（商品の公開で時計が進むので、入れた時点の時計の時刻）
     */
    private Instant cartWithOneItem() throws Exception {
        var product = catalog.product(standard, "商品", 1000).publish();
        inventory.stock(product.variantIds.getFirst(), 5);
        var changedAt = clock.instant();
        add(product.variantIds.getFirst(), 1);
        return changedAt;
    }

    /**
     * カートに入れ、204 を確認する。
     *
     * @param variantId バリアント ID
     * @param quantity  数量
     */
    private void add(UUID variantId, int quantity) throws Exception {
        mockMvc.perform(post(CARTS + "/" + cartId + "/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"variantId": "%s", "quantity": %d}""".formatted(variantId, quantity)))
                .andExpect(status().isNoContent());
    }


    /**
     * テストのカートを表示し、200 であることを確認する。
     *
     * @return 応答
     */
    private ResultActions showCart() throws Exception {
        return mockMvc.perform(get(CARTS + "/" + cartId)).andExpect(status().isOk());
    }
}
