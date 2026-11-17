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
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 返送された注文のキャンセルの受け入れテスト
 *
 * <p>配送状態は、mock の物流サービスの署名付きの通知をエンドポイントに送って進める。返金はコミットの後に行うので、
 * ロールバックするこのテストでは「返金待ち」までを確認する。返金の結果を待たずに成立することと、返金の額は、
 * コミットする {@code ReturnedOrderRefundTest} が確認する。
 *
 * <p>準備のデータと確認の期待値は、同じ定数から作る。
 */
@IntegrationTest
@Transactional
class ReturnedOrderCancellationApiTest {

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
     * 準備で入荷する在庫数
     */
    private static final int STOCKED = 5;

    /**
     * 注文の数量
     */
    private static final int ORDERED = 2;

    /**
     * 注文の後の在庫数（入荷した数 − 注文の数量）
     */
    private static final int AFTER_ORDER = STOCKED - ORDERED;

    /**
     * キャンセルの理由
     */
    private static final String REASON = "返送された";

    // ============================== Bean ==============================

    @Autowired
    MockMvc mockMvc;

    @Autowired
    TestPaymentGateway gateway;

    @Autowired
    MockCarrier mockCarrier;

    /**
     * 出荷を作る補助
     */
    private ShippingTestData shipping;

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
     * 注文した商品のバリアント ID
     */
    private UUID variantId;

    /**
     * 出荷済みの注文の ID
     */
    private UUID orderId;

    @BeforeEach
    void setUp() throws Exception {
        inventory = new InventoryTestData(stockItemRepository, NOW);
        ShippingTaxCategory.ensure(dsl);
        shipping = new ShippingTestData(mockMvc, mockCarrier);
        var catalog = new CatalogTestData(mockMvc, clock);
        var product = catalog.product(catalog.taxCategory("標準", "10"), "商品", 1000).publish();
        variantId = product.variantIds.getFirst();
        inventory.stock(variantId, product.id, STOCKED);
        orderId = new OrderTestData(mockMvc, gateway, applicationContext).placeOrder(variantId, ORDERED);
        shipping.ship(orderId, Carrier.YAMATO, TRACKING_NUMBER);
    }

    // ============================== テスト ==============================

    @Nested
    @DisplayName("返送の注文のキャンセル")
    class ReturnedOrder {

        @Test
        @DisplayName("AC-268 返送の注文の詳細は、キャンセルでき、在庫に戻すかを聞く")
        void offersCancellationWithRestockChoice() throws Exception {
            // 準備：配達中から返送にする
            notifyStatus("配達中");
            notifyStatus("返品");

            // 操作と確認：配送状態が返送で、キャンセルと在庫の選択の両方のフラグが立つ
            admin()
                    .andExpect(jsonPath("$.shipment.status").value("RETURNED"))
                    .andExpect(jsonPath("$.actions.cancel").value(true))
                    .andExpect(jsonPath("$.actions.cancelAsksRestock").value(true));
        }

        @Test
        @DisplayName("AC-190 返送の注文は、理由を記録してキャンセルできる")
        void cancelsWithReason() throws Exception {
            // 準備：配達中から返送にする
            notifyStatus("配達中");
            notifyStatus("返品");

            // 操作：理由を付けてキャンセルする
            cancel(REASON, true).andExpect(status().isNoContent());

            // 確認：キャンセルになり、理由が残り、配送状態は返送のまま
            admin()
                    .andExpect(jsonPath("$.status").value("CANCELLED"))
                    .andExpect(jsonPath("$.cancellation.reason").value(REASON))
                    .andExpect(jsonPath("$.shipment.status").value("RETURNED"));
        }

        @ParameterizedTest(name = "「{0}」")
        @CsvSource({"理由なし,,INVALID_REQUEST", "空,'',INVALID_REQUEST", "空白,'   ',CANCELLATION_REASON_REQUIRED"})
        @DisplayName("AC-190 返送の注文も、理由がなければキャンセルできず 400 で、出荷済みのまま")
        void requiresReason(String label, String reason, String errorCode) throws Exception {
            // 準備：返送にする
            notifyStatus("返品");

            // 操作と確認：理由がない・空・空白の本文は 400 で、理由の誤りを示すエラーコード
            mockMvc.perform(put("/api/admin/orders/" + orderId + "/cancel")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(reason == null ? """
                                    {"restock": true}""" : """
                                    {"reason": "%s", "restock": true}""".formatted(reason)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value(errorCode));

            // 確認：出荷済みのまま
            admin().andExpect(jsonPath("$.status").value("SHIPPED"));
        }

        @Test
        @DisplayName("AC-192 返送の注文をキャンセルすると、支払は返金待ちになる")
        void requestsRefund() throws Exception {
            // 準備：配達中から返送にする
            notifyStatus("配達中");
            notifyStatus("返品");

            // 操作：キャンセルする
            cancel(REASON, true).andExpect(status().isNoContent());

            // 確認：支払が返金待ち
            admin().andExpect(jsonPath("$.payment.status").value("REFUND_PENDING"));
        }
    }

    @Nested
    @DisplayName("在庫に戻すかの選択")
    class RestockChoice {

        @Test
        @DisplayName("AC-191 在庫に戻すときは、明細の数量だけ在庫数が増える")
        void restocksOrderedQuantity() throws Exception {
            // 準備：返送にする
            notifyStatus("返品");

            // 確認（前提）：注文の数量だけ減っている
            expectQuantity(AFTER_ORDER);

            // 操作：在庫に戻してキャンセルする
            cancel(REASON, true).andExpect(status().isNoContent());

            // 確認：入荷した数に戻る
            expectQuantity(STOCKED);
        }

        @Test
        @DisplayName("AC-191 在庫に戻さないときも、キャンセルになり、在庫数は変わらない")
        void keepsQuantityWithoutRestock() throws Exception {
            // 準備：返送にする
            notifyStatus("返品");

            // 操作：在庫に戻さずにキャンセルする
            cancel("返送された（破損）", false).andExpect(status().isNoContent());

            // 確認：キャンセルになり、在庫数は注文の後のまま
            admin().andExpect(jsonPath("$.status").value("CANCELLED"));
            expectQuantity(AFTER_ORDER);
        }

        @ParameterizedTest(name = "「{0}」")
        @ValueSource(booleans = {true, false})
        @DisplayName("AC-191 在庫に戻したかが、キャンセルの記録に残る")
        void recordsRestockChoice(boolean restock) throws Exception {
            // 準備：返送にする
            notifyStatus("返品");

            // 操作：在庫に戻すかを選んでキャンセルする
            cancel(REASON, restock).andExpect(status().isNoContent());

            // 確認：選んだとおりに記録される
            admin().andExpect(jsonPath("$.cancellation.restocked").value(restock));
        }

        @Test
        @DisplayName("AC-191 返送の注文のキャンセルで、在庫に戻すかを指定しなければ 400")
        void requiresRestockChoice() throws Exception {
            // 準備：返送にする
            notifyStatus("返品");

            // 操作と確認：在庫に戻すかのない本文は拒否される
            mockMvc.perform(put("/api/admin/orders/" + orderId + "/cancel")
                            .contentType(MediaType.APPLICATION_JSON).content("""
                                    {"reason": "返送された"}"""))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("RESTOCK_CHOICE_REQUIRED"));
        }
    }

    @Nested
    @DisplayName("返送でない出荷済みの注文")
    class NotReturned {

        @ParameterizedTest(name = "「{0}」")
        @CsvSource({"出荷済み,", "配達中,配達中", "配達完了,配達完了"})
        @DisplayName("AC-268 返送でない出荷済みの注文の詳細は、キャンセルできず、在庫に戻すかも聞かない")
        void offersNoCancellation(String label, String notification) throws Exception {
            // 準備：配送状態を進める（出荷済みのままなら通知しない）
            advanceTo(notification);

            // 操作と確認：どちらのフラグも立たない
            admin()
                    .andExpect(jsonPath("$.actions.cancel").value(false))
                    .andExpect(jsonPath("$.actions.cancelAsksRestock").value(false));
        }

        @ParameterizedTest(name = "「{0}」")
        @CsvSource({"出荷済み,", "配達中,配達中", "配達完了,配達完了"})
        @DisplayName("AC-171 返送でない出荷済みの注文のキャンセルは 409 で、注文も在庫も変わらない")
        void rejectsCancellation(String label, String notification) throws Exception {
            // 準備：配送状態を進める（出荷済みのままなら通知しない）
            advanceTo(notification);

            // 操作と確認：返送でないことのエラーで拒否される
            cancel("理由", true).andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errorCode").value("SHIPPED_ORDER_NOT_RETURNED"));

            // 確認：出荷済みのままで、在庫数は注文の後のまま
            admin().andExpect(jsonPath("$.status").value("SHIPPED"));
            expectQuantity(AFTER_ORDER);
        }
    }

    // ============================== 準備の補助 ==============================

    /**
     * mock の物流サービスから、配送状態の通知をエンドポイントへ送る。
     *
     * @param statusName 配送状態の名前
     */
    private void notifyStatus(String statusName) throws Exception {
        shipping.notifyStatus(Carrier.YAMATO, TRACKING_NUMBER, statusName);
    }

    /**
     * 配送状態を進める。
     *
     * @param statusName 配送状態の名前。null なら通知せず、出荷済みのままにする
     */
    private void advanceTo(String statusName) throws Exception {
        if (statusName != null) {
            notifyStatus(statusName);
        }
    }

    /**
     * 運営者のキャンセルを送る。
     *
     * @param reason  理由
     * @param restock 在庫に戻すか
     * @return 応答
     */
    private ResultActions cancel(String reason, boolean restock) throws Exception {
        return mockMvc.perform(put("/api/admin/orders/" + orderId + "/cancel")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"reason": "%s", "restock": %s}""".formatted(reason, restock)));
    }

    // ============================== 確認の補助 ==============================

    /**
     * 管理の API で注文の詳細を読む。
     *
     * @return 応答
     */
    private ResultActions admin() throws Exception {
        return mockMvc.perform(get("/api/admin/orders/" + orderId));
    }

    /**
     * 在庫数を確認する。
     *
     * @param quantity 期待する在庫数
     */
    private void expectQuantity(int quantity) throws Exception {
        InventoryTestData.expectQuantity(mockMvc, variantId, quantity);
    }
}
