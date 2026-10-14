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
import com.ecsite.module.order.application.OrderPaymentService;
import com.ecsite.module.payment.infrastructure.mock.MockPaymentGateway;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 出荷の登録の受け入れテスト（T9305、AC-167〜173、188）。<br />
 * 売上確定は mock の決済サービスで行う。テストごとにロールバックする。
 */
@IntegrationTest
@AutoConfigureMockMvc
@Import(TestClockConfig.class)
@Transactional
class ShipmentApiTest {

    /**
     * テストの現在時刻
     */
    private static final Instant NOW = Instant.parse("2026-10-07T15:00:00Z");

    @Autowired
    MockMvc mockMvc;

    @Autowired
    MockPaymentGateway gateway;

    @Autowired
    OrderPaymentService orderPaymentService;

    @Autowired
    MutableClock clock;

    @Autowired
    StockItemRepository stockItemRepository;

    @Autowired
    DSLContext dsl;

    private OrderTestData orders;

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
        orders = new OrderTestData(mockMvc, gateway, orderPaymentService);
        orderId = orders.placeOrder(variantId, 2);
    }

    @Nested
    @DisplayName("AC-167、168 出荷の登録")
    class Ship {

        @Test
        @DisplayName("AC-167、168 「受付」の注文に登録すると、売上を確定し、注文は出荷済みになり、出荷ができる")
        void ships() throws Exception {
            clock.setInstant(NOW.plusSeconds(3600));

            ship("YAMATO", "1234-5678-9012").andExpect(status().isCreated())
                    .andExpect(jsonPath("$.carrier").value("YAMATO"))
                    .andExpect(jsonPath("$.trackingNumber").value("123456789012"))
                    .andExpect(jsonPath("$.status").value("SHIPPED"))
                    .andExpect(jsonPath("$.shippedAt").value("2026-10-07T16:00:00Z"));

            mockMvc.perform(get("/api/admin/orders/" + orderId))
                    .andExpect(jsonPath("$.status").value("SHIPPED"))
                    .andExpect(jsonPath("$.payment.status").value("CAPTURED"))
                    .andExpect(jsonPath("$.shipment.trackingNumber").value("123456789012"))
                    .andExpect(jsonPath("$.actions.ship").value(false))
                    .andExpect(jsonPath("$.actions.cancel").value(false));
            assertThat(gateway.captured()).containsExactly(orderId);
        }

        @Test
        @DisplayName("AC-167 「受付」の注文の詳細は、出荷を登録できる")
        void shippableAction() throws Exception {
            mockMvc.perform(get("/api/admin/orders/" + orderId))
                    .andExpect(jsonPath("$.actions.ship").value(true));
        }
    }

    @Nested
    @DisplayName("AC-169、188 売上確定の失敗")
    class CaptureFailure {

        @Test
        @DisplayName("AC-169、188 与信期限切れなら 409 で、出荷は作られず受付のまま。支払は与信期限切れになる")
        void authorizationExpired() throws Exception {
            gateway.expireAuthorization(gateway.authorizationRefOf(orders.paidPageId(orderId)));

            ship("YAMATO", "123456789012").andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errorCode").value("AUTHORIZATION_EXPIRED"));

            mockMvc.perform(get("/api/admin/orders/" + orderId))
                    .andExpect(jsonPath("$.status").value("ACCEPTED"))
                    .andExpect(jsonPath("$.payment.status").value("AUTHORIZATION_EXPIRED"))
                    .andExpect(jsonPath("$.shipment").doesNotExist())
                    .andExpect(jsonPath("$.actions.cancel").value(true));
        }

        @Test
        @DisplayName("AC-169 決済サービスに接続できなければ 502 で、出荷は作られず受付のまま。支払は与信済みのまま")
        void gatewayUnavailable() throws Exception {
            gateway.failNextCaptures(1);

            ship("YAMATO", "123456789012").andExpect(status().isBadGateway())
                    .andExpect(jsonPath("$.errorCode").value("PAYMENT_GATEWAY_UNAVAILABLE"));

            mockMvc.perform(get("/api/admin/orders/" + orderId))
                    .andExpect(jsonPath("$.status").value("ACCEPTED"))
                    .andExpect(jsonPath("$.payment.status").value("AUTHORIZED"))
                    .andExpect(jsonPath("$.shipment").doesNotExist());

            // やり直せば出荷できる
            ship("YAMATO", "123456789012").andExpect(status().isCreated());
        }
    }

    @Nested
    @DisplayName("AC-170、172 登録できない注文と、重ねた登録")
    class Conflict {

        @Test
        @DisplayName("AC-170 「キャンセル申請中」の注文は 409 で、売上を確定しない。却下すると出荷できる")
        void rejectsCancellationRequested() throws Exception {
            requestCancellation();

            ship("YAMATO", "123456789012").andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errorCode").value("ORDER_NOT_SHIPPABLE"));
            assertThat(gateway.captureCalls()).isZero();

            mockMvc.perform(put("/api/admin/orders/" + orderId + "/cancellation-request/reject"))
                    .andExpect(status().isNoContent());
            ship("YAMATO", "123456789012").andExpect(status().isCreated());
        }

        @Test
        @DisplayName("AC-172 出荷済みの注文に重ねて登録すると 409 で、売上確定は 1 回だけ")
        void rejectsShippedOrder() throws Exception {
            ship("YAMATO", "123456789012").andExpect(status().isCreated());

            ship("SAGAWA", "999999999999").andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errorCode").value("ORDER_NOT_SHIPPABLE"));

            assertThat(gateway.captureCalls()).isEqualTo(1);
            mockMvc.perform(get("/api/admin/orders/" + orderId))
                    .andExpect(jsonPath("$.shipment.carrier").value("YAMATO"));
        }

        @Test
        @DisplayName("AC-172 売上確定の後に注文が受付でなくなっていたら 409。売上確定済みのまま残り、登録し直すと確定せずに進む")
        void orderChangedDuringCapture() throws Exception {
            gateway.duringNextCapture(() -> {
                try {
                    requestCancellation();
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            });

            ship("YAMATO", "123456789012").andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errorCode").value("ORDER_NOT_SHIPPABLE"));
            mockMvc.perform(get("/api/admin/orders/" + orderId))
                    .andExpect(jsonPath("$.status").value("CANCELLATION_REQUESTED"))
                    .andExpect(jsonPath("$.payment.status").value("CAPTURED"))
                    .andExpect(jsonPath("$.shipment").doesNotExist());

            mockMvc.perform(put("/api/admin/orders/" + orderId + "/cancellation-request/reject"))
                    .andExpect(status().isNoContent());
            ship("YAMATO", "123456789012").andExpect(status().isCreated());

            assertThat(gateway.captureCalls()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("AC-173 入力")
    class Input {

        @Test
        @DisplayName("AC-173 配送業者は一覧から選び、送り状番号は必須で、数字とハイフンの 30 文字まで。誤りは 400 で売上を確定しない")
        void validates() throws Exception {
            ship("KURONEKO", "123456789012").andExpect(status().isBadRequest());
            ship("YAMATO", "").andExpect(status().isBadRequest());
            ship("YAMATO", "ABC-123").andExpect(status().isBadRequest());
            ship("YAMATO", "1".repeat(31)).andExpect(status().isBadRequest());
            mockMvc.perform(post("/api/admin/orders/" + orderId + "/shipment")
                            .contentType(MediaType.APPLICATION_JSON).content("""
                                    {"carrier": "YAMATO"}"""))
                    .andExpect(status().isBadRequest());

            assertThat(gateway.captureCalls()).isZero();
        }

        @Test
        @DisplayName("存在しない注文は 404")
        void notFound() throws Exception {
            mockMvc.perform(post("/api/admin/orders/" + UUID.randomUUID() + "/shipment")
                            .contentType(MediaType.APPLICATION_JSON).content("""
                                    {"carrier": "YAMATO", "trackingNumber": "123456789012"}"""))
                    .andExpect(status().isNotFound());
        }
    }

    // ============================== 補助 ==============================

    /**
     * 出荷を登録する
     */
    private ResultActions ship(String carrier, String trackingNumber) throws Exception {
        return mockMvc.perform(post("/api/admin/orders/" + orderId + "/shipment")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"carrier": "%s", "trackingNumber": "%s"}""".formatted(carrier, trackingNumber)));
    }

    /**
     * 消費者がキャンセルを申請する
     */
    private void requestCancellation() throws Exception {
        mockMvc.perform(put("/api/store/orders/" + orderId + "/cancellation-request")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNoContent());
    }
}
