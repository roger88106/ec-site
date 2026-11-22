package com.ecsite.module.order;

import com.ecsite.common.model.id.OrderId;
import com.ecsite.module.inventory.domain.stock.port.StockItemRepository;
import com.ecsite.module.order.application.payment.OrderPayments;
import com.ecsite.module.order.event.OrderShipped;
import com.ecsite.module.order.event.OrderStatusChanged;
import com.ecsite.module.order.event.OrderStatusChanged.CancellationKind;
import com.ecsite.module.order.event.OrderStatusChanged.Status;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
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
 * イベント「注文の状態の変化」の統合テスト
 *
 * <p>注文の状態が変わるたびに 1 つ出て、変わらない操作（重ねて届いた支払の結果など）では出ないことを確認する。
 * 状態の遷移の規則そのものは、注文の単体テストが確認する。
 *
 * <p>イベントの記録は発行の時点なので、コミットを待たない。テストごとにロールバックする。
 */
@IntegrationTest
@RecordApplicationEvents
@Transactional
class OrderStatusChangedEventTest {

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

    @Autowired
    ApplicationEvents events;

    /**
     * 注文を作る補助
     */
    private OrderTestData orders;

    /**
     * 出荷を作る補助
     */
    private ShippingTestData shipping;

    /**
     * 注文する商品のバリアント ID
     */
    private UUID variantId;

    @BeforeEach
    void setUp() throws Exception {
        inventory = new InventoryTestData(stockItemRepository, NOW);
        ShippingTaxCategory.ensure(dsl);
        var catalog = new CatalogTestData(mockMvc, clock);
        var product = catalog.product(catalog.taxCategory("標準", "10"), "商品", 1000).publish();
        variantId = product.variantIds.getFirst();
        inventory.stock(variantId, product.id, 10);
        orders = new OrderTestData(mockMvc, gateway, applicationContext);
        shipping = new ShippingTestData(mockMvc, mockCarrier, applicationContext);
        // 商品の準備で時計が進むので、注文の前に戻す
        clock.setInstant(NOW);
    }

    // ============================== テスト ==============================

    @Nested
    @DisplayName("状態が変わる操作")
    class OnChange {

        @Test
        @DisplayName("作成、受付、キャンセル申請、却下、出荷、返送のキャンセルのたびに、前と後の状態と発生日時を持つイベントが 1 つずつ出る")
        void emitsOnEveryChange() throws Exception {
            // 準備：支払待ちの注文を作り、1 分ずつ時計を進めて状態を変えていく
            var orderId = orders.placePendingOrder(variantId, 2);
            at(1);
            orders.pay(orderId);
            at(2);
            requestCancellation(orderId);
            at(3);
            mockMvc.perform(put("/api/admin/orders/" + orderId + "/cancellation-request/reject")).andExpect(status().isNoContent());
            at(4);
            shipping.ship(orderId, Carrier.YAMATO, TRACKING_NUMBER);
            shipping.notifyStatus(Carrier.YAMATO, TRACKING_NUMBER, "返品");
            at(5);

            // 操作：返送の注文を、在庫に戻さずにキャンセルする
            mockMvc.perform(put("/api/admin/orders/" + orderId + "/cancel")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"reason": "返送", "restock": false}""")).andExpect(status().isNoContent());

            // 確認：変化ごとに 1 つずつ、順に出ている
            assertEmittedOnEveryChange(orderId);
        }

        @Test
        @DisplayName("出荷すると、注文の状態の変化とは別に、注文の出荷のイベントが出る。その後の返送とキャンセルでは重ねて出ない")
        void emitsShippedOnlyOnShipment() throws Exception {
            // 準備：受付の注文
            var orderId = orders.placeOrder(variantId, 1);

            // 操作：出荷し、返送の後にキャンセルする
            shipping.ship(orderId, Carrier.YAMATO, TRACKING_NUMBER);
            shipping.notifyStatus(Carrier.YAMATO, TRACKING_NUMBER, "返品");
            mockMvc.perform(put("/api/admin/orders/" + orderId + "/cancel")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"reason": "返送", "restock": false}""")).andExpect(status().isNoContent());

            // 確認：この注文の出荷のイベントが 1 つだけ
            assertThat(events.stream(OrderShipped.class)).extracting(OrderShipped::orderId).containsExactly(new OrderId(orderId));
        }
    }

    @Nested
    @DisplayName("キャンセルの種類と在庫の戻し")
    class CancellationKinds {

        @Test
        @DisplayName("申請の承認のキャンセルは、種類が申請の承認で、在庫に戻した")
        void emitsOnApproval() throws Exception {
            // 準備：受付の注文で、キャンセルを申請する
            var orderId = orders.placeOrder(variantId, 1);
            requestCancellation(orderId);

            // 操作：申請を承認する
            mockMvc.perform(put("/api/admin/orders/" + orderId + "/cancellation-request/approve")).andExpect(status().isNoContent());

            // 確認：最後のイベントが、申請中からキャンセルへの、申請の承認による変化で、在庫に戻した
            assertThat(events.stream(OrderStatusChanged.class)).last()
                    .extracting(OrderStatusChanged::from, OrderStatusChanged::to, OrderStatusChanged::cancellationKind,
                            OrderStatusChanged::restocked)
                    .containsExactly(Status.CANCELLATION_REQUESTED, Status.CANCELLED, CancellationKind.REQUEST_APPROVED, true);
        }

        @Test
        @DisplayName("受付の注文の運営者のキャンセルは、種類が運営者の判断で、在庫に戻した")
        void emitsOnOperatorCancellation() throws Exception {
            // 準備：受付の注文
            var orderId = orders.placeOrder(variantId, 1);

            // 操作：運営者がキャンセルする
            mockMvc.perform(put("/api/admin/orders/" + orderId + "/cancel")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"reason": "在庫の誤り"}""")).andExpect(status().isNoContent());

            // 確認：最後のイベントが、受付からキャンセルへの、運営者の判断による変化で、在庫に戻した
            assertThat(events.stream(OrderStatusChanged.class)).last()
                    .extracting(OrderStatusChanged::from, OrderStatusChanged::to, OrderStatusChanged::cancellationKind,
                            OrderStatusChanged::restocked)
                    .containsExactly(Status.ACCEPTED, Status.CANCELLED, CancellationKind.OPERATOR, true);
        }

        @Test
        @DisplayName("支払の期限切れのキャンセルは、種類が支払期限切れで、在庫に戻した")
        void emitsOnPaymentExpiry() throws Exception {
            // 準備：支払待ちの注文を作り、30 分後にする
            var orderId = orders.placePendingOrder(variantId, 1);
            at(30);

            // 操作：支払の期限切れを反映する
            OrderPayments.expire(applicationContext, orderId);

            // 確認：成立と、支払期限切れのキャンセルの 2 つだけが出る
            assertThat(events.stream(OrderStatusChanged.class))
                    .extracting(OrderStatusChanged::from, OrderStatusChanged::to, OrderStatusChanged::occurredAt,
                            OrderStatusChanged::cancellationKind, OrderStatusChanged::restocked)
                    .containsExactly(
                            tuple(null, Status.PENDING_PAYMENT, NOW, null, null),
                            tuple(Status.PENDING_PAYMENT, Status.CANCELLED, minutes(30), CancellationKind.PAYMENT_EXPIRED, true));
        }
    }

    @Nested
    @DisplayName("状態が変わらない操作")
    class WithoutChange {

        @Test
        @DisplayName("状態の変わらない操作（重ねて届いた与信の成功、申請中の申請）では出ない")
        void doesNotEmitWithoutChange() throws Exception {
            // 準備：受付の注文
            var orderId = orders.placeOrder(variantId, 1);

            // 操作：与信の成功を重ねて反映し、キャンセルを 2 回申請する
            OrderPayments.accept(applicationContext, orderId);
            requestCancellation(orderId);
            requestCancellation(orderId);

            // 確認：成立、受付、申請中の 3 つだけ
            assertThat(events.stream(OrderStatusChanged.class)).extracting(OrderStatusChanged::to)
                    .containsExactly(Status.PENDING_PAYMENT, Status.ACCEPTED, Status.CANCELLATION_REQUESTED);
        }
    }

    // ============================== 確認の補助 ==============================

    /**
     * {@link OnChange#emitsOnEveryChange} の操作の順に、状態の変化のイベントが出たことを確認する。
     *
     * <p>確認すること：
     * <ul>
     *   <li>イベントが 6 つで、順に、なし → 支払待ち、支払待ち → 受付、受付 → キャンセル申請中、キャンセル申請中 → 受付、
     *   受付 → 出荷済み、出荷済み → キャンセル</li>
     *   <li>全てのイベントの注文 ID がこの注文</li>
     *   <li>発生日時が、それぞれの操作の時刻（NOW から 0〜5 分後）</li>
     *   <li>最後のキャンセルだけ、種類が運営者の判断で、在庫に戻していない。他はキャンセルの種類と在庫の戻しがない</li>
     *   <li>イベント ID が全て異なる</li>
     * </ul>
     *
     * @param orderId 注文 ID（実行ごとに作るので、定数にできない）
     */
    private void assertEmittedOnEveryChange(UUID orderId) {
        assertThat(events.stream(OrderStatusChanged.class))
                .extracting(OrderStatusChanged::orderId, OrderStatusChanged::from, OrderStatusChanged::to,
                        OrderStatusChanged::occurredAt, OrderStatusChanged::cancellationKind, OrderStatusChanged::restocked)
                .containsExactly(
                        tuple(new OrderId(orderId), null, Status.PENDING_PAYMENT, NOW, null, null),
                        tuple(new OrderId(orderId), Status.PENDING_PAYMENT, Status.ACCEPTED, minutes(1), null, null),
                        tuple(new OrderId(orderId), Status.ACCEPTED, Status.CANCELLATION_REQUESTED, minutes(2), null, null),
                        tuple(new OrderId(orderId), Status.CANCELLATION_REQUESTED, Status.ACCEPTED, minutes(3), null, null),
                        tuple(new OrderId(orderId), Status.ACCEPTED, Status.SHIPPED, minutes(4), null, null),
                        tuple(new OrderId(orderId), Status.SHIPPED, Status.CANCELLED, minutes(5), CancellationKind.OPERATOR, false));
        assertThat(events.stream(OrderStatusChanged.class).map(OrderStatusChanged::eventId).distinct()).hasSize(6);
    }

    // ============================== 準備の補助 ==============================

    /**
     * 既定の会員として、キャンセルを申請し、204 を確認する。
     *
     * @param orderId 注文 ID
     */
    private void requestCancellation(UUID orderId) throws Exception {
        mockMvc.perform(put("/api/store/orders/" + orderId + "/cancellation-request")
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isNoContent());
    }

    /**
     * 時計を NOW の指定の分の後にする。
     *
     * @param minutes NOW からの分
     */
    private void at(int minutes) {
        clock.setInstant(minutes(minutes));
    }

    /**
     * NOW の指定の分の後の時刻を求める。
     *
     * @param minutes NOW からの分
     * @return 時刻
     */
    private static Instant minutes(int minutes) {
        return NOW.plus(Duration.ofMinutes(minutes));
    }
}
