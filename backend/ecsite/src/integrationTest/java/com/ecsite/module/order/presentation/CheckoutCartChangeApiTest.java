package com.ecsite.module.order.presentation;

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

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 注文手続きと、カートの変更のイベントの受け入れテスト
 *
 * <p>カートの変更のイベントで、そのカートの注文手続きの引当が解けることだけを確認する。
 * 進み直しと期限の経過による引当の解除は {@link CheckoutStartApiTest} が確認する。
 *
 * <p>カートの変更のイベントはコミットの後に非同期で届くので、テストはトランザクションにせず、コミットして処理を待つ。
 *
 * <p>作ったデータは消さない。他のテストは、一覧の全体ではなく自分のデータだけを確認する。
 */
@IntegrationTest
class CheckoutCartChangeApiTest {

    // ============================== Bean ==============================

    @Autowired
    MockMvc mockMvc;

    @Autowired
    MutableClock clock;

    @Autowired
    StockItemRepository stockItemRepository;

    /**
     * 在庫を作る補助
     */
    private InventoryTestData inventory;

    @Autowired
    StockReservationApi stockReservationApi;

    @Autowired
    AsyncEvents asyncEvents;

    @Autowired
    DSLContext dsl;

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
        taxCategoryId = catalog.taxCategory("カート変更-" + UUID.randomUUID().toString().substring(0, 8), "10");
        cartId = orders.cart();
    }

    // ============================== テスト ==============================

    @Test
    @DisplayName("AC-84 カートを変更すると（カート変更のイベント）、そのカートの注文手続きの引当が解かれる")
    void releasesReservationOnCartChange() throws Exception {
        // 準備：在庫 3 の品目を 2 個入れて、注文手続きに進む
        var variant = stockedVariant(3);
        orders.add(cartId, variant, 2);
        // 追加のイベントのリスナーが、次の注文手続きの引当を解かないように、先に処理を待つ
        awaitCartChanged();
        orders.startCheckout(cartId);

        // 確認（前提）：2 個を引き当てている
        assertThat(stockReservationApi.sellableQuantities(List.of(variant), null)).containsEntry(variant, 1);

        // 操作：カートの数量を変え、変更のイベントの処理を待つ
        mockMvc.perform(put("/api/store/carts/" + cartId + "/items/" + variant).with(owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"quantity": 1}"""))
                .andExpect(status().isNoContent());
        awaitCartChanged();

        // 確認：引当が解け、販売可能数が在庫数に戻っている
        assertThat(stockReservationApi.sellableQuantities(List.of(variant), null)).containsEntry(variant, 3);
    }

    // ============================== 準備の補助 ==============================

    /**
     * このカートの変更のイベントの処理が終わるのを待つ。
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
