package com.ecsite.module.order.presentation;

import com.ecsite.CatalogTestData;
import com.ecsite.IntegrationTest;
import com.ecsite.MutableClock;
import com.ecsite.OrderTestData;
import com.ecsite.ShippingTaxCategory;
import com.ecsite.TestClockConfig;
import com.ecsite.TestTokens;
import com.ecsite.module.inventory.domain.stock.StockAdjustment;
import com.ecsite.module.inventory.domain.stock.StockAdjustmentReason;
import com.ecsite.module.inventory.domain.stock.port.StockItemRepository;
import com.ecsite.module.order.application.payment.OrderPaymentService;
import com.ecsite.module.order.application.port.StockDirectory;
import com.ecsite.module.payment.event.PaymentAuthorized;
import com.ecsite.module.payment.infrastructure.mock.TestPaymentGateway;
import org.jooq.DSLContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.modulith.events.core.EventPublicationRegistry;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static com.ecsite.AsyncEvents.TIMEOUT;
import static com.ecsite.jooq.cart.Tables.CART_;
import static com.ecsite.jooq.catalog.Tables.PRODUCT;
import static com.ecsite.jooq.inventory.Tables.STOCK_ITEM;
import static com.ecsite.jooq.ordering.Tables.CHECKOUT;
import static com.ecsite.jooq.ordering.Tables.SALES_ORDER;
import static com.ecsite.jooq.tax.Tables.TAX_CATEGORY;
import static com.ecsite.jooq.tax.Tables.TAX_RATE;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * キャンセルと在庫の戻しの一貫性の受け入れテスト（T2403、AC-104、NFR-2）。<br />
 * 在庫の戻しを失敗させ、注文もキャンセルにならないこと（トランザクションごと取り消されること）を確かめる。
 * 取り消しを見るためにコミットが必要なので、作ったデータを後で消す。税区分の名称は、ほかのテストと重ならない名前にする。
 */
@IntegrationTest
@AutoConfigureMockMvc
@Import(TestClockConfig.class)
class CancellationAtomicityTest {

    /**
     * テストの現在時刻
     */
    private static final Instant NOW = Instant.parse("2026-10-07T15:00:00Z");

    @Autowired
    MockMvc mockMvc;

    @Autowired
    TestPaymentGateway gateway;

    @Autowired
    OrderPaymentService orderPaymentService;

    @Autowired
    MutableClock clock;

    @Autowired
    StockItemRepository stockItemRepository;

    @Autowired
    DSLContext dsl;

    @Autowired
    EventPublicationRegistry publicationRegistry;

    @MockitoSpyBean
    StockDirectory stockDirectory;

    private UUID taxCategoryId;

    private CatalogTestData.TestProduct product;

    private UUID orderId;

    @BeforeEach
    void setUp() throws Exception {
        clock.setInstant(NOW);
        ShippingTaxCategory.ensure(dsl);
        var data = new CatalogTestData(mockMvc, clock);
        taxCategoryId = data.taxCategory("一貫性-" + UUID.randomUUID().toString().substring(0, 8), "10");
        product = data.product(taxCategoryId, "一貫性の商品", 1000).publish();
        var variantId = product.variantIds.getFirst();
        stockItemRepository.createIfAbsent(variantId, product.id);
        stockItemRepository.adjust(variantId, StockAdjustment.create(5, StockAdjustmentReason.ARRIVAL, null, TestTokens.OPERATOR, NOW));
        orderId = new OrderTestData(mockMvc, gateway, orderPaymentService).placeOrder(variantId, 2);
        awaitAuthorizedHandled();
    }

    @AfterEach
    void cleanUp() {
        var checkoutId = dsl.select(SALES_ORDER.CHECKOUT_ID).from(SALES_ORDER).where(SALES_ORDER.ID.eq(orderId)).fetchOne(SALES_ORDER.CHECKOUT_ID);
        var cartId = dsl.select(CHECKOUT.CART_ID).from(CHECKOUT).where(CHECKOUT.ID.eq(checkoutId)).fetchOne(CHECKOUT.CART_ID);
        dsl.deleteFrom(SALES_ORDER).where(SALES_ORDER.ID.eq(orderId)).execute();
        dsl.deleteFrom(CHECKOUT).where(CHECKOUT.ID.eq(checkoutId)).execute();
        dsl.deleteFrom(CART_).where(CART_.ID.eq(cartId)).execute();
        dsl.deleteFrom(STOCK_ITEM).where(STOCK_ITEM.PRODUCT_ID.eq(product.id)).execute();
        dsl.deleteFrom(PRODUCT).where(PRODUCT.ID.eq(product.id)).execute();
        dsl.deleteFrom(TAX_RATE).where(TAX_RATE.TAX_CATEGORY_ID.eq(taxCategoryId)).execute();
        dsl.deleteFrom(TAX_CATEGORY).where(TAX_CATEGORY.ID.eq(taxCategoryId)).execute();
    }

    /**
     * 与信の成功のイベントのリスナーが終わるのを待つ。<br />
     * このテストはコミットするので、支払うとリスナーが非同期で注文を受け付け直す。終わる前にキャンセルすると、
     * 楽観ロックで衝突し、在庫の戻しの失敗（500）ではなく衝突（409）になることがある。
     */
    private void awaitAuthorizedHandled() {
        await().atMost(TIMEOUT).until(() -> publicationRegistry.findIncompletePublications().stream()
                .noneMatch(publication -> publication.getEvent() instanceof PaymentAuthorized event
                        && event.orderId().equals(orderId)));
    }

    @Test
    @DisplayName("AC-104 在庫の戻しに失敗すると、注文もキャンセルにならず、在庫も変わらない")
    void keepsOrderWhenRestoreFails() throws Exception {
        doThrow(new IllegalStateException("在庫の戻しの失敗")).when(stockDirectory).restore(any(), any(), any());

        mockMvc.perform(put("/api/admin/orders/" + orderId + "/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason": "在庫の不備"}"""))
                .andExpect(status().isInternalServerError());

        mockMvc.perform(get("/api/store/orders/" + orderId)).andExpect(jsonPath("$.status").value("ACCEPTED"));
        mockMvc.perform(get("/api/admin/inventory/variants/" + product.variantIds.getFirst()))
                .andExpect(jsonPath("$.quantity").value(3));
    }
}
