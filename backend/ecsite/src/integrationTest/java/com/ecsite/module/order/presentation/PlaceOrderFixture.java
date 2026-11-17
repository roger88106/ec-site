package com.ecsite.module.order.presentation;

import com.ecsite.module.inventory.domain.stock.port.StockItemRepository;
import com.ecsite.support.MutableClock;
import com.ecsite.support.data.CatalogTestData;
import com.ecsite.support.data.InventoryTestData;
import com.ecsite.support.data.OrderTestData;
import com.ecsite.support.data.ShippingTaxCategory;
import com.ecsite.support.http.TestJson;
import com.jayway.jsonpath.JsonPath;
import org.jooq.DSLContext;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.ecsite.jooq.ordering.Tables.SALES_ORDER;
import static com.ecsite.support.data.OrderTestData.placeOrderRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 注文の成立の受け入れテスト（{@code PlaceOrder*ApiTest}）が共有する準備と確認の補助
 *
 * <p>標準の税区分（10%）、公開した T シャツ（白・M、税抜 2,700 円、在庫数 3）、T シャツ 2 枚を入れた会員のカートを作る。
 * 時計は、テストごとに戻る初期値（{@code TestClockConfig.INITIAL_INSTANT}、日本時間の 2026-10-08 00:00）のまま {@link #prepare} を呼ぶ。
 */
final class PlaceOrderFixture {

    // ============================== 定数 ==============================

    /**
     * T シャツの税抜単価
     */
    static final long SHIRT_PRICE_EXCL_TAX = 2700;

    /**
     * 準備の時点の T シャツの在庫数
     */
    static final int SHIRT_STOCK = 3;

    /**
     * カートに入れた T シャツの数量
     */
    static final int SHIRT_QUANTITY = 2;

    /**
     * T シャツ 2 枚の注文の総額
     *
     * <p>税込単価 2,970 円 × 2 枚と、送料 1,000 円。
     */
    static final long TOTAL = 6940;

    // ============================== 準備したもの ==============================

    /**
     * API を呼ぶ MockMvc
     */
    final MockMvc mockMvc;

    /**
     * DB を直接読む DSLContext
     */
    final DSLContext dsl;

    /**
     * 在庫を作る補助
     */
    final InventoryTestData inventory;

    /**
     * 税区分・カテゴリ・商品を作る補助
     */
    final CatalogTestData catalog;

    /**
     * カート・注文手続き・注文を作る補助
     */
    final OrderTestData orders;

    /**
     * 標準の税区分（10%）の ID
     */
    final UUID standard;

    /**
     * 公開した T シャツ（白・M）
     */
    final CatalogTestData.TestProduct shirt;

    /**
     * T シャツ 2 枚を入れた、既定の会員のカートの ID
     */
    final UUID cartId;

    /**
     * 共有の準備を作る。
     *
     * @param context テストのコンテキスト
     * @throws Exception API の呼び出しに失敗したとき
     */
    private PlaceOrderFixture(ApplicationContext context) throws Exception {
        mockMvc = context.getBean(MockMvc.class);
        dsl = context.getBean(DSLContext.class);
        var clock = context.getBean(MutableClock.class);
        inventory = new InventoryTestData(context.getBean(StockItemRepository.class), clock);
        ShippingTaxCategory.ensure(dsl);
        catalog = new CatalogTestData(mockMvc, clock);
        orders = new OrderTestData(mockMvc);
        standard = catalog.taxCategory("標準", "10");
        shirt = catalog.product(standard, "T シャツ", SHIRT_PRICE_EXCL_TAX, "色", "サイズ").variant("白", "M").publish();
        inventory.stock(shirt.variantIds.getFirst(), shirt.id, SHIRT_STOCK);
        cartId = orders.cart();
        orders.add(cartId, shirt.variantIds.getFirst(), SHIRT_QUANTITY);
    }

    // ============================== 準備の補助 ==============================

    /**
     * 税区分・T シャツ・在庫・カートを作る。
     *
     * @param context テストのコンテキスト
     * @return 準備したもの
     * @throws Exception API の呼び出しに失敗したとき
     */
    static PlaceOrderFixture prepare(ApplicationContext context) throws Exception {
        return new PlaceOrderFixture(context);
    }

    /**
     * T シャツ（白・M）のバリアント ID を返す。
     *
     * @return バリアント ID
     */
    UUID shirtVariant() {
        return shirt.variantIds.getFirst();
    }

    /**
     * 確認した注文の一覧を送ってカートから注文手続きを始め、注文手続き ID を返す。
     *
     * @param acknowledged 確認した支払待ちの注文の ID
     * @return 注文手続き ID
     * @throws Exception API の呼び出しに失敗したとき
     */
    UUID startAcknowledging(UUID... acknowledged) throws Exception {
        return orders.startCheckout(cartId, List.of(acknowledged));
    }

    /**
     * カートの T シャツの数量を変え、204 を確認する。
     *
     * <p>減らす変更は販売可能数を確認しないので、注文手続き中でもできる。
     *
     * @param quantity 変えた後の数量
     * @throws Exception API の呼び出しに失敗したとき、または 204 でないとき
     */
    void changeShirtQuantity(int quantity) throws Exception {
        mockMvc.perform(put("/api/store/carts/" + cartId + "/items/" + shirtVariant())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"quantity": %d}""".formatted(quantity)))
                .andExpect(status().isNoContent());
    }

    // ============================== 操作の補助 ==============================

    /**
     * 確認した総額を指定して、決まったお届け先で注文する。
     *
     * @param checkoutId    注文手続き ID
     * @param expectedTotal 確認した総額
     * @return 応答
     * @throws Exception API の呼び出しに失敗したとき
     */
    ResultActions place(UUID checkoutId, long expectedTotal) throws Exception {
        return mockMvc.perform(placeOrderRequest(checkoutId, expectedTotal));
    }

    /**
     * 本文を指定して注文する。
     *
     * @param checkoutId 注文手続き ID
     * @param body       注文のリクエストの本文
     * @return 応答
     * @throws Exception API の呼び出しに失敗したとき
     */
    ResultActions placeWith(UUID checkoutId, String body) throws Exception {
        return mockMvc.perform(placeOrderRequest(checkoutId, body));
    }

    /**
     * T シャツ 2 枚の注文手続きから注文し、201 を確認して、注文 ID を返す。
     *
     * @param checkoutId 注文手続き ID
     * @return 注文 ID
     * @throws Exception API の呼び出しに失敗したとき、または 201 でないとき
     */
    UUID placed(UUID checkoutId) throws Exception {
        return TestJson.createdId(place(checkoutId, TOTAL).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
    }

    /**
     * カートから注文手続きを始めて T シャツ 2 枚を注文し、注文 ID を返す。
     *
     * @return 注文 ID
     * @throws Exception API の呼び出しに失敗したとき、または 201 でないとき
     */
    UUID placeOrder() throws Exception {
        return placed(orders.startCheckout(cartId));
    }

    /**
     * 注文の支払の期限を、注文の API から読む。
     *
     * @param orderId 注文 ID
     * @return 支払の期限
     * @throws Exception API の呼び出しに失敗したとき
     */
    Instant paymentDeadlineOf(UUID orderId) throws Exception {
        var order = mockMvc.perform(get("/api/store/orders/" + orderId)).andReturn().getResponse().getContentAsString();
        return Instant.parse(JsonPath.read(order, "$.paymentDeadline"));
    }

    // ============================== 確認の補助 ==============================

    /**
     * 再送が、作成済みの注文 ID を 200 で返すことを確認する。
     *
     * <p>確認すること：
     * <ul>
     *   <li>状態が 200</li>
     *   <li>応答の ID が作成済みの注文 ID</li>
     * </ul>
     *
     * @param checkoutId 再送する注文手続き ID
     * @param orderId    作成済みの注文 ID（実行ごとに作るので、定数にできない）
     * @throws Exception API の呼び出しに失敗したとき、または確認が通らないとき
     */
    void expectExisting(UUID checkoutId, UUID orderId) throws Exception {
        place(checkoutId, TOTAL)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(orderId.toString()));
    }

    /**
     * 支払待ちの注文の確認を求める 409 であることを確認する。
     *
     * <p>確認すること：
     * <ul>
     *   <li>状態が 409</li>
     *   <li>エラーコードが {@code PENDING_ORDER_CONFIRMATION_REQUIRED}</li>
     * </ul>
     *
     * @param result 注文の応答
     * @throws Exception 確認が通らないとき
     */
    static void expectConfirmationRequired(ResultActions result) throws Exception {
        result.andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("PENDING_ORDER_CONFIRMATION_REQUIRED"));
    }

    /**
     * 支払待ちの注文が上限に達していることを示す 409 であることを確認する。
     *
     * <p>確認すること：
     * <ul>
     *   <li>状態が 409</li>
     *   <li>エラーコードが {@code PENDING_ORDER_LIMIT_EXCEEDED}</li>
     * </ul>
     *
     * @param result 注文、または注文手続きの開始の応答
     * @throws Exception 確認が通らないとき
     */
    static void expectLimitExceeded(ResultActions result) throws Exception {
        result.andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("PENDING_ORDER_LIMIT_EXCEEDED"));
    }

    /**
     * 注文手続きに注文がないことを確認する。
     *
     * @param checkoutId 注文手続き ID
     */
    void expectNotPlaced(UUID checkoutId) {
        assertThat(orderCount(checkoutId)).isZero();
    }

    /**
     * T シャツの在庫数を確認する。
     *
     * @param quantity 期待する在庫数
     * @throws Exception API の呼び出しに失敗したとき、または在庫数が違うとき
     */
    void expectQuantity(int quantity) throws Exception {
        InventoryTestData.expectQuantity(mockMvc, shirtVariant(), quantity);
    }

    /**
     * 注文手続きから作った注文の件数を返す。
     *
     * @param checkoutId 注文手続き ID
     * @return 注文の件数
     */
    int orderCount(UUID checkoutId) {
        return dsl.fetchCount(SALES_ORDER, SALES_ORDER.CHECKOUT_ID.eq(checkoutId));
    }
}
