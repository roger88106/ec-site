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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.ecsite.OrderTestData.startCheckoutRequest;
import static com.ecsite.jooq.cart.Tables.CART_;
import static com.ecsite.jooq.catalog.Tables.PRODUCT;
import static com.ecsite.jooq.inventory.Tables.STOCK_ITEM;
import static com.ecsite.jooq.inventory.Tables.STOCK_RESERVATION;
import static com.ecsite.jooq.ordering.Tables.CHECKOUT;
import static com.ecsite.jooq.tax.Tables.TAX_CATEGORY;
import static com.ecsite.jooq.tax.Tables.TAX_RATE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 注文手続きと、カートの変更のイベントの受け入れテスト（AC-84）。<br />
 * カートの変更のイベントはコミットの後に非同期で届くので、テストはトランザクションにせず、コミットして処理を待ち、
 * 作ったデータを後で消す。<br />
 * 後処理（{@code @AfterEach}）を持つ理由：コミットした公開の商品が残ると、商品の一覧の件数を確認する他のテスト
 * （店舗の統合テスト）に見えて、失敗するため。
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

    /**
     * このテストで作った商品
     */
    private final List<CatalogTestData.TestProduct> products = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        clock.setInstant(NOW);
        ShippingTaxCategory.ensure(dsl);
        data = new CatalogTestData(mockMvc, clock);
        taxCategoryId = data.taxCategory("カート変更-" + UUID.randomUUID().toString().substring(0, 8), "10");
        cartId = createCart();
    }

    @AfterEach
    void cleanUp() {
        var stockItemIds = products.stream()
                .flatMap(product -> dsl.select(STOCK_ITEM.ID).from(STOCK_ITEM)
                        .where(STOCK_ITEM.PRODUCT_ID.eq(product.id)).fetch(STOCK_ITEM.ID).stream())
                .toList();
        dsl.deleteFrom(STOCK_RESERVATION).where(STOCK_RESERVATION.STOCK_ITEM_ID.in(stockItemIds)).execute();
        dsl.deleteFrom(CHECKOUT).where(CHECKOUT.CART_ID.eq(cartId)).execute();
        // 会員のカートは、次に使うときに再作成される
        dsl.deleteFrom(CART_).where(CART_.ID.eq(cartId)).execute();
        products.forEach(product -> {
            dsl.deleteFrom(STOCK_ITEM).where(STOCK_ITEM.PRODUCT_ID.eq(product.id)).execute();
            dsl.deleteFrom(PRODUCT).where(PRODUCT.ID.eq(product.id)).execute();
        });
        dsl.deleteFrom(TAX_RATE).where(TAX_RATE.TAX_CATEGORY_ID.eq(taxCategoryId)).execute();
        dsl.deleteFrom(TAX_CATEGORY).where(TAX_CATEGORY.ID.eq(taxCategoryId)).execute();
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
        products.add(product);
        var variantId = product.variantIds.getFirst();
        stockItemRepository.createIfAbsent(variantId, product.id);
        stockItemRepository.adjust(variantId,
                StockAdjustment.create(quantity, StockAdjustmentReason.ARRIVAL, null, TestTokens.OPERATOR, clock.instant()));
        return variantId;
    }
}
