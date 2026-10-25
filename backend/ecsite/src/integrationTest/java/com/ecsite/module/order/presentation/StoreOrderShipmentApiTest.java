package com.ecsite.module.order.presentation;

import com.ecsite.CatalogTestData;
import com.ecsite.IntegrationTest;
import com.ecsite.InventoryTestData;
import com.ecsite.MutableClock;
import com.ecsite.OrderTestData;
import com.ecsite.ShippingTestData;
import com.ecsite.ShippingTaxCategory;
import com.ecsite.TestClockConfig;
import com.ecsite.module.inventory.domain.stock.port.StockItemRepository;
import com.ecsite.module.order.application.payment.OrderPaymentService;
import com.ecsite.module.payment.infrastructure.mock.TestPaymentGateway;
import com.ecsite.module.shipping.domain.Carrier;
import com.ecsite.module.shipping.infrastructure.mock.MockCarrier;
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

import java.time.Instant;
import java.util.UUID;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 店舗の注文の詳細と一覧の、出荷の表示の受け入れテスト（T9311、AC-171、179、180、196）。<br />
 * 注文と出荷は既定の会員（{@link com.ecsite.TestSecurityConfig}）で作る。テストごとにロールバックする。
 */
@IntegrationTest
@AutoConfigureMockMvc
@Import(TestClockConfig.class)
@Transactional
class StoreOrderShipmentApiTest {

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

    private InventoryTestData inventory;

    @Autowired
    DSLContext dsl;

    /**
     * 受付のまま（出荷待ち）の注文
     */
    private UUID accepted;

    /**
     * 出荷済みの注文
     */
    private UUID shipped;

    @BeforeEach
    void setUp() throws Exception {
        inventory = new InventoryTestData(stockItemRepository, NOW);
        clock.setInstant(NOW);
        gateway.reset();
        ShippingTaxCategory.ensure(dsl);
        var data = new CatalogTestData(mockMvc, clock);
        var product = data.product(data.taxCategory("標準", "10"), "商品", 1000).publish();
        var variantId = product.variantIds.getFirst();
        inventory.stock(variantId, product.id, 10);
        var orders = new OrderTestData(mockMvc, gateway, orderPaymentService);
        accepted = orders.placeOrder(variantId, 1);
        shipped = orders.placeOrder(variantId, 1);
        mockMvc.perform(post("/api/admin/orders/" + shipped + "/shipment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"carrier": "YAMATO", "trackingNumber": "1234-5678-9012"}"""))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("AC-179 注文詳細に、出荷済みの状態と、配送状態・配送業者・送り状番号を返す。配送状態は通知で進む")
    void showsShipmentInDetail() throws Exception {
        mockMvc.perform(get("/api/store/orders/" + shipped))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SHIPPED"))
                .andExpect(jsonPath("$.shipment.carrier").value("YAMATO"))
                .andExpect(jsonPath("$.shipment.trackingNumber").value(TRACKING_NUMBER))
                .andExpect(jsonPath("$.shipment.status").value("SHIPPED"));

        notifyStatus(Carrier.YAMATO, "配達中");

        mockMvc.perform(get("/api/store/orders/" + shipped)).andExpect(jsonPath("$.shipment.status").value("IN_TRANSIT"));
        mockMvc.perform(get("/api/store/orders/" + accepted)).andExpect(jsonPath("$.shipment").value(nullValue()));
    }

    @Test
    @DisplayName("AC-171 出荷済みの注文は、キャンセルを申請できない")
    void cannotRequestCancellation() throws Exception {
        mockMvc.perform(get("/api/store/orders/" + shipped)).andExpect(jsonPath("$.actions.requestCancellation").value(false));
        mockMvc.perform(put("/api/store/orders/" + shipped + "/cancellation-request")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("CANCELLATION_NOT_REQUESTABLE"));
    }

    @Test
    @DisplayName("AC-180 注文一覧の出荷済みの注文に、配送状態を返す。出荷していなければ null")
    void showsShipmentStatusInList() throws Exception {
        mockMvc.perform(get("/api/store/me/orders"))
                .andExpect(jsonPath("$.items[?(@.id == '%s')].status".formatted(shipped)).value(contains("SHIPPED")))
                .andExpect(jsonPath("$.items[?(@.id == '%s')].shipmentStatus".formatted(shipped)).value(contains("SHIPPED")))
                .andExpect(jsonPath("$.items[?(@.id == '%s')].shipmentStatus".formatted(accepted)).value(contains(nullValue())));
    }

    @Test
    @DisplayName("AC-196 返送された注文を運営者がキャンセルすると、注文詳細と一覧はキャンセルを示す")
    void showsCancelledAfterReturn() throws Exception {
        notifyStatus(Carrier.YAMATO, "返品");
        mockMvc.perform(put("/api/admin/orders/" + shipped + "/cancel")
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"reason": "返送された", "restock": true}"""))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/store/orders/" + shipped))
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancellation.kind").value("OPERATOR"));
        mockMvc.perform(get("/api/store/me/orders"))
                .andExpect(jsonPath("$.items[?(@.id == '%s')].status".formatted(shipped)).value(contains("CANCELLED")));
    }

    // ============================== 補助 ==============================

    /**
     * mock の物流サービスから、配送状態の通知をエンドポイントへ送る
     */
    private void notifyStatus(Carrier carrier, String statusName) throws Exception {
        new ShippingTestData(mockMvc, mockCarrier).notifyStatus(carrier, TRACKING_NUMBER, statusName);
    }
}
