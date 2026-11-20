package com.ecsite.module.cart.presentation;

import com.ecsite.module.cart.event.CartChanged;
import com.ecsite.module.inventory.api.StockReservationApi;
import com.ecsite.module.inventory.domain.stock.port.StockItemRepository;
import com.ecsite.support.MutableClock;
import com.ecsite.support.async.AsyncEvents;
import com.ecsite.support.config.IntegrationTest;
import com.ecsite.support.data.CatalogTestData;
import com.ecsite.support.data.InventoryTestData;
import com.ecsite.support.data.OrderTestData;
import com.ecsite.support.data.ShippingTaxCategory;
import com.ecsite.support.security.TestTokens;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * カートの追加・増量が、自分の注文手続きの引当も数えて販売可能数を判定することの受け入れテスト
 *
 * <p>カートは、除く引当の持ち主を指定せずに販売可能数を読むので、同じ会員が注文手続きを始めて引き当てた数量も差し引かれる。
 * この振る舞いは意図したもので（カートの機能仕様の「品目の追加と変更」）、ここで固定する。
 *
 * <p>カートの変更のイベントはコミットの後に非同期で届くので、テストはトランザクションにせず、コミットして処理を待つ。
 * 作ったデータは消さない。他のテストは、一覧の全体ではなく自分のデータだけを確認する。
 */
@IntegrationTest
class CartOwnReservationApiTest {

    // ============================== Bean ==============================

    @Autowired
    MockMvc mockMvc;

    @Autowired
    MutableClock clock;

    @Autowired
    StockItemRepository stockItemRepository;

    @Autowired
    StockReservationApi stockReservationApi;

    @Autowired
    AsyncEvents asyncEvents;

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
     * 注文を作る補助
     */
    private OrderTestData orders;

    /**
     * テストで使う税区分の ID
     */
    private UUID taxCategoryId;

    /**
     * このテストの会員のカート ID
     */
    private UUID cartId;

    /**
     * このテストの会員
     *
     * <p>コミットするので、他のテストとカートを共有しないよう、テストごとに変える。
     */
    private TestTokens.Builder owner;

    @BeforeEach
    void setUp() throws Exception {
        inventory = new InventoryTestData(stockItemRepository, clock);
        ShippingTaxCategory.ensure(dsl);
        catalog = new CatalogTestData(mockMvc, clock);
        owner = TestTokens.member().subject(UUID.randomUUID());
        orders = new OrderTestData(mockMvc, null, null, owner.bearer());
        taxCategoryId = catalog.taxCategory("自分の引当-" + UUID.randomUUID().toString().substring(0, 8), "10");
        cartId = orders.cart();
    }

    // ============================== テスト ==============================

    @Test
    @DisplayName("注文手続きを始めると、自分の引当の分も売れないものとして数え、数量を増やせない")
    void rejectsIncreaseCountingOwnReservation() throws Exception {
        // 準備：在庫 5 の品目を 3 個入れて、注文手続きに進み、3 個を引き当てる（販売可能数は 2）
        var variant = stockedVariant(5);
        orders.add(cartId, variant, 3);
        awaitCartChanged();
        orders.startCheckout(cartId);
        assertThat(stockReservationApi.sellableQuantities(List.of(variant), null)).containsEntry(variant, 2);

        // 操作：4 個に増やす（自分の引当を除けば、販売可能数は 5 で足りる）
        var result = change(variant, 4);

        // 確認：自分の引当も数えるので、販売可能数 2 を超えるとして拒否される
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("QUANTITY_EXCEEDS_STOCK"));
    }

    @Test
    @DisplayName("注文手続きを始めると、自分の引当の分も売れないものとして数え、同じ品目を追加できない")
    void rejectsAddCountingOwnReservation() throws Exception {
        // 準備：在庫 5 の品目を 3 個入れて、注文手続きに進み、3 個を引き当てる（販売可能数は 2）
        var variant = stockedVariant(5);
        orders.add(cartId, variant, 3);
        awaitCartChanged();
        orders.startCheckout(cartId);

        // 操作：もう 1 個追加する（合算で 4）
        var result = mockMvc.perform(post("/api/store/carts/" + cartId + "/items").with(owner.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"variantId": "%s", "quantity": 1}""".formatted(variant)));

        // 確認：自分の引当も数えるので、販売可能数 2 を超えるとして拒否される
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("QUANTITY_EXCEEDS_STOCK"));
    }

    @Test
    @DisplayName("注文手続きを始めていなければ、同じ数量まで増やせる（引当がないときの比較）")
    void increasesWithoutReservation() throws Exception {
        // 準備：在庫 5 の品目を 3 個入れる（注文手続きは始めない）
        var variant = stockedVariant(5);
        orders.add(cartId, variant, 3);
        awaitCartChanged();

        // 操作：4 個に増やす
        var result = change(variant, 4);

        // 確認：受け付けられる
        result.andExpect(status().isNoContent());
    }

    // ============================== 準備の補助 ==============================

    /**
     * このカートの品目の数量を変える。
     */
    private ResultActions change(UUID variant, int quantity) throws Exception {
        return mockMvc.perform(put("/api/store/carts/" + cartId + "/items/" + variant).with(owner.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"quantity": %d}""".formatted(quantity)));
    }

    /**
     * このカートの変更のイベントの処理が終わるのを待つ。
     *
     * <p>追加のイベントのリスナーが、次の注文手続きの引当を解かないようにする。
     */
    private void awaitCartChanged() {
        asyncEvents.awaitHandled(published -> published instanceof CartChanged changed && changed.cartId().equals(cartId));
    }

    /**
     * 公開した商品（1,000 円・10%）のバリアントを作り、在庫数を設定して、バリアント ID を返す。
     *
     * @param quantity 在庫数
     * @return バリアント ID
     */
    private UUID stockedVariant(int quantity) throws Exception {
        return inventory.stockedVariant(catalog, taxCategoryId, "商品", quantity);
    }
}
