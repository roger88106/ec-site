package com.ecsite.module.order;

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
import com.ecsite.module.order.event.OrderShipped;
import com.ecsite.module.order.event.OrderStatusChanged;
import com.ecsite.module.order.event.OrderStatusChanged.CancellationKind;
import com.ecsite.module.order.event.OrderStatusChanged.Status;
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
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * イベント「注文の状態の変化」の統合テスト（T0110、plan 010 第 2 節）。<br />
 * 注文の状態が変わるたびに 1 つ出て、変わらない操作（重ねて届いた支払の結果など）では出ないことを確認する。
 * イベントの記録は発行の時点なので、コミットを待たない。テストごとにロールバックする。
 */
@IntegrationTest
@AutoConfigureMockMvc
@Import(TestClockConfig.class)
@RecordApplicationEvents
@Transactional
class OrderStatusChangedEventTest {

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

    @Autowired
    ApplicationEvents events;

    private OrderTestData orders;

    private ShippingTestData shipping;

    private UUID variantId;

    @BeforeEach
    void setUp() throws Exception {
        inventory = new InventoryTestData(stockItemRepository, NOW);
        clock.setInstant(NOW);
        gateway.reset();
        ShippingTaxCategory.ensure(dsl);
        var data = new CatalogTestData(mockMvc, clock);
        var product = data.product(data.taxCategory("標準", "10"), "商品", 1000).publish();
        variantId = product.variantIds.getFirst();
        inventory.stock(variantId, product.id, 10);
        orders = new OrderTestData(mockMvc, gateway, orderPaymentService);
        shipping = new ShippingTestData(mockMvc, mockCarrier);
        // 商品の準備で時計が進むので、注文の前に戻す
        clock.setInstant(NOW);
    }

    @Test
    @DisplayName("作成、受付、キャンセル申請、却下、出荷、返送のキャンセルで、前と後の状態と発生日時が出る。出荷では注文の出荷も出る")
    void emitsOnEveryChange() throws Exception {
        var orderId = orders.placePendingOrder(variantId, 2);
        at(1);
        orders.pay(orderId);
        at(2);
        mockMvc.perform(put("/api/store/orders/" + orderId + "/cancellation-request")
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isNoContent());
        at(3);
        mockMvc.perform(put("/api/admin/orders/" + orderId + "/cancellation-request/reject")).andExpect(status().isNoContent());
        at(4);
        shipping.ship(orderId, Carrier.YAMATO, TRACKING_NUMBER);
        shipping.notifyStatus(Carrier.YAMATO, TRACKING_NUMBER, "返品");
        at(5);
        mockMvc.perform(put("/api/admin/orders/" + orderId + "/cancel")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"reason": "返送", "restock": false}""")).andExpect(status().isNoContent());

        assertThat(events.stream(OrderStatusChanged.class))
                .extracting(OrderStatusChanged::orderId, OrderStatusChanged::from, OrderStatusChanged::to,
                        OrderStatusChanged::occurredAt, OrderStatusChanged::cancellationKind, OrderStatusChanged::restocked)
                .containsExactly(
                        tuple(orderId, null, Status.PENDING_PAYMENT, NOW, null, null),
                        tuple(orderId, Status.PENDING_PAYMENT, Status.ACCEPTED, minutes(1), null, null),
                        tuple(orderId, Status.ACCEPTED, Status.CANCELLATION_REQUESTED, minutes(2), null, null),
                        tuple(orderId, Status.CANCELLATION_REQUESTED, Status.ACCEPTED, minutes(3), null, null),
                        tuple(orderId, Status.ACCEPTED, Status.SHIPPED, minutes(4), null, null),
                        tuple(orderId, Status.SHIPPED, Status.CANCELLED, minutes(5), CancellationKind.OPERATOR, false));
        assertThat(events.stream(OrderShipped.class)).extracting(OrderShipped::orderId).containsExactly(orderId);
        assertThat(events.stream(OrderStatusChanged.class).map(OrderStatusChanged::eventId).distinct()).hasSize(6);
    }

    @Test
    @DisplayName("申請の承認のキャンセルは、種類が申請の承認で、在庫に戻した")
    void emitsOnApproval() throws Exception {
        var orderId = orders.placeOrder(variantId, 1);
        mockMvc.perform(put("/api/store/orders/" + orderId + "/cancellation-request")
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isNoContent());
        mockMvc.perform(put("/api/admin/orders/" + orderId + "/cancellation-request/approve")).andExpect(status().isNoContent());

        assertThat(events.stream(OrderStatusChanged.class)).last()
                .extracting(OrderStatusChanged::from, OrderStatusChanged::to, OrderStatusChanged::cancellationKind,
                        OrderStatusChanged::restocked)
                .containsExactly(Status.CANCELLATION_REQUESTED, Status.CANCELLED, CancellationKind.REQUEST_APPROVED, true);
    }

    @Test
    @DisplayName("受付の注文の運営者のキャンセルは、種類が運営者の判断で、在庫に戻した")
    void emitsOnOperatorCancellation() throws Exception {
        var orderId = orders.placeOrder(variantId, 1);
        mockMvc.perform(put("/api/admin/orders/" + orderId + "/cancel")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"reason": "在庫の誤り"}""")).andExpect(status().isNoContent());

        assertThat(events.stream(OrderStatusChanged.class)).last()
                .extracting(OrderStatusChanged::from, OrderStatusChanged::to, OrderStatusChanged::cancellationKind,
                        OrderStatusChanged::restocked)
                .containsExactly(Status.ACCEPTED, Status.CANCELLED, CancellationKind.OPERATOR, true);
    }

    @Test
    @DisplayName("支払の期限切れのキャンセルは、種類が支払期限切れで、在庫に戻した")
    void emitsOnPaymentExpiry() throws Exception {
        var orderId = orders.placePendingOrder(variantId, 1);
        at(30);
        orderPaymentService.expire(orderId);

        assertThat(events.stream(OrderStatusChanged.class))
                .extracting(OrderStatusChanged::from, OrderStatusChanged::to, OrderStatusChanged::occurredAt,
                        OrderStatusChanged::cancellationKind, OrderStatusChanged::restocked)
                .containsExactly(
                        tuple(null, Status.PENDING_PAYMENT, NOW, null, null),
                        tuple(Status.PENDING_PAYMENT, Status.CANCELLED, minutes(30), CancellationKind.PAYMENT_EXPIRED, true));
    }

    @Test
    @DisplayName("状態の変わらない操作（重ねて届いた与信の成功、申請中の申請）では出ない")
    void doesNotEmitWithoutChange() throws Exception {
        var orderId = orders.placeOrder(variantId, 1);
        orderPaymentService.accept(orderId);
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(put("/api/store/orders/" + orderId + "/cancellation-request")
                    .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isNoContent());
        }

        assertThat(events.stream(OrderStatusChanged.class)).extracting(OrderStatusChanged::to)
                .containsExactly(Status.PENDING_PAYMENT, Status.ACCEPTED, Status.CANCELLATION_REQUESTED);
    }

    // ============================== 補助 ==============================

    /**
     * 時計を NOW の指定の分の後にする
     */
    private void at(int minutes) {
        clock.setInstant(minutes(minutes));
    }

    /**
     * NOW の指定の分の後
     */
    private static Instant minutes(int minutes) {
        return NOW.plus(Duration.ofMinutes(minutes));
    }
}
