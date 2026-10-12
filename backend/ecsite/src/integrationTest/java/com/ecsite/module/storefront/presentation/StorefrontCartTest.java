package com.ecsite.module.storefront.presentation;

import com.ecsite.CatalogTestData;
import com.ecsite.IntegrationTest;
import com.ecsite.MutableClock;
import com.ecsite.ShippingTaxCategory;
import com.ecsite.TestClockConfig;
import com.ecsite.TestTokens;
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
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * カートの表示の受け入れテスト（T2304、AC-77、79〜81）。<br />
 * 商品は B 側の API で作り、在庫は在庫モジュールのリポジトリで直接作る。テストごとにロールバックする。
 */
@IntegrationTest
@AutoConfigureMockMvc
@Import(TestClockConfig.class)
@Transactional
class StorefrontCartTest {

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
    DSLContext dsl;

    private CatalogTestData data;

    private UUID standard;

    private UUID reduced;

    private UUID cartId;

    @BeforeEach
    void setUp() throws Exception {
        clock.setInstant(NOW);
        ShippingTaxCategory.ensure(dsl);
        data = new CatalogTestData(mockMvc, clock);
        standard = data.taxCategory("標準", "10");
        reduced = data.taxCategory("軽減", "8");
        var body = mockMvc.perform(post("/api/store/carts")).andReturn().getResponse().getContentAsString();
        cartId = UUID.fromString(JsonPath.read(body, "$.id"));
    }

    @Test
    @DisplayName("AC-77 品目ごとの商品名・軸の値・単価・数量・小計と、商品の合計・送料・総額・税率ごとの消費税額が返る")
    void returnsCartWithAmounts() throws Exception {
        var shirt = data.product(standard, "T シャツ", 2700, "色", "サイズ").variant("白", "M").publish();
        var tea = data.product(reduced, "お茶", 1234).publish();
        stock(shirt.variantIds.getFirst(), 5);
        stock(tea.variantIds.getFirst(), 5);
        add(shirt.variantIds.getFirst(), 1);
        add(tea.variantIds.getFirst(), 1);

        mockMvc.perform(get("/api/store/carts/" + cartId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(cartId.toString()))
                .andExpect(jsonPath("$.items[*].variantId", contains(shirt.variantIds.getFirst().toString(), tea.variantIds.getFirst().toString())))
                .andExpect(jsonPath("$.items[0].productId").value(shirt.id.toString()))
                .andExpect(jsonPath("$.items[0].productName").value("T シャツ"))
                .andExpect(jsonPath("$.items[0].axisValues[*].name", contains("色", "サイズ")))
                .andExpect(jsonPath("$.items[0].axisValues[*].value", contains("白", "M")))
                .andExpect(jsonPath("$.items[0].imageUrl").value(nullValue()))
                .andExpect(jsonPath("$.items[0].unitPrice.inclTax").value(2970))
                .andExpect(jsonPath("$.items[0].unitPrice.exclTax").value(2700))
                .andExpect(jsonPath("$.items[0].quantity").value(1))
                .andExpect(jsonPath("$.items[0].subtotal").value(2970))
                .andExpect(jsonPath("$.items[0].purchasable").value(true))
                .andExpect(jsonPath("$.items[0].unpurchasableReason").value(nullValue()))
                .andExpect(jsonPath("$.amounts.itemsTotal").value(2970 + 1332))
                .andExpect(jsonPath("$.amounts.shippingFee").value(1000))
                .andExpect(jsonPath("$.amounts.total").value(2970 + 1332 + 1000))
                // spec の BR-8 の例：10% の対象 3,970 円 → 360 円、8% の対象 1,332 円 → 98 円
                .andExpect(jsonPath("$.amounts.taxes[*].ratePercent", contains(10, 8)))
                .andExpect(jsonPath("$.amounts.taxes[*].taxableAmount", contains(3970, 1332)))
                .andExpect(jsonPath("$.amounts.taxes[*].taxAmount", contains(360, 98)))
                .andExpect(jsonPath("$.orderable").value(true));
    }

    @Test
    @DisplayName("AC-79 単価は常に現在の価格で表示する")
    void showsCurrentPrice() throws Exception {
        var product = data.product(standard, "商品", 1000).publish();
        stock(product.variantIds.getFirst(), 5);
        add(product.variantIds.getFirst(), 2);

        product.changePrice(standard, 2000);

        mockMvc.perform(get("/api/store/carts/" + cartId))
                .andExpect(jsonPath("$.items[0].unitPrice.inclTax").value(2200))
                .andExpect(jsonPath("$.items[0].subtotal").value(4400));
    }

    @Test
    @DisplayName("AC-80 購入できなくなった品目は理由とともに示し、合計に含めない。注文はできない")
    void marksUnpurchasableItems() throws Exception {
        var hidden = data.product(standard, "非公開にする", 1000).publish();
        var suspended = data.product(standard, "販売停止にする", 1000, "色").variant("白").publish();
        var soldOut = data.product(standard, "売り切れる", 1000).publish();
        var short_ = data.product(standard, "足りなくなる", 1000).publish();
        var ok = data.product(standard, "買える", 1000).publish();
        for (var product : new CatalogTestData.TestProduct[]{hidden, suspended, soldOut, short_, ok}) {
            stock(product.variantIds.getFirst(), 3);
            add(product.variantIds.getFirst(), 2);
        }
        hidden.unpublish();
        suspended.suspend(0);
        adjust(soldOut.variantIds.getFirst(), -3);
        adjust(short_.variantIds.getFirst(), -2);

        mockMvc.perform(get("/api/store/carts/" + cartId))
                .andExpect(jsonPath("$.items[*].purchasable", contains(false, false, false, false, true)))
                .andExpect(jsonPath("$.items[*].unpurchasableReason",
                        contains("NOT_SHOWN", "SALES_SUSPENDED", "OUT_OF_STOCK", "INSUFFICIENT_STOCK", null)))
                .andExpect(jsonPath("$.amounts.itemsTotal").value(2200))
                .andExpect(jsonPath("$.amounts.total").value(3200))
                .andExpect(jsonPath("$.orderable").value(false));
    }

    @Test
    @DisplayName("AC-81 最後の変更から 30 日たったカートは空として返す。空のカートは送料 0 で注文できない")
    void returnsExpiredCartAsEmpty() throws Exception {
        var product = data.product(standard, "商品", 1000).publish();
        stock(product.variantIds.getFirst(), 5);
        var changedAt = clock.instant();
        add(product.variantIds.getFirst(), 1);

        clock.setInstant(changedAt.plus(Duration.ofDays(30)));

        mockMvc.perform(get("/api/store/carts/" + cartId))
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.amounts.shippingFee").value(0))
                .andExpect(jsonPath("$.amounts.total").value(0))
                .andExpect(jsonPath("$.amounts.taxes").isEmpty())
                .andExpect(jsonPath("$.orderable").value(false));
    }

    @Test
    @DisplayName("存在しないカートは 404")
    void returnsNotFoundForUnknownCart() throws Exception {
        mockMvc.perform(get("/api/store/carts/" + UUID.randomUUID())).andExpect(status().isNotFound());
    }

    // ============================== 補助 ==============================

    /**
     * カートに入れ、204 を確かめる
     */
    private void add(UUID variantId, int quantity) throws Exception {
        mockMvc.perform(post("/api/store/carts/" + cartId + "/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"variantId": "%s", "quantity": %d}""".formatted(variantId, quantity)))
                .andExpect(status().isNoContent());
    }

    /**
     * バリアントの在庫を作り、在庫数を設定する（入荷として調整する）
     */
    private void stock(UUID variantId, int quantity) {
        stockItemRepository.createIfAbsent(variantId, UUID.randomUUID());
        adjust(variantId, quantity);
    }

    /**
     * 在庫数を増減する
     */
    private void adjust(UUID variantId, int delta) {
        var reason = delta > 0 ? StockAdjustmentReason.ARRIVAL : StockAdjustmentReason.DAMAGE_OR_LOSS;
        stockItemRepository.adjust(variantId, StockAdjustment.create(delta, reason, null, TestTokens.OPERATOR, clock.instant()));
    }
}
