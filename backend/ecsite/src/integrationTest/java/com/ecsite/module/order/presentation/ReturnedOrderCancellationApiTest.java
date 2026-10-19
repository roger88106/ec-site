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
import com.ecsite.module.payment.infrastructure.mock.TestPaymentGateway;
import com.ecsite.module.shipping.domain.Carrier;
import com.ecsite.module.shipping.infrastructure.mock.MockCarrier;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 返送された注文のキャンセルの受け入れテスト（T9307、AC-171、190〜193）。<br />
 * 配送状態は、mock の物流サービスの署名付きの通知を受け口に送って進める。返金はコミットの後に行うので、
 * ロールバックするこのテストでは「返金待ち」までを確かめる（返金済みになるのは決済の統合テストと E2E）。
 */
@IntegrationTest
@AutoConfigureMockMvc
@Import(TestClockConfig.class)
@Transactional
class ReturnedOrderCancellationApiTest {

    /**
     * テストの現在時刻
     */
    private static final Instant NOW = Instant.parse("2026-10-07T15:00:00Z");

    /**
     * 送り状番号
     */
    private static final String TRACKING_NUMBER = "123456789012";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    TestPaymentGateway gateway;

    @Autowired
    MockCarrier mockCarrier;

    @Autowired
    OrderPaymentService orderPaymentService;

    @Autowired
    MutableClock clock;

    @Autowired
    StockItemRepository stockItemRepository;

    @Autowired
    DSLContext dsl;

    private UUID variantId;

    private UUID orderId;

    @BeforeEach
    void setUp() throws Exception {
        clock.setInstant(NOW);
        gateway.reset();
        ShippingTaxCategory.ensure(dsl);
        var data = new CatalogTestData(mockMvc, clock);
        var product = data.product(data.taxCategory("標準", "10"), "商品", 1000).publish();
        variantId = product.variantIds.getFirst();
        stockItemRepository.createIfAbsent(variantId, product.id);
        stockItemRepository.adjust(variantId, StockAdjustment.create(5, StockAdjustmentReason.ARRIVAL, null, TestTokens.OPERATOR, NOW));
        orderId = new OrderTestData(mockMvc, gateway, orderPaymentService).placeOrder(variantId, 2);
        mockMvc.perform(post("/api/admin/orders/" + orderId + "/shipment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"carrier": "YAMATO", "trackingNumber": "%s"}""".formatted(TRACKING_NUMBER)))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("AC-190、192、268 返送の注文をキャンセルすると、キャンセルになり、支払は返金待ちになる。キャンセルできる旗と、在庫に戻すかを聞く旗が立つ")
    void cancelsReturnedOrder() throws Exception {
        notifyStatus("配達中");
        notifyStatus("返品");
        mockMvc.perform(get("/api/admin/orders/" + orderId))
                .andExpect(jsonPath("$.shipment.status").value("RETURNED"))
                .andExpect(jsonPath("$.actions.cancel").value(true))
                .andExpect(jsonPath("$.actions.cancelAsksRestock").value(true));

        cancel("返送された", true).andExpect(status().isNoContent());

        mockMvc.perform(get("/api/admin/orders/" + orderId))
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancellation.reason").value("返送された"))
                .andExpect(jsonPath("$.payment.status").value("REFUND_PENDING"))
                .andExpect(jsonPath("$.shipment.status").value("RETURNED"));
    }

    @Test
    @DisplayName("AC-191 在庫に戻すときは明細の数量だけ在庫数が増え、記録に残る")
    void restocks() throws Exception {
        notifyStatus("返品");
        expectQuantity(3);

        cancel("返送された", true).andExpect(status().isNoContent());

        expectQuantity(5);
        mockMvc.perform(get("/api/admin/orders/" + orderId))
                .andExpect(jsonPath("$.cancellation.restocked").value(true));
    }

    @Test
    @DisplayName("AC-191 在庫に戻さないときは在庫数が変わらず、記録に残る")
    void doesNotRestock() throws Exception {
        notifyStatus("返品");

        cancel("返送された（破損）", false).andExpect(status().isNoContent());

        expectQuantity(3);
        mockMvc.perform(get("/api/admin/orders/" + orderId))
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancellation.restocked").value(false));
    }

    @Test
    @DisplayName("AC-191 返送の注文のキャンセルで、在庫に戻すかを指定しなければ 400")
    void requiresRestockChoice() throws Exception {
        notifyStatus("返品");

        mockMvc.perform(put("/api/admin/orders/" + orderId + "/cancel")
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"reason": "返送された"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("RESTOCK_CHOICE_REQUIRED"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "配達中", "配達完了"})
    @DisplayName("AC-171、268 返送でない出荷済みの注文（出荷済み・配送中・配達完了）は 409 で、キャンセルできる旗も在庫に戻すかを聞く旗も立たない")
    void rejectsNotReturned(String statusName) throws Exception {
        if (!statusName.isEmpty()) {
            notifyStatus(statusName);
        }
        mockMvc.perform(get("/api/admin/orders/" + orderId))
                .andExpect(jsonPath("$.actions.cancel").value(false))
                .andExpect(jsonPath("$.actions.cancelAsksRestock").value(false));

        cancel("理由", true).andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("SHIPPED_ORDER_NOT_RETURNED"));

        mockMvc.perform(get("/api/admin/orders/" + orderId)).andExpect(jsonPath("$.status").value("SHIPPED"));
        expectQuantity(3);
    }

    @Test
    @DisplayName("AC-193 決済サービスが返金に失敗する状態でも、キャンセルは返金の結果を待たずに成立する")
    void doesNotWaitForRefund() throws Exception {
        notifyStatus("返品");
        gateway.failNextRefunds(1);

        cancel("返送された", true).andExpect(status().isNoContent());

        mockMvc.perform(get("/api/admin/orders/" + orderId))
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.payment.status").value("REFUND_PENDING"));
    }

    // ============================== 補助 ==============================

    /**
     * mock の物流サービスから、配送状態の通知を受け口へ送る
     */
    private void notifyStatus(String statusName) throws Exception {
        var notification = mockCarrier.notification(Carrier.YAMATO, TRACKING_NUMBER, statusName);
        mockMvc.perform(post("/api/webhooks/carrier")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Carrier-Signature", notification.signature())
                        .content(notification.body()))
                .andExpect(status().isOk());
    }

    /**
     * 運営者のキャンセルを送る
     */
    private ResultActions cancel(String reason, boolean restock) throws Exception {
        return mockMvc.perform(put("/api/admin/orders/" + orderId + "/cancel")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"reason": "%s", "restock": %s}""".formatted(reason, restock)));
    }

    /**
     * 在庫数を確かめる
     */
    private void expectQuantity(int quantity) throws Exception {
        mockMvc.perform(get("/api/admin/inventory/variants/" + variantId)).andExpect(jsonPath("$.quantity").value(quantity));
    }
}
