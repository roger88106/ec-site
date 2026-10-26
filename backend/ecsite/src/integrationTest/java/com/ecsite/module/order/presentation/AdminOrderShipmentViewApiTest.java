package com.ecsite.module.order.presentation;

import com.ecsite.CatalogTestData;
import com.ecsite.IntegrationTest;
import com.ecsite.InventoryTestData;
import com.ecsite.MutableClock;
import com.ecsite.OrderTestData;
import com.ecsite.ShippingTaxCategory;
import com.ecsite.ShippingTestData;
import com.ecsite.TestClockConfig;
import com.ecsite.module.inventory.domain.stock.port.StockItemRepository;
import com.ecsite.module.payment.infrastructure.mock.TestPaymentGateway;
import com.ecsite.module.shipping.domain.Carrier;
import com.ecsite.module.shipping.infrastructure.mock.MockCarrier;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * B 側の注文の一覧・詳細の、出荷と与信期限の表示と絞り込みの受け入れテスト。<br />
 * 与信期限は 7 日、「期限が近い」は期限の 2 日前から（設定の既定）。テストごとにロールバックする。
 */
@IntegrationTest
@AutoConfigureMockMvc
@Import(TestClockConfig.class)
@Transactional
class AdminOrderShipmentViewApiTest {

    /**
     * API のパス
     */
    private static final String BASE = "/api/admin/orders";

    /**
     * 注文・与信の時刻（日本時間の 2026-11-20 00:00。他のテストの注文日（2026-10-08、12 月）と重ならない日にして、注文番号の前方一致で自分の注文だけを絞れるようにする）
     */
    private static final Instant NOW = Instant.parse("2026-11-19T15:00:00Z");

    /**
     * 与信期限（与信の時刻から 7 日）
     */
    private static final Instant AUTHORIZATION_DEADLINE = NOW.plus(Duration.ofDays(7));

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
     * 注文のテストデータ
     */
    private OrderTestData orders;

    /**
     * 出荷を作る補助
     */
    private ShippingTestData shipping;

    /**
     * 受付のまま（出荷待ち）の注文
     */
    private UUID accepted;

    /**
     * 出荷済み（配送状態も出荷済み）の注文
     */
    private UUID shipped;

    /**
     * 出荷済みで、配送状態が返送の注文
     */
    private UUID returned;

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
        // 商品の公開で時計が進むので、注文と与信の時刻をそろえる
        clock.setInstant(NOW);
        orders = new OrderTestData(mockMvc, gateway, applicationContext);
        shipping = new ShippingTestData(mockMvc, mockCarrier);
        accepted = orders.placeOrder(variantId, 1, "受付 太郎");
        shipped = orders.placeOrder(variantId, 1, "出荷 花子");
        returned = orders.placeOrder(variantId, 1, "返送 次郎");
        shipping.ship(shipped, Carrier.YAMATO, "111111111111");
        shipping.ship(returned, Carrier.YAMATO, "222222222222");
        shipping.notifyStatus(Carrier.YAMATO, "222222222222", "返品");
    }

    @Nested
    @DisplayName("AC-181、186、194 注文の詳細")
    class Detail {

        @Test
        @DisplayName("AC-181 出荷の配送業者、送り状番号、配送状態、出荷日時を返す")
        void showsShipment() throws Exception {
            mockMvc.perform(get(BASE + "/" + returned))
                    .andExpect(jsonPath("$.shipment.carrier").value("YAMATO"))
                    .andExpect(jsonPath("$.shipment.trackingNumber").value("222222222222"))
                    .andExpect(jsonPath("$.shipment.status").value("RETURNED"))
                    .andExpect(jsonPath("$.shipment.shippedAt").value("2026-11-19T15:00:00Z"));
            mockMvc.perform(get(BASE + "/" + accepted)).andExpect(jsonPath("$.shipment").doesNotExist());
        }

        @Test
        @DisplayName("AC-186 与信期限を返し、期限の 2 日前を過ぎたら「期限が近い」にする（期限を過ぎても近いのまま）")
        void showsAuthorizationDeadline() throws Exception {
            mockMvc.perform(get(BASE + "/" + accepted))
                    .andExpect(jsonPath("$.payment.authorizationDeadline").value("2026-11-26T15:00:00Z"))
                    .andExpect(jsonPath("$.payment.authorizationExpiring").value(false));

            clock.setInstant(AUTHORIZATION_DEADLINE.minus(Duration.ofDays(2)));
            mockMvc.perform(get(BASE + "/" + accepted)).andExpect(jsonPath("$.payment.authorizationExpiring").value(true));

            clock.setInstant(AUTHORIZATION_DEADLINE.plusSeconds(1));
            mockMvc.perform(get(BASE + "/" + accepted)).andExpect(jsonPath("$.payment.authorizationExpiring").value(true));
        }

        @Test
        @DisplayName("AC-186 売上を確定した注文は、期限が近くならない")
        void capturedIsNotExpiring() throws Exception {
            clock.setInstant(AUTHORIZATION_DEADLINE.minusSeconds(1));

            mockMvc.perform(get(BASE + "/" + shipped))
                    .andExpect(jsonPath("$.payment.status").value("CAPTURED"))
                    .andExpect(jsonPath("$.payment.authorizationExpiring").value(false));
        }

        @Test
        @DisplayName("AC-186、188 与信期限切れでキャンセルした注文は、期限が近いを示さず、絞り込みにも出ない")
        void cancelledIsNotExpiring() throws Exception {
            clock.setInstant(AUTHORIZATION_DEADLINE.plusSeconds(1));
            gateway.expireAuthorization(gateway.authorizationRefOf(orders.paidPageId(accepted)));
            mockMvc.perform(post(BASE + "/" + accepted + "/shipment")
                            .contentType(MediaType.APPLICATION_JSON).content("""
                                    {"carrier": "YAMATO", "trackingNumber": "333333333333"}"""))
                    .andExpect(status().isConflict());
            mockMvc.perform(put(BASE + "/" + accepted + "/cancel")
                            .contentType(MediaType.APPLICATION_JSON).content("""
                                    {"reason": "与信期限切れ"}"""))
                    .andExpect(status().isNoContent());

            mockMvc.perform(get(BASE + "/" + accepted))
                    .andExpect(jsonPath("$.payment.status").value("AUTHORIZATION_EXPIRED"))
                    .andExpect(jsonPath("$.payment.authorizationExpiring").value(false));
            mockMvc.perform(get(BASE).param("orderNumber", orderNumberPrefix()).param("authorizationExpiring", "true"))
                    .andExpect(jsonPath("$.items", empty()));
        }

        @Test
        @DisplayName("AC-194 返送の注文をキャンセルすると、返金が済むまで支払は返金待ちを示す")
        void showsRefundPending() throws Exception {
            mockMvc.perform(put(BASE + "/" + returned + "/cancel")
                            .contentType(MediaType.APPLICATION_JSON).content("""
                                    {"reason": "返送された", "restock": true}"""))
                    .andExpect(status().isNoContent());

            mockMvc.perform(get(BASE + "/" + returned)).andExpect(jsonPath("$.payment.status").value("REFUND_PENDING"));
        }
    }

    @Nested
    @DisplayName("AC-182、186、187、189 注文一覧")
    class ListOrders {

        @Test
        @DisplayName("AC-182、186 各行に配送状態と「与信期限が近い」を返す。出荷していなければ配送状態は null")
        void showsColumns() throws Exception {
            clock.setInstant(AUTHORIZATION_DEADLINE.minus(Duration.ofDays(1)));

            mockMvc.perform(get(BASE).param("orderNumber", orderNumberPrefix()))
                    .andExpect(jsonPath("$.items[?(@.id == '%s')].shipmentStatus".formatted(returned)).value("RETURNED"))
                    .andExpect(jsonPath("$.items[?(@.id == '%s')].shipmentStatus".formatted(shipped)).value("SHIPPED"))
                    .andExpect(jsonPath("$.items[?(@.id == '%s')].shipmentStatus".formatted(accepted)).value(contains(nullValue())))
                    .andExpect(jsonPath("$.items[?(@.id == '%s')].authorizationExpiring".formatted(accepted)).value(true))
                    .andExpect(jsonPath("$.items[?(@.id == '%s')].authorizationExpiring".formatted(shipped)).value(false));
        }

        @Test
        @DisplayName("AC-182 状態「出荷済み」で絞り込むと出荷済みの注文が、「受付」で絞り込むと出荷待ちの注文が並ぶ")
        void filtersByShipped() throws Exception {
            mockMvc.perform(get(BASE).param("orderNumber", orderNumberPrefix()).param("status", "SHIPPED"))
                    .andExpect(jsonPath("$.items[*].id", containsInAnyOrder(shipped.toString(), returned.toString())));
            mockMvc.perform(get(BASE).param("orderNumber", orderNumberPrefix()).param("status", "ACCEPTED"))
                    .andExpect(jsonPath("$.items[*].id", containsInAnyOrder(accepted.toString())));
        }

        @Test
        @DisplayName("AC-187 与信期限が近い注文で絞り込める。他の条件と組み合わせられる")
        void filtersByAuthorizationExpiring() throws Exception {
            mockMvc.perform(get(BASE).param("orderNumber", orderNumberPrefix()).param("authorizationExpiring", "true"))
                    .andExpect(jsonPath("$.items", empty()))
                    .andExpect(jsonPath("$.totalItems").value(0));

            clock.setInstant(AUTHORIZATION_DEADLINE.minus(Duration.ofDays(1)));
            mockMvc.perform(get(BASE).param("orderNumber", orderNumberPrefix()).param("authorizationExpiring", "true"))
                    .andExpect(jsonPath("$.items[*].id", containsInAnyOrder(accepted.toString())));
            mockMvc.perform(get(BASE).param("orderNumber", orderNumberPrefix()).param("authorizationExpiring", "true").param("status", "CANCELLED"))
                    .andExpect(jsonPath("$.items", empty()));
            mockMvc.perform(get(BASE).param("orderNumber", orderNumberPrefix()).param("authorizationExpiring", "false"))
                    .andExpect(jsonPath("$.totalItems").value(3));
        }

        @Test
        @DisplayName("AC-189 配送状態「返送」の注文で絞り込める")
        void filtersByReturned() throws Exception {
            mockMvc.perform(get(BASE).param("orderNumber", orderNumberPrefix()).param("returned", "true"))
                    .andExpect(jsonPath("$.items[*].id", containsInAnyOrder(returned.toString())));
            mockMvc.perform(get(BASE).param("orderNumber", orderNumberPrefix()).param("returned", "true").param("status", "ACCEPTED"))
                    .andExpect(jsonPath("$.items", empty()));
        }
    }

    // ============================== 補助 ==============================

    /**
     * このテストの注文に共通する注文番号の前方一致（注文日。他のテストは、この日に注文しない）
     */
    private static String orderNumberPrefix() {
        return "20261120";
    }
}
