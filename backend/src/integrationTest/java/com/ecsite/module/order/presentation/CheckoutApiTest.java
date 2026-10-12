package com.ecsite.module.order.presentation;

import com.ecsite.CatalogTestData;
import com.ecsite.IntegrationTest;
import com.ecsite.MutableClock;
import com.ecsite.ShippingTaxCategory;
import com.ecsite.TestClockConfig;
import com.ecsite.module.inventory.api.StockReservationApi;
import com.ecsite.module.inventory.domain.stock.StockAdjustment;
import com.ecsite.module.inventory.domain.stock.StockAdjustmentReason;
import com.ecsite.module.inventory.domain.stock.port.StockItemRepository;
import com.ecsite.module.order.application.CheckoutService;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 注文手続きの受け入れテスト（T2311、AC-82、84、85、88）。<br />
 * カート変更のイベントはコミット後に非同期で届くので、ここではリスナーが呼ぶ引当の解除を直接呼んで確かめる。テストごとにロールバックする。
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

    @Autowired
    StockReservationApi stockReservationApi;

    @Autowired
    CheckoutService checkoutService;

    @Autowired
    DSLContext dsl;

    private CatalogTestData data;

    private UUID standard;

    private UUID cartId;

    @BeforeEach
    void setUp() throws Exception {
        clock.setInstant(NOW);
        ShippingTaxCategory.ensure(dsl);
        data = new CatalogTestData(mockMvc, clock);
        standard = data.taxCategory("標準", "10");
        cartId = createCart();
    }

    @Test
    @DisplayName("AC-82 注文手続きに進むと、全品目をカートの数量だけ 15 分間引き当てる")
    void reservesAllItems() throws Exception {
        var shirt = stockedVariant(3);
        var cap = stockedVariant(5);
        add(cartId, shirt, 2);
        add(cartId, cap, 1);

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
        add(cartId, plenty, 1);
        add(cartId, scarce, 2);
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
        stockItemRepository.createIfAbsent(product.variantIds.getFirst(), product.id);
        stockItemRepository.adjust(product.variantIds.getFirst(),
                StockAdjustment.create(3, StockAdjustmentReason.ARRIVAL, null, clock.instant()));
        add(cartId, product.variantIds.getFirst(), 1);
        product.suspend(0);

        mockMvc.perform(post("/api/store/carts/" + cartId + "/checkouts"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("CART_NOT_ORDERABLE"));
    }

    @Test
    @DisplayName("AC-84 進み直すと、前の注文手続きの引当を解いて、その時点のカートで引き当て直す")
    void replacesReservationOnRestart() throws Exception {
        var variant = stockedVariant(3);
        add(cartId, variant, 2);
        startCheckout(cartId);

        clock.setInstant(clock.instant().plus(Duration.ofMinutes(10)));
        var second = startCheckout(cartId);

        assertThat(stockReservationApi.sellableQuantities(List.of(variant), null)).containsEntry(variant, 1);
        // 期限も 15 分に戻る
        mockMvc.perform(get("/api/store/checkouts/" + second))
                .andExpect(jsonPath("$.expiresAt").value(clock.instant().plus(Duration.ofMinutes(15)).toString()));
    }

    @Test
    @DisplayName("AC-84 カートを変更すると（カート変更のイベント）、そのカートの注文手続きの引当が解かれる")
    void releasesReservationOnCartChange() throws Exception {
        var variant = stockedVariant(3);
        add(cartId, variant, 2);
        startCheckout(cartId);

        checkoutService.releaseReservations(cartId);

        assertThat(stockReservationApi.sellableQuantities(List.of(variant), null)).containsEntry(variant, 3);
    }

    @Test
    @DisplayName("AC-85 注文手続きの表示：内容と金額を返す。自分の引当は販売可能数から除くので、購入できる")
    void showsCheckoutExcludingOwnReservation() throws Exception {
        var variant = stockedVariant(1);
        add(cartId, variant, 1);

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
    @DisplayName("存在しないカート・注文手続きは 404")
    void returnsNotFound() throws Exception {
        mockMvc.perform(post("/api/store/carts/" + UUID.randomUUID() + "/checkouts")).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/store/checkouts/" + UUID.randomUUID())).andExpect(status().isNotFound());
    }

    // ============================== 補助 ==============================

    /**
     * 既定の会員（TestSecurityConfig）の会員のカートの ID を返す（注文手続きは会員のカートからだけ始められる。AC-125）
     */
    private UUID createCart() throws Exception {
        var body = mockMvc.perform(get("/api/store/me/cart")).andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(body, "$.cartId"));
    }

    /**
     * 注文手続きに進み、201 と Location を確かめて、注文手続き ID を返す
     */
    private UUID startCheckout(UUID cart) throws Exception {
        var body = mockMvc.perform(post("/api/store/carts/" + cart + "/checkouts"))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(body, "$.id"));
    }

    /**
     * カートに入れ、204 を確かめる
     */
    private ResultActions add(UUID cart, UUID variantId, int quantity) throws Exception {
        return mockMvc.perform(post("/api/store/carts/" + cart + "/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"variantId": "%s", "quantity": %d}""".formatted(variantId, quantity)))
                .andExpect(status().isNoContent());
    }

    /**
     * 公開した商品（1,000 円・10%）のバリアントを作り、在庫数を設定して、バリアント ID を返す
     */
    private UUID stockedVariant(int quantity) throws Exception {
        var product = data.product(standard, "商品", 1000).publish();
        var variantId = product.variantIds.getFirst();
        stockItemRepository.createIfAbsent(variantId, product.id);
        stockItemRepository.adjust(variantId,
                StockAdjustment.create(quantity, StockAdjustmentReason.ARRIVAL, null, clock.instant()));
        return variantId;
    }
}
