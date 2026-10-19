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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import static com.ecsite.jooq.ordering.Tables.SALES_ORDER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * キャンセルの受け入れテスト（T2403、AC-95、96、101〜103、105、107）。<br />
 * 在庫の戻しと注文の取り消しが一緒に起きること（AC-104）は、コミットが必要なので {@code CancellationAtomicityTest} で確かめる。
 * テストごとにロールバックする。
 */
@IntegrationTest
@AutoConfigureMockMvc
@Import(TestClockConfig.class)
@Transactional
class CancellationApiTest {

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

    private UUID variantId;

    private UUID orderId;

    @BeforeEach
    void setUp() throws Exception {
        clock.setInstant(NOW);
        ShippingTaxCategory.ensure(dsl);
        var data = new CatalogTestData(mockMvc, clock);
        var product = data.product(data.taxCategory("標準", "10"), "商品", 1000).publish();
        variantId = product.variantIds.getFirst();
        stockItemRepository.createIfAbsent(variantId, product.id);
        stockItemRepository.adjust(variantId, StockAdjustment.create(5, StockAdjustmentReason.ARRIVAL, null, TestTokens.OPERATOR, NOW));
        orderId = new OrderTestData(mockMvc, gateway, orderPaymentService).placeOrder(variantId, 2);
    }

    @Nested
    @DisplayName("AC-95、96 キャンセルの申請（C 側）")
    class Request {

        @Test
        @DisplayName("AC-95 「受付」の注文を申請すると「キャンセル申請中」になり、在庫はまだ戻らない")
        void requests() throws Exception {
            request("サイズを間違えました").andExpect(status().isNoContent());

            mockMvc.perform(get("/api/store/orders/" + orderId))
                    .andExpect(jsonPath("$.status").value("CANCELLATION_REQUESTED"))
                    .andExpect(jsonPath("$.cancellationRequest.result").value("PENDING"))
                    .andExpect(jsonPath("$.cancellationRequest.reason").value("サイズを間違えました"));
            expectQuantity(3);
        }

        @Test
        @DisplayName("AC-96 申請中にもう一度申請しても 204 で、何も変わらない。却下された注文は 400")
        void requestsOnlyOnce() throws Exception {
            request("最初").andExpect(status().isNoContent());
            request("次").andExpect(status().isNoContent());
            mockMvc.perform(get("/api/store/orders/" + orderId))
                    .andExpect(jsonPath("$.cancellationRequest.reason").value("最初"));

            admin("cancellation-request/reject").andExpect(status().isNoContent());

            request("もう一度").andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("CANCELLATION_NOT_REQUESTABLE"));
            mockMvc.perform(get("/api/store/orders/" + orderId))
                    .andExpect(jsonPath("$.status").value("ACCEPTED"))
                    .andExpect(jsonPath("$.cancellationRequest.result").value("REJECTED"));
        }

        @Test
        @DisplayName("理由は任意。存在しない注文は 404")
        void requestsWithoutReason() throws Exception {
            mockMvc.perform(put("/api/store/orders/" + orderId + "/cancellation-request")
                            .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isNoContent());
            mockMvc.perform(put("/api/store/orders/" + UUID.randomUUID() + "/cancellation-request")
                            .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("AC-101、102、103、105 承認・却下・運営者のキャンセル（B 側）")
    class Decide {

        @Test
        @DisplayName("AC-101、107 申請を承認するとキャンセルになり、在庫が戻り、調整履歴に理由「注文のキャンセル」が残る")
        void approves() throws Exception {
            request(null).andExpect(status().isNoContent());

            admin("cancellation-request/approve").andExpect(status().isNoContent());

            mockMvc.perform(get("/api/store/orders/" + orderId)).andExpect(jsonPath("$.status").value("CANCELLED"));
            expectQuantity(5);
            mockMvc.perform(get("/api/admin/inventory/variants/" + variantId + "/adjustments"))
                    .andExpect(jsonPath("$.items[0].reason").value("ORDER_CANCELLATION"))
                    .andExpect(jsonPath("$.items[0].delta").value(2));
        }

        @Test
        @DisplayName("AC-102 申請を却下すると「受付」に戻り、在庫は変わらない")
        void rejects() throws Exception {
            request(null).andExpect(status().isNoContent());

            admin("cancellation-request/reject").andExpect(status().isNoContent());

            mockMvc.perform(get("/api/store/orders/" + orderId)).andExpect(jsonPath("$.status").value("ACCEPTED"));
            expectQuantity(3);
        }

        @Test
        @DisplayName("AC-103 運営者の判断で、理由を記録してキャンセルできる。在庫が戻る")
        void cancelsByOperator() throws Exception {
            cancel("在庫の不備").andExpect(status().isNoContent());

            mockMvc.perform(get("/api/store/orders/" + orderId)).andExpect(jsonPath("$.status").value("CANCELLED"));
            expectQuantity(5);
        }

        @Test
        @DisplayName("AC-103 申請中の注文も、運営者の判断でキャンセルできる")
        void cancelsRequestedOrder() throws Exception {
            request(null).andExpect(status().isNoContent());

            cancel("お客様の申請による").andExpect(status().isNoContent());

            mockMvc.perform(get("/api/store/orders/" + orderId))
                    .andExpect(jsonPath("$.status").value("CANCELLED"))
                    .andExpect(jsonPath("$.cancellationRequest.result").value("APPROVED"));
        }

        @Test
        @DisplayName("AC-103 運営者のキャンセルの理由がなければ 400")
        void requiresReason() throws Exception {
            cancel("").andExpect(status().isBadRequest());
            mockMvc.perform(put("/api/admin/orders/" + orderId + "/cancel")
                            .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isBadRequest());

            mockMvc.perform(get("/api/store/orders/" + orderId)).andExpect(jsonPath("$.status").value("ACCEPTED"));
        }

        @Test
        @DisplayName("AC-105 キャンセル済みの注文へのキャンセル・承認は 204 で、在庫を戻さない。却下は 400")
        void isIdempotentForCancelledOrder() throws Exception {
            cancel("在庫の不備").andExpect(status().isNoContent());

            cancel("もう一度").andExpect(status().isNoContent());
            admin("cancellation-request/approve").andExpect(status().isNoContent());
            admin("cancellation-request/reject").andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("CANCELLATION_NOT_PENDING"));

            expectQuantity(5);
        }

        @Test
        @DisplayName("申請のない注文の承認・却下は 400。存在しない注文は 404")
        void rejectsDecisionWithoutRequest() throws Exception {
            admin("cancellation-request/approve").andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("CANCELLATION_NOT_PENDING"));
            mockMvc.perform(put("/api/admin/orders/" + UUID.randomUUID() + "/cancellation-request/approve"))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("AC-142 操作した運営者")
    class Operator {

        /**
         * 判断した運営者
         */
        private static final UUID JUDGE = UUID.fromString("00000000-0000-0000-0000-0000000a0101");

        /**
         * キャンセルした運営者
         */
        private static final UUID CANCELLER = UUID.fromString("00000000-0000-0000-0000-0000000a0102");

        @Test
        @DisplayName("AC-142 申請を承認すると、承認した運営者を、判断した運営者とキャンセルした運営者に残す")
        void recordsApprover() throws Exception {
            request(null).andExpect(status().isNoContent());

            asOperator(put("/api/admin/orders/" + orderId + "/cancellation-request/approve"), JUDGE, "審査", "一郎")
                    .andExpect(status().isNoContent());

            mockMvc.perform(get("/api/admin/orders/" + orderId))
                    .andExpect(jsonPath("$.cancellationRequest.decidedBy").value("審査 一郎"))
                    .andExpect(jsonPath("$.cancellation.cancelledBy").value("審査 一郎"));
            expectOperatorIds(JUDGE, JUDGE);
        }

        @Test
        @DisplayName("AC-142 却下した後に運営者がキャンセルすると、判断した運営者とキャンセルした運営者を別々に残す")
        void recordsJudgeAndCancellerSeparately() throws Exception {
            request(null).andExpect(status().isNoContent());
            asOperator(put("/api/admin/orders/" + orderId + "/cancellation-request/reject"), JUDGE, "審査", "一郎")
                    .andExpect(status().isNoContent());

            asOperator(put("/api/admin/orders/" + orderId + "/cancel")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"reason": "在庫の不備"}"""), CANCELLER, "取消", "二郎")
                    .andExpect(status().isNoContent());

            mockMvc.perform(get("/api/admin/orders/" + orderId))
                    .andExpect(jsonPath("$.cancellationRequest.result").value("REJECTED"))
                    .andExpect(jsonPath("$.cancellationRequest.decidedBy").value("審査 一郎"))
                    .andExpect(jsonPath("$.cancellation.cancelledBy").value("取消 二郎"));
            expectOperatorIds(JUDGE, CANCELLER);
        }

        @Test
        @DisplayName("AC-142 申請中の判断前は、判断した運営者は空")
        void leavesPendingRequestEmpty() throws Exception {
            request(null).andExpect(status().isNoContent());

            mockMvc.perform(get("/api/admin/orders/" + orderId))
                    .andExpect(jsonPath("$.cancellationRequest.decidedBy").value(nullValue()));
        }

        /**
         * 指定した運営者の token で送る
         */
        private ResultActions asOperator(MockHttpServletRequestBuilder builder, UUID subject, String familyName,
                                         String givenName) throws Exception {
            return mockMvc.perform(builder.with(TestTokens.administrator().subject(subject).name(familyName, givenName).bearer()));
        }

        /**
         * 注文に残った、判断した運営者 ID とキャンセルした運営者 ID を確かめる
         */
        private void expectOperatorIds(UUID decidedBy, UUID cancelledBy) {
            var order = dsl.select(SALES_ORDER.CANCELLATION_DECIDED_BY_ID, SALES_ORDER.CANCELLED_BY_ID)
                    .from(SALES_ORDER)
                    .where(SALES_ORDER.ID.eq(orderId))
                    .fetchSingle();
            assertThat(order.value1()).isEqualTo(decidedBy);
            assertThat(order.value2()).isEqualTo(cancelledBy);
        }
    }

    // ============================== 補助 ==============================

    /**
     * C 側からキャンセルを申請する
     */
    private ResultActions request(String reason) throws Exception {
        var body = reason == null ? "{}" : """
                {"reason": "%s"}""".formatted(reason);
        return mockMvc.perform(put("/api/store/orders/" + orderId + "/cancellation-request")
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    /**
     * B 側の操作（承認・却下）を送る
     */
    private ResultActions admin(String action) throws Exception {
        return mockMvc.perform(put("/api/admin/orders/" + orderId + "/" + action));
    }

    /**
     * B 側で、理由を指定して運営者のキャンセルを送る
     */
    private ResultActions cancel(String reason) throws Exception {
        return mockMvc.perform(put("/api/admin/orders/" + orderId + "/cancel")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"reason": "%s"}""".formatted(reason)));
    }

    /**
     * 在庫数を確かめる
     */
    private void expectQuantity(int quantity) throws Exception {
        mockMvc.perform(get("/api/admin/inventory/variants/" + variantId)).andExpect(jsonPath("$.quantity").value(quantity));
    }
}
