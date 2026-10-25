package com.ecsite.module.order.presentation;

import com.ecsite.AsyncEvents;
import com.ecsite.CatalogTestData;
import com.ecsite.IntegrationTest;
import com.ecsite.InventoryTestData;
import com.ecsite.MutableClock;
import com.ecsite.OrderTestData;
import com.ecsite.ShippingTaxCategory;
import com.ecsite.TestClockConfig;
import com.ecsite.TestTokens;
import com.ecsite.module.inventory.domain.stock.port.StockItemRepository;
import com.ecsite.module.order.application.payment.OrderPaymentService;
import com.ecsite.module.order.application.port.StockDirectory;
import com.ecsite.module.payment.event.PaymentAuthorized;
import com.ecsite.module.payment.infrastructure.mock.TestPaymentGateway;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * キャンセルと在庫の戻しの一貫性の受け入れテスト（T2403、AC-104、NFR-2）。<br />
 * 在庫の戻しを失敗させ、注文もキャンセルにならないこと（トランザクションごと取り消されること）を確認する。
 * 取り消しを見るためにコミットが必要だが、作ったデータは消さない。税区分の名称は、ほかのテストと重ならない名前にする。
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

    private InventoryTestData inventory;

    @Autowired
    DSLContext dsl;

    @Autowired
    AsyncEvents asyncEvents;

    @MockitoSpyBean
    StockDirectory stockDirectory;

    private UUID taxCategoryId;

    private CatalogTestData.TestProduct product;

    private UUID orderId;

    /**
     * このテストの会員（コミットするので、他のテストとカートを共有しないよう、テストごとに変える）
     */
    private TestTokens.Builder owner;

    @BeforeEach
    void setUp() throws Exception {
        owner = TestTokens.member().subject(UUID.randomUUID());
        inventory = new InventoryTestData(stockItemRepository, NOW);
        clock.setInstant(NOW);
        ShippingTaxCategory.ensure(dsl);
        var data = new CatalogTestData(mockMvc, clock);
        taxCategoryId = data.taxCategory("一貫性-" + UUID.randomUUID().toString().substring(0, 8), "10");
        product = data.product(taxCategoryId, "一貫性の商品", 1000).publish();
        var variantId = product.variantIds.getFirst();
        inventory.stock(variantId, product.id, 5);
        orderId = new OrderTestData(mockMvc, gateway, orderPaymentService, owner.bearer()).placeOrder(variantId, 2);
        awaitAuthorizedHandled();
    }

    /**
     * 与信の成功のイベントのリスナーが終わるのを待つ。<br />
     * このテストはコミットするので、支払うとリスナーが非同期で注文を受け付け直す。終わる前にキャンセルすると、
     * 楽観ロックで衝突し、在庫の戻しの失敗（500）ではなく衝突（409）になることがある。
     */
    private void awaitAuthorizedHandled() {
        asyncEvents.awaitHandled(event -> event instanceof PaymentAuthorized authorized
                && authorized.orderId().equals(orderId));
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

        mockMvc.perform(get("/api/store/orders/" + orderId).with(owner.bearer())).andExpect(jsonPath("$.status").value("ACCEPTED"));
        mockMvc.perform(get("/api/admin/inventory/variants/" + product.variantIds.getFirst()))
                .andExpect(jsonPath("$.quantity").value(3));
    }
}
