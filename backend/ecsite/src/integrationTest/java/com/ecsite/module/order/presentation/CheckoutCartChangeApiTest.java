package com.ecsite.module.order.presentation;

import com.ecsite.AsyncEvents;
import com.ecsite.CatalogTestData;
import com.ecsite.IntegrationTest;
import com.ecsite.InventoryTestData;
import com.ecsite.OrderTestData;
import com.ecsite.MutableClock;
import com.ecsite.ShippingTaxCategory;
import com.ecsite.TestClockConfig;
import com.ecsite.module.cart.event.CartChanged;
import com.ecsite.module.inventory.api.StockReservationApi;
import com.ecsite.module.inventory.domain.stock.port.StockItemRepository;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 注文手続きと、カートの変更のイベントの受け入れテスト（AC-84）。<br />
 * カートの変更のイベントはコミットの後に非同期で届くので、テストはトランザクションにせず、コミットして処理を待つ。<br />
 * 作ったデータは消さない。他のテストは、一覧の全体ではなく自分のデータだけを確認する。
 */
@IntegrationTest
@AutoConfigureMockMvc
@Import(TestClockConfig.class)
class CheckoutCartChangeApiTest {

    /**
     * テストの現在時刻
     */
    private static final Instant NOW = Instant.parse("2026-10-07T15:00:00Z");

    @Autowired
    MockMvc mockMvc;

    @Autowired
    MutableClock clock;

    @Autowired
    StockItemRepository stockItemRepository;

    private InventoryTestData inventory;

    @Autowired
    StockReservationApi stockReservationApi;

    @Autowired
    AsyncEvents asyncEvents;

    @Autowired
    DSLContext dsl;

    private CatalogTestData data;

    private OrderTestData orders;

    private UUID taxCategoryId;

    private UUID cartId;

    @BeforeEach
    void setUp() throws Exception {
        inventory = new InventoryTestData(stockItemRepository, clock);
        clock.setInstant(NOW);
        ShippingTaxCategory.ensure(dsl);
        data = new CatalogTestData(mockMvc, clock);
        orders = new OrderTestData(mockMvc);
        taxCategoryId = data.taxCategory("カート変更-" + UUID.randomUUID().toString().substring(0, 8), "10");
        cartId = orders.cart();
    }

    @Test
    @DisplayName("AC-84 カートを変更すると（カート変更のイベント）、そのカートの注文手続きの引当が解かれる")
    void releasesReservationOnCartChange() throws Exception {
        var variant = stockedVariant(3);
        orders.add(cartId, variant, 2);
        // 追加のイベントのリスナーが、次の注文手続きの引当を解かないように、先に処理を待つ
        awaitCartChanged();
        orders.startCheckout(cartId);
        assertThat(stockReservationApi.sellableQuantities(List.of(variant), null)).containsEntry(variant, 1);

        mockMvc.perform(put("/api/store/carts/" + cartId + "/items/" + variant)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"quantity": 1}"""))
                .andExpect(status().isNoContent());
        awaitCartChanged();

        assertThat(stockReservationApi.sellableQuantities(List.of(variant), null)).containsEntry(variant, 3);
    }

    // ============================== 補助 ==============================

    /**
     * このカートの変更のイベントの処理が終わるのを待つ
     */
    private void awaitCartChanged() {
        asyncEvents.awaitHandled(published -> published instanceof CartChanged changed && changed.cartId().equals(cartId));
    }

    /**
     * 公開した商品（1,000 円・10%）のバリアントを作り、在庫数を設定して、バリアント ID を返す
     */
    private UUID stockedVariant(int quantity) throws Exception {
        var product = data.product(taxCategoryId, "商品", 1000).publish();
        var variantId = product.variantIds.getFirst();
        inventory.stock(variantId, product.id, quantity);
        return variantId;
    }
}
