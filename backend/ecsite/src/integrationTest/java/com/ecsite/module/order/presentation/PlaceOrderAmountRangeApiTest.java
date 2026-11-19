package com.ecsite.module.order.presentation;

import com.ecsite.module.order.application.OrderProperties;
import com.ecsite.support.config.IntegrationTest;
import com.ecsite.support.http.TestJson;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static com.ecsite.jooq.ordering.Tables.SALES_ORDER;
import static com.ecsite.jooq.payment.Tables.PAYMENT_;
import static com.ecsite.module.order.presentation.OrderTotalFixture.LOW_ITEMS_TOTAL;
import static com.ecsite.module.order.presentation.OrderTotalFixture.LOW_QUANTITY;
import static com.ecsite.module.order.presentation.OrderTotalFixture.OUT_OF_RANGE_ERROR_CODE;
import static com.ecsite.module.order.presentation.OrderTotalFixture.OUT_OF_RANGE_MESSAGE;
import static com.ecsite.support.data.InventoryTestData.expectQuantity;
import static com.ecsite.support.data.OrderTestData.placeOrderRequest;
import static com.ecsite.support.http.ProblemAssertions.problem;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 注文の総額の範囲による、注文の拒否の受け入れテスト
 *
 * <p>総額は、商品の価格と数量、注文の設定の送料で作る（{@link OrderTotalFixture}）。
 * 注文手続きを開始するときは範囲の中の総額にし、開始の後に送料を変えて総額を範囲の外にする。
 * 注文手続きの開始の範囲は {@code CheckoutAmountRangeApiTest} が確認する。
 *
 * <p>テストごとにロールバックする。
 */
@IntegrationTest
@Transactional
class PlaceOrderAmountRangeApiTest {

    // ============================== 定数 ==============================

    /**
     * 開始の時点の総額（範囲の中）
     */
    private static final long START_TOTAL = 100;

    // ============================== Bean ==============================

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ApplicationContext applicationContext;

    @Autowired
    DSLContext dsl;

    /**
     * 注文の設定
     *
     * <p>送料を差し替えて、総額を作る。
     */
    @MockitoSpyBean
    OrderProperties properties;

    /**
     * 注文手続きの開始の準備と確認の補助
     */
    private CheckoutStartFixture fixture;

    /**
     * 狙った総額のカートを作る補助
     */
    private OrderTotalFixture totals;

    @BeforeEach
    void setUp() throws Exception {
        fixture = new CheckoutStartFixture(applicationContext);
        totals = new OrderTotalFixture(fixture, properties, mockMvc);
    }

    // ============================== テスト ==============================

    @Nested
    @DisplayName("範囲の外")
    class OutOfRange {

        @Test
        @DisplayName("AC-518-3、AC-518-5 開始の後に総額が下限の外（49 円）になると、新しい総額で注文しても 400 で拒否し、注文も支払も作らず、在庫も変えない")
        void rejectsBelowMinimum() throws Exception {
            rejectsAfterTotalChangedTo(49);
        }

        @Test
        @DisplayName("AC-518-3、AC-518-5 開始の後に総額が上限の外（100,000,000 円）になると、新しい総額で注文しても 400 で拒否し、注文も支払も作らず、在庫も変えない")
        void rejectsAboveMaximum() throws Exception {
            rejectsAfterTotalChangedTo(100_000_000);
        }
    }

    @Nested
    @DisplayName("範囲の境界")
    class Boundary {

        @Test
        @DisplayName("AC-518-4 総額が下限ちょうど（50 円）なら、注文できる")
        void placesAtMinimum() throws Exception {
            // 準備：総額 50 円の注文手続き
            totals.fillCartWithTotal(50);
            var checkoutId = fixture.startCheckout(fixture.cartId());

            // 操作と確認：確認した総額で注文でき、保存した総額は 50 円
            var orderId = place(checkoutId, 50);
            assertThat(orderTotal(orderId)).isEqualTo(50);
            assertThat(paymentAmount(orderId)).isEqualTo(50);
        }

        @Test
        @DisplayName("AC-518-4 総額が上限ちょうど（99,999,999 円）なら、注文でき、保存した注文の総額と支払の金額は 99,999,999 円")
        void placesAtMaximum() throws Exception {
            // 準備：総額 99,999,999 円の注文手続き
            totals.fillCartWithTotal(99_999_999);
            var checkoutId = fixture.startCheckout(fixture.cartId());

            // 操作と確認：確認した総額で注文でき、保存した総額と支払の金額は 99,999,999 円
            var orderId = place(checkoutId, 99_999_999);
            assertThat(orderTotal(orderId)).isEqualTo(99_999_999);
            assertThat(paymentAmount(orderId)).isEqualTo(99_999_999);
        }
    }

    // ============================== 操作の補助 ==============================

    /**
     * 開始の後に総額を範囲の外にして、新しい総額で注文すると拒否されることを確認する。
     *
     * <p>確認すること：
     * <ul>
     *   <li>状態が 400 で、エラーコードと文言が範囲の外のもの</li>
     *   <li>注文と支払が作られない</li>
     *   <li>在庫数と販売可能数が変わらない</li>
     * </ul>
     *
     * @param newTotal 開始の後の総額
     */
    private void rejectsAfterTotalChangedTo(long newTotal) throws Exception {
        // 準備：範囲の中の総額で注文手続きを開始し、その後に送料を変えて総額を範囲の外にする
        var variant = totals.fillCartWithTotal(START_TOTAL);
        var checkoutId = fixture.startCheckout(fixture.cartId());
        doReturn(newTotal - LOW_ITEMS_TOTAL).when(properties).shippingFee();
        var stock = fixture.sellable(variant);
        var payments = dsl.fetchCount(PAYMENT_);

        // 操作と確認：新しい総額で注文すると、範囲の外のエラーで拒否される
        mockMvc.perform(placeOrderRequest(checkoutId, newTotal))
                .andExpect(status().isBadRequest())
                .andExpectAll(problem(OUT_OF_RANGE_ERROR_CODE, OUT_OF_RANGE_MESSAGE));

        // 確認：注文も支払も作られず、在庫数も販売可能数も変わらない
        assertThat(dsl.fetchCount(SALES_ORDER, SALES_ORDER.CHECKOUT_ID.eq(checkoutId))).isZero();
        assertThat(dsl.fetchCount(PAYMENT_)).isEqualTo(payments);
        expectQuantity(mockMvc, variant, LOW_QUANTITY);
        assertThat(fixture.sellable(variant)).isEqualTo(stock);
    }

    /**
     * 確認した総額で注文し、201 を確認して、注文 ID を返す。
     *
     * @param checkoutId    注文手続き ID
     * @param expectedTotal 確認した総額
     * @return 注文 ID
     */
    private UUID place(UUID checkoutId, long expectedTotal) throws Exception {
        var body = mockMvc.perform(placeOrderRequest(checkoutId, expectedTotal))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return TestJson.createdId(body);
    }

    // ============================== 状態の読み取り ==============================

    /**
     * 保存した注文の総額を DB から読む。
     *
     * @param orderId 注文 ID
     * @return 総額（円）
     */
    private long orderTotal(UUID orderId) {
        return dsl.select(SALES_ORDER.TOTAL_AMOUNT).from(SALES_ORDER).where(SALES_ORDER.ID.eq(orderId))
                .fetchOne(SALES_ORDER.TOTAL_AMOUNT);
    }

    /**
     * 注文の支払の金額を DB から読む。
     *
     * @param orderId 注文 ID
     * @return 支払の金額（円）
     */
    private long paymentAmount(UUID orderId) {
        return dsl.select(PAYMENT_.AMOUNT).from(PAYMENT_).where(PAYMENT_.ORDER_ID.eq(orderId))
                .fetchOne(PAYMENT_.AMOUNT);
    }
}
