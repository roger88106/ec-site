package com.ecsite.module.order.presentation;

import com.ecsite.AsyncEvents;
import com.ecsite.CatalogTestData;
import com.ecsite.IntegrationTest;
import com.ecsite.MutableClock;
import com.ecsite.ShippingTaxCategory;
import com.ecsite.TestClockConfig;
import com.ecsite.TestTokens;
import com.ecsite.module.cart.event.CartChanged;
import com.ecsite.module.inventory.api.StockReservationApi;
import com.ecsite.module.inventory.domain.stock.StockAdjustment;
import com.ecsite.module.inventory.domain.stock.StockAdjustmentReason;
import com.ecsite.module.inventory.domain.stock.port.StockItemRepository;
import com.jayway.jsonpath.JsonPath;
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

import static com.ecsite.OrderTestData.startCheckoutRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 注文手続きと、カートの変更のイベントの受け入れテスト（AC-84）。<br />
 * カートの変更のイベントはコミットの後に非同期で届くので、テストはトランザクションにせず、コミットして処理を待つ。
 * 値（税区分など）はテストごとに作り、自分のデータだけを確認する（テストの書き方 第 3.1 節）。
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

    @Autowired
    StockReservationApi stockReservationApi;

    @Autowired
    AsyncEvents asyncEvents;

    @Autowired
    DSLContext dsl;

    private CatalogTestData data;

    private UUID taxCategoryId;

    private UUID cartId;

    @BeforeEach
    void setUp() throws Exception {
        clock.setInstant(NOW);
        ShippingTaxCategory.ensure(dsl);
        data = new CatalogTestData(mockMvc, clock);
        taxCategoryId = data.taxCategory("カート変更-" + UUID.randomUUID().toString().substring(0, 8), "10");
        cartId = createCart();
    }

    @Test
    @DisplayName("AC-84 カートを変更すると（カート変更のイベント）、そのカートの注文手続きの引当が解かれる")
    void releasesReservationOnCartChange() throws Exception {
        var variant = stockedVariant(3);
        add(cartId, variant, 2);
        // 追加のイベントのリスナーが、次の注文手続きの引当を解かないように、先に処理を待つ
        awaitCartChanged();
        startCheckout(cartId);
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
     * 既定の会員（TestSecurityConfig）の会員のカートの ID を返す（注文手続きは会員のカートからだけ始められる。AC-125）
     */
    private UUID createCart() throws Exception {
        var body = mockMvc.perform(get("/api/store/me/cart")).andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(body, "$.cartId"));
    }

    /**
     * 注文手続きに進み、201 を確認する
     */
    private void startCheckout(UUID cart) throws Exception {
        mockMvc.perform(startCheckoutRequest(cart)).andExpect(status().isCreated());
    }

    /**
     * カートに入れ、204 を確認する
     */
    private void add(UUID cart, UUID variantId, int quantity) throws Exception {
        mockMvc.perform(post("/api/store/carts/" + cart + "/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"variantId": "%s", "quantity": %d}""".formatted(variantId, quantity)))
                .andExpect(status().isNoContent());
    }

    /**
     * 公開した商品（1,000 円・10%）のバリアントを作り、在庫数を設定して、バリアント ID を返す
     */
    private UUID stockedVariant(int quantity) throws Exception {
        var product = data.product(taxCategoryId, "商品", 1000).publish();
        var variantId = product.variantIds.getFirst();
        stockItemRepository.createIfAbsent(variantId, product.id);
        stockItemRepository.adjust(variantId,
                StockAdjustment.create(quantity, StockAdjustmentReason.ARRIVAL, null, TestTokens.OPERATOR, clock.instant()));
        return variantId;
    }
}
