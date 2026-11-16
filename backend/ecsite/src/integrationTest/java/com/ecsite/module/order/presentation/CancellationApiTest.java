package com.ecsite.module.order.presentation;

import com.ecsite.CatalogTestData;
import com.ecsite.IntegrationTest;
import com.ecsite.InventoryTestData;
import com.ecsite.MutableClock;
import com.ecsite.OrderTestData;
import com.ecsite.ShippingTaxCategory;
import com.ecsite.TestTokens;
import com.ecsite.module.inventory.domain.stock.port.StockItemRepository;
import com.ecsite.module.payment.infrastructure.mock.TestPaymentGateway;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
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
 * キャンセルの受け入れテスト
 *
 * <p>受付の注文の、消費者のキャンセルの申請と、運営者の承認・却下・キャンセルを、API を通して確認する。
 * 状態の遷移の規則は {@code OrderCancellationTest}、在庫の戻しと注文の取り消しが一緒に起きることは、
 * コミットが必要なので {@code CancellationAtomicityTest}、支払待ちと返送の注文のキャンセルは
 * {@code OrderPaymentApiTest}・{@code ReturnedOrderCancellationApiTest} が確認する。
 *
 * <p>テストごとにロールバックする。準備のデータと確認の期待値は、同じ定数から作る。
 */
@IntegrationTest
@Transactional
class CancellationApiTest {

    // ============================== 定数 ==============================

    /**
     * テストの現在時刻
     *
     * <p>日本時間の 2026-10-08 00:00。
     */
    private static final Instant NOW = Instant.parse("2026-10-07T15:00:00Z");

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

    // ============================== Bean ==============================

    @Autowired
    MockMvc mockMvc;

    @Autowired
    TestPaymentGateway gateway;

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
     * 受付の注文の ID
     */
    private UUID orderId;

    @BeforeEach
    void setUp() throws Exception {
        inventory = new InventoryTestData(stockItemRepository, NOW);
        ShippingTaxCategory.ensure(dsl);
        var catalog = new CatalogTestData(mockMvc, clock);
        var product = catalog.product(catalog.taxCategory("標準", "10"), "商品", 1000).publish();
        variantId = product.variantIds.getFirst();
        inventory.stock(variantId, product.id, STOCKED);
        orderId = new OrderTestData(mockMvc, gateway, applicationContext).placeOrder(variantId, ORDERED);
    }

    // ============================== テスト ==============================

    @Nested
    @DisplayName("キャンセルの申請（C 側）")
    class Request {

        @Test
        @DisplayName("AC-267 申請していない注文の店舗の応答は、キャンセルの申請を持たない")
        void hasNoRequestBeforeRequesting() throws Exception {
            // 操作と確認：申請していない受付の注文の応答に、キャンセルの申請がない
            store()
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("ACCEPTED"))
                    .andExpect(jsonPath("$.cancellationRequest").doesNotExist());
        }

        @Test
        @DisplayName("AC-95、267 「受付」の注文を申請すると「キャンセル申請中」になり、店舗の応答の申請は判断待ちで、申請の理由が残る")
        void requests() throws Exception {
            // 操作：理由を付けて申請する
            request("サイズを間違えました").andExpect(status().isNoContent());

            // 確認：申請中で、申請は判断待ち、理由が残る
            store()
                    .andExpect(jsonPath("$.status").value("CANCELLATION_REQUESTED"))
                    .andExpect(jsonPath("$.cancellationRequest.result").value("PENDING"))
                    .andExpect(jsonPath("$.cancellationRequest.reason").value("サイズを間違えました"));
        }

        @Test
        @DisplayName("AC-95 申請しても、在庫はまだ戻らない")
        void keepsStockOnRequest() throws Exception {
            // 操作：申請する
            request("サイズを間違えました").andExpect(status().isNoContent());

            // 確認：在庫数は注文の後のまま
            expectQuantity(AFTER_ORDER);
        }

        @Test
        @DisplayName("AC-95 理由がなくても申請できる")
        void requestsWithoutReason() throws Exception {
            // 操作と確認：理由のない本文で 204
            request(null).andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("存在しない注文への申請は 404")
        void rejectsUnknownOrder() throws Exception {
            // 操作と確認：ない注文 ID で 404
            mockMvc.perform(put("/api/store/orders/" + UUID.randomUUID() + "/cancellation-request")
                            .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("AC-96 申請中にもう一度申請しても 204 で、最初の申請のまま変わらない")
        void ignoresRepeatedRequest() throws Exception {
            // 準備：申請する
            request("最初").andExpect(status().isNoContent());

            // 操作：もう一度申請する
            request("次").andExpect(status().isNoContent());

            // 確認：理由は最初の申請のまま
            store().andExpect(jsonPath("$.cancellationRequest.reason").value("最初"));
        }

        @Test
        @DisplayName("AC-96、267 却下された注文は、もう一度申請できず、受付のままで、店舗の応答の申請は却下")
        void rejectsRequestAfterRejection() throws Exception {
            // 準備：申請して却下する
            request("最初").andExpect(status().isNoContent());
            admin("cancellation-request/reject").andExpect(status().isNoContent());

            // 操作と確認：申請できないことのエラーで 400
            request("もう一度").andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("CANCELLATION_NOT_REQUESTABLE"));

            // 確認：受付で、申請は却下のまま
            store()
                    .andExpect(jsonPath("$.status").value("ACCEPTED"))
                    .andExpect(jsonPath("$.cancellationRequest.result").value("REJECTED"));
        }
    }

    @Nested
    @DisplayName("承認・却下・運営者のキャンセル（B 側）")
    class Decide {

        @Test
        @DisplayName("AC-101 キャンセル申請中の注文を承認すると、キャンセルになる")
        void approves() throws Exception {
            // 準備：申請する
            request(null).andExpect(status().isNoContent());

            // 操作：承認する
            admin("cancellation-request/approve").andExpect(status().isNoContent());

            // 確認：キャンセル
            store().andExpect(jsonPath("$.status").value("CANCELLED"));
        }

        @Test
        @DisplayName("AC-101 申請を承認すると、在庫が戻る")
        void restocksOnApproval() throws Exception {
            // 準備：申請する
            request(null).andExpect(status().isNoContent());

            // 操作：承認する
            admin("cancellation-request/approve").andExpect(status().isNoContent());

            // 確認：入荷した数に戻る
            expectQuantity(STOCKED);
        }

        @Test
        @DisplayName("AC-107 申請の承認で戻した在庫は、調整履歴に理由「注文のキャンセル」で残る")
        void recordsRestoreInHistory() throws Exception {
            // 準備：申請する
            request(null).andExpect(status().isNoContent());

            // 操作：承認する
            admin("cancellation-request/approve").andExpect(status().isNoContent());

            // 確認：最新の調整が、注文のキャンセルによる注文の数量の戻し
            mockMvc.perform(get("/api/admin/inventory/variants/" + variantId + "/adjustments"))
                    .andExpect(jsonPath("$.items[0].reason").value("ORDER_CANCELLATION"))
                    .andExpect(jsonPath("$.items[0].delta").value(ORDERED));
        }

        @Test
        @DisplayName("AC-102 申請を却下すると「受付」に戻る")
        void rejects() throws Exception {
            // 準備：申請する
            request(null).andExpect(status().isNoContent());

            // 操作：却下する
            admin("cancellation-request/reject").andExpect(status().isNoContent());

            // 確認：受付
            store().andExpect(jsonPath("$.status").value("ACCEPTED"));
        }

        @Test
        @DisplayName("AC-102 申請を却下しても、在庫は変わらない")
        void keepsStockOnRejection() throws Exception {
            // 準備：申請する
            request(null).andExpect(status().isNoContent());

            // 操作：却下する
            admin("cancellation-request/reject").andExpect(status().isNoContent());

            // 確認：在庫数は注文の後のまま
            expectQuantity(AFTER_ORDER);
        }

        @Test
        @DisplayName("AC-103 運営者の判断で、理由を記録して受付の注文をキャンセルできる")
        void cancelsByOperator() throws Exception {
            // 操作：理由を付けてキャンセルする
            cancel("在庫の不備").andExpect(status().isNoContent());

            // 確認：キャンセル
            store().andExpect(jsonPath("$.status").value("CANCELLED"));
        }

        @Test
        @DisplayName("AC-103 運営者がキャンセルすると、在庫が戻る")
        void restocksOnOperatorCancellation() throws Exception {
            // 操作：キャンセルする
            cancel("在庫の不備").andExpect(status().isNoContent());

            // 確認：入荷した数に戻る
            expectQuantity(STOCKED);
        }

        @Test
        @DisplayName("AC-103、267 申請中の注文も、運営者の判断でキャンセルでき、店舗の応答の申請は承認になる")
        void cancelsRequestedOrder() throws Exception {
            // 準備：申請する
            request(null).andExpect(status().isNoContent());

            // 操作：運営者がキャンセルする
            cancel("お客様の申請による").andExpect(status().isNoContent());

            // 確認：キャンセルで、申請の結果は承認
            store()
                    .andExpect(jsonPath("$.status").value("CANCELLED"))
                    .andExpect(jsonPath("$.cancellationRequest.result").value("APPROVED"));
        }

        @ParameterizedTest(name = "「{0}」")
        @ValueSource(strings = {"{\"reason\": \"\"}", "{}"})
        @DisplayName("AC-103 運営者のキャンセルの理由がなければ 400 で、受付のまま")
        void requiresReason(String body) throws Exception {
            // 操作と確認：理由が空か、ない本文は 400
            mockMvc.perform(put("/api/admin/orders/" + orderId + "/cancel")
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());

            // 確認：受付のまま
            store().andExpect(jsonPath("$.status").value("ACCEPTED"));
        }

        @Test
        @DisplayName("AC-105 キャンセル済みの注文をもう一度キャンセルしても 204 で、在庫を戻さない")
        void ignoresRepeatedCancellation() throws Exception {
            // 準備：キャンセルする
            cancel("在庫の不備").andExpect(status().isNoContent());

            // 操作：もう一度キャンセルする
            cancel("もう一度").andExpect(status().isNoContent());

            // 確認：在庫は 1 回分だけ戻る
            expectQuantity(STOCKED);
        }

        @Test
        @DisplayName("AC-105 キャンセル済みの注文の承認は 204 で、在庫を戻さない")
        void ignoresApprovalOfCancelledOrder() throws Exception {
            // 準備：キャンセルする
            cancel("在庫の不備").andExpect(status().isNoContent());

            // 操作：承認する
            admin("cancellation-request/approve").andExpect(status().isNoContent());

            // 確認：在庫は 1 回分だけ戻る
            expectQuantity(STOCKED);
        }

        @Test
        @DisplayName("AC-105 キャンセル済みの注文の申請は却下できない")
        void rejectsRejectionOfCancelledOrder() throws Exception {
            // 準備：キャンセルする
            cancel("在庫の不備").andExpect(status().isNoContent());

            // 操作と確認：判断待ちの申請がないことのエラーで 400
            admin("cancellation-request/reject").andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("CANCELLATION_NOT_PENDING"));
        }

        @Test
        @DisplayName("申請のない注文の承認は 400")
        void rejectsApprovalWithoutRequest() throws Exception {
            // 操作と確認：判断待ちの申請がないことのエラーで 400
            admin("cancellation-request/approve").andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("CANCELLATION_NOT_PENDING"));
        }

        @Test
        @DisplayName("申請のない注文の承認を拒否したときは、注文は受付のままで、在庫を戻さない")
        void keepsOrderWhenApprovalRejected() throws Exception {
            // 操作：申請のない注文を承認して拒否される
            admin("cancellation-request/approve").andExpect(status().isBadRequest());

            // 確認：受付のままで、キャンセルの記録がなく、在庫数は注文の後のまま
            mockMvc.perform(get("/api/admin/orders/" + orderId))
                    .andExpect(jsonPath("$.status").value("ACCEPTED"))
                    .andExpect(jsonPath("$.cancellation").doesNotExist());
            expectQuantity(AFTER_ORDER);
        }

        @Test
        @DisplayName("存在しない注文の承認は 404")
        void rejectsApprovalOfUnknownOrder() throws Exception {
            // 操作と確認：ない注文 ID で 404
            mockMvc.perform(put("/api/admin/orders/" + UUID.randomUUID() + "/cancellation-request/approve"))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("操作した運営者")
    class Operator {

        /**
         * 判断した運営者
         */
        private static final UUID JUDGE = UUID.fromString("00000000-0000-0000-0000-0000000a0101");

        /**
         * 判断した運営者の氏名
         */
        private static final String JUDGE_NAME = "審査 一郎";

        /**
         * キャンセルした運営者
         */
        private static final UUID CANCELLER = UUID.fromString("00000000-0000-0000-0000-0000000a0102");

        /**
         * キャンセルした運営者の氏名
         */
        private static final String CANCELLER_NAME = "取消 二郎";

        @Test
        @DisplayName("AC-142 申請を承認すると、承認した運営者を、判断した運営者とキャンセルした運営者に残す")
        void recordsApprover() throws Exception {
            // 準備：申請する
            request(null).andExpect(status().isNoContent());

            // 操作：審査 一郎が承認する
            asOperator(put("/api/admin/orders/" + orderId + "/cancellation-request/approve"), JUDGE, "審査", "一郎")
                    .andExpect(status().isNoContent());

            // 確認：詳細に、判断とキャンセルの両方の運営者の氏名が出る
            mockMvc.perform(get("/api/admin/orders/" + orderId))
                    .andExpect(jsonPath("$.cancellationRequest.decidedBy").value(JUDGE_NAME))
                    .andExpect(jsonPath("$.cancellation.cancelledBy").value(JUDGE_NAME));

            // 確認：注文に、両方とも承認した運営者の ID が残る
            assertOperatorIds(JUDGE, JUDGE);
        }

        @Test
        @DisplayName("AC-142 却下した後に運営者がキャンセルすると、判断した運営者とキャンセルした運営者を別々に残す")
        void recordsJudgeAndCancellerSeparately() throws Exception {
            // 準備：申請し、審査 一郎が却下する
            request(null).andExpect(status().isNoContent());
            asOperator(put("/api/admin/orders/" + orderId + "/cancellation-request/reject"), JUDGE, "審査", "一郎")
                    .andExpect(status().isNoContent());

            // 操作：取消 二郎がキャンセルする
            asOperator(put("/api/admin/orders/" + orderId + "/cancel")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"reason": "在庫の不備"}"""), CANCELLER, "取消", "二郎")
                    .andExpect(status().isNoContent());

            // 確認：詳細に、却下した運営者とキャンセルした運営者が別々に出る
            mockMvc.perform(get("/api/admin/orders/" + orderId))
                    .andExpect(jsonPath("$.cancellationRequest.result").value("REJECTED"))
                    .andExpect(jsonPath("$.cancellationRequest.decidedBy").value(JUDGE_NAME))
                    .andExpect(jsonPath("$.cancellation.cancelledBy").value(CANCELLER_NAME));

            // 確認：注文に、それぞれの運営者の ID が残る
            assertOperatorIds(JUDGE, CANCELLER);
        }

        @Test
        @DisplayName("AC-142 申請中の判断前は、判断した運営者は空")
        void leavesPendingRequestEmpty() throws Exception {
            // 操作：申請する
            request(null).andExpect(status().isNoContent());

            // 確認：判断した運営者がない
            mockMvc.perform(get("/api/admin/orders/" + orderId))
                    .andExpect(jsonPath("$.cancellationRequest.decidedBy").value(nullValue()));
        }

        /**
         * 指定した運営者の token で送る。
         *
         * @param builder    送る要求
         * @param subject    運営者 ID
         * @param familyName 姓
         * @param givenName  名
         * @return 応答
         */
        private ResultActions asOperator(MockHttpServletRequestBuilder builder, UUID subject, String familyName,
                                         String givenName) throws Exception {
            return mockMvc.perform(builder.with(TestTokens.administrator().subject(subject).name(familyName, givenName).bearer()));
        }

        /**
         * 注文に残った運営者 ID を確認する。
         *
         * <p>確認すること：
         * <ul>
         *   <li>判断した運営者 ID</li>
         *   <li>キャンセルした運営者 ID</li>
         * </ul>
         *
         * @param decidedBy   期待する、判断した運営者 ID
         * @param cancelledBy 期待する、キャンセルした運営者 ID
         */
        private void assertOperatorIds(UUID decidedBy, UUID cancelledBy) {
            var order = dsl.select(SALES_ORDER.CANCELLATION_DECIDED_BY_ID, SALES_ORDER.CANCELLED_BY_ID)
                    .from(SALES_ORDER)
                    .where(SALES_ORDER.ID.eq(orderId))
                    .fetchSingle();
            assertThat(order.get(SALES_ORDER.CANCELLATION_DECIDED_BY_ID)).isEqualTo(decidedBy);
            assertThat(order.get(SALES_ORDER.CANCELLED_BY_ID)).isEqualTo(cancelledBy);
        }
    }

    // ============================== 確認の補助 ==============================

    /**
     * 店舗の API で注文の詳細を読む。
     *
     * @return 応答
     */
    private ResultActions store() throws Exception {
        return mockMvc.perform(get("/api/store/orders/" + orderId));
    }

    /**
     * 在庫数を確認する。
     *
     * @param quantity 期待する在庫数
     */
    private void expectQuantity(int quantity) throws Exception {
        InventoryTestData.expectQuantity(mockMvc, variantId, quantity);
    }

    // ============================== 準備の補助 ==============================

    /**
     * C 側からキャンセルを申請する。
     *
     * @param reason 理由。null なら理由のない本文で送る
     * @return 応答
     */
    private ResultActions request(String reason) throws Exception {
        var body = reason == null ? "{}" : """
                {"reason": "%s"}""".formatted(reason);
        return mockMvc.perform(put("/api/store/orders/" + orderId + "/cancellation-request")
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    /**
     * B 側の操作（承認・却下）を送る。
     *
     * @param action 注文の URL の後に続く操作のパス
     * @return 応答
     */
    private ResultActions admin(String action) throws Exception {
        return mockMvc.perform(put("/api/admin/orders/" + orderId + "/" + action));
    }

    /**
     * B 側で、理由を指定して運営者のキャンセルを送る。
     *
     * @param reason 理由
     * @return 応答
     */
    private ResultActions cancel(String reason) throws Exception {
        return mockMvc.perform(put("/api/admin/orders/" + orderId + "/cancel")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"reason": "%s"}""".formatted(reason)));
    }
}
