package com.ecsite.module.order.presentation;

import com.ecsite.CatalogTestData;
import com.ecsite.IntegrationTest;
import com.ecsite.InventoryTestData;
import com.ecsite.OrderTestData;
import com.ecsite.MutableClock;
import com.ecsite.ShippingTaxCategory;
import com.ecsite.TestClockConfig;
import com.ecsite.TestJson;
import com.ecsite.module.inventory.api.StockReservationApi;
import com.ecsite.module.inventory.domain.stock.port.StockItemRepository;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.ecsite.OrderTestData.startCheckoutRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 注文手続きの受け入れテスト（T2311、AC-82、84、85、88）。<br />
 * テストごとにロールバックする。カートの変更のイベントによる引当の解除（AC-84）は、コミットが要るので
 * {@link CheckoutCartChangeApiTest} で確認する。
 */
@IntegrationTest
@AutoConfigureMockMvc
@Import(TestClockConfig.class)
@Transactional
class CheckoutApiTest {

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
    DSLContext dsl;

    private CatalogTestData data;

    private OrderTestData orders;

    private UUID standard;

    private UUID cartId;

    @BeforeEach
    void setUp() throws Exception {
        inventory = new InventoryTestData(stockItemRepository, clock);
        clock.setInstant(NOW);
        ShippingTaxCategory.ensure(dsl);
        data = new CatalogTestData(mockMvc, clock);
        orders = new OrderTestData(mockMvc);
        standard = data.taxCategory("標準", "10");
        cartId = orders.cart();
    }

    @Test
    @DisplayName("AC-82 注文手続きに進むと、全品目をカートの数量だけ 15 分間引き当てる")
    void reservesAllItems() throws Exception {
        var shirt = stockedVariant(3);
        var cap = stockedVariant(5);
        orders.add(cartId, shirt, 2);
        orders.add(cartId, cap, 1);

        var checkoutId = startCheckout(cartId);

        assertThat(stockReservationApi.sellableQuantities(List.of(shirt, cap), null))
                .containsEntry(shirt, 1).containsEntry(cap, 4);
        mockMvc.perform(get("/api/store/checkouts/" + checkoutId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(checkoutId.toString()))
                .andExpect(jsonPath("$.expiresAt").value(clock.instant().plus(Duration.ofMinutes(15)).toString()))
                .andExpect(jsonPath("$.orderId").value(nullValue()));
    }

    @Test
    @DisplayName("AC-82 1 品目でも引き当てられなければ 409 で、どれも引き当てない")
    void reservesNothingWhenShort() throws Exception {
        var plenty = stockedVariant(5);
        var scarce = stockedVariant(2);
        orders.add(cartId, plenty, 1);
        orders.add(cartId, scarce, 2);
        // ほかの消費者が 1 個引き当てる
        stockReservationApi.reserve(UUID.randomUUID(), Map.of(scarce, 1), clock.instant().plus(Duration.ofMinutes(15)));

        mockMvc.perform(post("/api/store/carts/" + cartId + "/checkouts"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("OUT_OF_STOCK"));

        assertThat(stockReservationApi.sellableQuantities(List.of(plenty), null)).containsEntry(plenty, 5);
        // どの品目が足りないかは、カートの表示で示す
        mockMvc.perform(get("/api/store/carts/" + cartId))
                .andExpect(jsonPath("$.items[1].unpurchasableReason").value("INSUFFICIENT_STOCK"));
    }

    @Test
    @DisplayName("AC-88 空のカート、購入できない品目を含むカートは 400 で注文手続きに進めない")
    void rejectsUnorderableCart() throws Exception {
        mockMvc.perform(post("/api/store/carts/" + cartId + "/checkouts"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("CART_NOT_ORDERABLE"));

        var product = data.product(standard, "販売停止にする", 1000, "色").variant("白").publish();
        inventory.stock(product.variantIds.getFirst(), product.id, 3);
        orders.add(cartId, product.variantIds.getFirst(), 1);
        product.suspend(0);

        mockMvc.perform(post("/api/store/carts/" + cartId + "/checkouts"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("CART_NOT_ORDERABLE"));
    }

    @Test
    @DisplayName("AC-84 進み直すと、前の注文手続きの引当を解いて、その時点のカートで引き当て直す")
    void replacesReservationOnRestart() throws Exception {
        var variant = stockedVariant(3);
        orders.add(cartId, variant, 2);
        startCheckout(cartId);

        clock.setInstant(clock.instant().plus(Duration.ofMinutes(10)));
        var second = startCheckout(cartId);

        assertThat(stockReservationApi.sellableQuantities(List.of(variant), null)).containsEntry(variant, 1);
        // 期限も 15 分に戻る
        mockMvc.perform(get("/api/store/checkouts/" + second))
                .andExpect(jsonPath("$.expiresAt").value(clock.instant().plus(Duration.ofMinutes(15)).toString()));
    }

    @Test
    @DisplayName("AC-85 注文手続きの表示：内容と金額を返す。自分の引当は販売可能数から除くので、購入できる")
    void showsCheckoutExcludingOwnReservation() throws Exception {
        var variant = stockedVariant(1);
        orders.add(cartId, variant, 1);

        var checkoutId = startCheckout(cartId);

        mockMvc.perform(get("/api/store/checkouts/" + checkoutId))
                .andExpect(jsonPath("$.items[0].variantId").value(variant.toString()))
                .andExpect(jsonPath("$.items[0].purchasable").value(true))
                .andExpect(jsonPath("$.amounts.itemsTotal").value(1100))
                .andExpect(jsonPath("$.amounts.shippingFee").value(1000))
                .andExpect(jsonPath("$.amounts.total").value(2100));
        // カートの表示でも、注文手続き中の自分の引当は除いて判定する
        mockMvc.perform(get("/api/store/carts/" + cartId))
                .andExpect(jsonPath("$.items[0].purchasable").value(true))
                .andExpect(jsonPath("$.orderable").value(true));
    }

    @Test
    @DisplayName("AC-85 注文手続きの表示：税率ごとの消費税額を返す（税率の高い順。送料は 10% の対象）")
    void showsTaxAmountPerRate() throws Exception {
        var reduced = data.taxCategory("軽減", "8");
        var standardVariant = stockedVariant(1);
        var reducedProduct = data.product(reduced, "軽減の商品", 1000).publish();
        var reducedVariant = reducedProduct.variantIds.getFirst();
        inventory.stock(reducedVariant, reducedProduct.id, 1);
        orders.add(cartId, standardVariant, 1);
        orders.add(cartId, reducedVariant, 1);

        var checkoutId = startCheckout(cartId);

        // 10%：1,100 円 + 送料 1,000 円 = 2,100 円 → 2,100 × 10 ÷ 110 = 190.9 → 190 円
        // 8%：1,080 円 → 1,080 × 8 ÷ 108 = 80 円
        mockMvc.perform(get("/api/store/checkouts/" + checkoutId))
                .andExpect(jsonPath("$.amounts.itemsTotal").value(2180))
                .andExpect(jsonPath("$.amounts.total").value(3180))
                .andExpect(jsonPath("$.amounts.taxes.length()").value(2))
                .andExpect(jsonPath("$.amounts.taxes[0].ratePercent").value(10))
                .andExpect(jsonPath("$.amounts.taxes[0].taxableAmount").value(2100))
                .andExpect(jsonPath("$.amounts.taxes[0].taxAmount").value(190))
                .andExpect(jsonPath("$.amounts.taxes[1].ratePercent").value(8))
                .andExpect(jsonPath("$.amounts.taxes[1].taxableAmount").value(1080))
                .andExpect(jsonPath("$.amounts.taxes[1].taxAmount").value(80));
    }

    @Test
    @DisplayName("存在しないカート・注文手続きは 404")
    void returnsNotFound() throws Exception {
        mockMvc.perform(post("/api/store/carts/" + UUID.randomUUID() + "/checkouts")).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/store/checkouts/" + UUID.randomUUID())).andExpect(status().isNotFound());
    }

    // ============================== 補助 ==============================

    /**
     * 注文手続きに進み、201 と Location を確認して、注文手続き ID を返す
     */
    private UUID startCheckout(UUID cart) throws Exception {
        var body = mockMvc.perform(startCheckoutRequest(cart))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andReturn().getResponse().getContentAsString();
        return TestJson.createdId(body);
    }

    /**
     * 公開した商品（1,000 円・10%）のバリアントを作り、在庫数を設定して、バリアント ID を返す
     */
    private UUID stockedVariant(int quantity) throws Exception {
        var product = data.product(standard, "商品", 1000).publish();
        var variantId = product.variantIds.getFirst();
        inventory.stock(variantId, product.id, quantity);
        return variantId;
    }
}
