package com.ecsite.module.order.presentation;

import com.ecsite.module.inventory.domain.stock.port.StockItemRepository;
import com.ecsite.module.payment.infrastructure.mock.TestPaymentGateway;
import com.ecsite.module.shipping.domain.Carrier;
import com.ecsite.module.shipping.infrastructure.mock.MockCarrier;
import com.ecsite.support.MutableClock;
import com.ecsite.support.config.IntegrationTest;
import com.ecsite.support.data.CatalogTestData;
import com.ecsite.support.data.InventoryTestData;
import com.ecsite.support.data.OrderTestData;
import com.ecsite.support.data.ShippingTaxCategory;
import com.ecsite.support.data.ShippingTestData;
import com.ecsite.support.security.TestSecurityConfig;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 店舗の注文の詳細と一覧の、出荷の表示の受け入れテスト
 *
 * <p>出荷した注文の詳細と一覧の配送状態、出荷済みの注文のキャンセルの申請、返送の後のキャンセルの表示を確認する。
 * 出荷の登録そのものは {@code ShipmentApiTest} が確認する。
 *
 * <p>注文と出荷は既定の会員（{@link TestSecurityConfig}）で作る。一覧は、自分の注文の行を ID で選んで確認する。
 * テストごとにロールバックする。
 */
@IntegrationTest
@Transactional
class StoreOrderShipmentApiTest {

    // ============================== 定数 ==============================

    /**
     * テストの現在時刻
     *
     * <p>日本時間の 2026-10-08 00:00。
     */
    private static final Instant NOW = Instant.parse("2026-10-07T15:00:00Z");

    /**
     * 送り状番号
     */
    private static final String TRACKING_NUMBER = "123456789012";

    /**
     * ハイフン付きの送り状番号（登録するとハイフンを除いた {@link #TRACKING_NUMBER} になる）
     */
    private static final String TRACKING_NUMBER_WITH_HYPHENS = "1234-5678-9012";

    // ============================== Bean ==============================

    @Autowired
    MockMvc mockMvc;

    @Autowired
    TestPaymentGateway gateway;

    @Autowired
    MockCarrier mockCarrier;

    @Autowired
    ApplicationContext applicationContext;

    @Autowired
    MutableClock clock;

    @Autowired
    StockItemRepository stockItemRepository;

    /**
     * 在庫を作る補助
     */
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
        ShippingTaxCategory.ensure(dsl);
        var catalog = new CatalogTestData(mockMvc, clock);
        var product = catalog.product(catalog.taxCategory("標準", "10"), "商品", 1000).publish();
        var variantId = product.variantIds.getFirst();
        inventory.stock(variantId, product.id, 10);
        var orders = new OrderTestData(mockMvc, gateway, applicationContext);
        accepted = orders.placeOrder(variantId, 1);
        shipped = orders.placeOrder(variantId, 1);
        // ハイフン付きで出荷し、送り状番号をハイフンなしにそろえる処理も通す
        new ShippingTestData(mockMvc, mockCarrier).ship(shipped, Carrier.YAMATO, TRACKING_NUMBER_WITH_HYPHENS);
    }

    // ============================== テスト ==============================

    @Nested
    @DisplayName("注文詳細の出荷")
    class Detail {

        @Test
        @DisplayName("AC-179 出荷済みの注文の詳細に、出荷済みの状態と、配送状態・配送業者・送り状番号を返す")
        void showsShipment() throws Exception {
            // 操作と確認：出荷の項目が付き、送り状番号はハイフンなし
            mockMvc.perform(get("/api/store/orders/" + shipped))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("SHIPPED"))
                    .andExpect(jsonPath("$.shipment.carrier").value("YAMATO"))
                    .andExpect(jsonPath("$.shipment.trackingNumber").value(TRACKING_NUMBER))
                    .andExpect(jsonPath("$.shipment.status").value("SHIPPED"));
        }

        @Test
        @DisplayName("AC-179 配送状態は、物流サービスの通知で進む")
        void advancesShipmentStatusByNotification() throws Exception {
            // 準備：配達中の通知を送る
            notifyStatus(Carrier.YAMATO, "配達中");

            // 操作と確認：配送状態が配達中
            mockMvc.perform(get("/api/store/orders/" + shipped)).andExpect(jsonPath("$.shipment.status").value("IN_TRANSIT"));
        }

        @Test
        @DisplayName("AC-179 出荷していない注文の詳細は、出荷が null")
        void returnsNullShipmentWhenNotShipped() throws Exception {
            // 操作と確認：受付の注文の出荷は null
            mockMvc.perform(get("/api/store/orders/" + accepted)).andExpect(jsonPath("$.shipment").value(nullValue()));
        }
    }

    @Nested
    @DisplayName("注文一覧の配送状態")
    class OrderList {

        @Test
        @DisplayName("AC-180 注文一覧の出荷済みの注文に、配送状態を返す")
        void showsShipmentStatus() throws Exception {
            // 操作と確認：出荷済みの注文の行に、状態と配送状態
            mockMvc.perform(get("/api/store/me/orders"))
                    .andExpect(jsonPath("$.items[?(@.id == '%s')].status".formatted(shipped)).value(contains("SHIPPED")))
                    .andExpect(jsonPath("$.items[?(@.id == '%s')].shipmentStatus".formatted(shipped)).value(contains("SHIPPED")));
        }

        @Test
        @DisplayName("AC-180 出荷していない注文の行は、配送状態が null")
        void returnsNullShipmentStatusWhenNotShipped() throws Exception {
            // 操作と確認：受付の注文の行の配送状態は null
            mockMvc.perform(get("/api/store/me/orders"))
                    .andExpect(jsonPath("$.items[?(@.id == '%s')].shipmentStatus".formatted(accepted)).value(contains(nullValue())));
        }
    }

    @Nested
    @DisplayName("出荷済みの注文のキャンセル")
    class Cancellation {

        @Test
        @DisplayName("AC-171 出荷済みの注文の詳細は、キャンセルを申請できないことを示す")
        void showsNotRequestable() throws Exception {
            // 操作と確認：申請の操作ができない
            mockMvc.perform(get("/api/store/orders/" + shipped)).andExpect(jsonPath("$.actions.requestCancellation").value(false));
        }

        @Test
        @DisplayName("AC-171 出荷済みの注文のキャンセルを申請すると 400")
        void rejectsRequest() throws Exception {
            // 操作と確認：400 の CANCELLATION_NOT_REQUESTABLE
            mockMvc.perform(put("/api/store/orders/" + shipped + "/cancellation-request")
                            .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("CANCELLATION_NOT_REQUESTABLE"));
        }

        @Test
        @DisplayName("AC-196 返送された注文を運営者がキャンセルすると、注文詳細はキャンセルを示す")
        void showsCancelledInDetail() throws Exception {
            // 準備：返送の通知の後、運営者がキャンセルする
            cancelAfterReturn();

            // 操作と確認：キャンセルで、種類は運営者の判断
            mockMvc.perform(get("/api/store/orders/" + shipped))
                    .andExpect(jsonPath("$.status").value("CANCELLED"))
                    .andExpect(jsonPath("$.cancellation.kind").value("OPERATOR"));
        }

        @Test
        @DisplayName("AC-196 返送された注文を運営者がキャンセルすると、注文一覧はキャンセルを示す")
        void showsCancelledInList() throws Exception {
            // 準備：返送の通知の後、運営者がキャンセルする
            cancelAfterReturn();

            // 操作と確認：その注文の行がキャンセル
            mockMvc.perform(get("/api/store/me/orders"))
                    .andExpect(jsonPath("$.items[?(@.id == '%s')].status".formatted(shipped)).value(contains("CANCELLED")));
        }
    }

    // ============================== 準備の補助 ==============================

    /**
     * mock の物流サービスから、配送状態の通知をエンドポイントへ送る。
     *
     * @param carrier    配送業者
     * @param statusName 物流サービスの配送状態の名前
     */
    private void notifyStatus(Carrier carrier, String statusName) throws Exception {
        new ShippingTestData(mockMvc, mockCarrier).notifyStatus(carrier, TRACKING_NUMBER, statusName);
    }

    /**
     * 出荷済みの注文に返送の通知を送り、運営者が在庫に戻してキャンセルする。
     */
    private void cancelAfterReturn() throws Exception {
        notifyStatus(Carrier.YAMATO, "返品");
        mockMvc.perform(put("/api/admin/orders/" + shipped + "/cancel")
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"reason": "返送された", "restock": true}"""))
                .andExpect(status().isNoContent());
    }
}
