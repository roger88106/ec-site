package com.ecsite.module.order.presentation;

import com.ecsite.module.order.application.OrderProperties;
import com.ecsite.support.config.IntegrationTest;
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

import static com.ecsite.support.data.OrderTestData.startCheckoutRequest;
import static com.ecsite.support.http.ProblemAssertions.problem;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 注文の総額の範囲による、注文手続きの開始の拒否の受け入れテスト
 *
 * <p>総額は、商品の価格と数量、注文の設定の送料で作る（{@link OrderTotalFixture}）。
 * 注文の時点の範囲は {@code PlaceOrderAmountRangeApiTest} が確認する。
 *
 * <p>テストごとにロールバックする。
 */
@IntegrationTest
@Transactional
class CheckoutAmountRangeApiTest {

    // ============================== 定数 ==============================

    /**
     * 範囲の外のエラーコード
     */
    private static final String ERROR_CODE = "ORDER_AMOUNT_OUT_OF_RANGE";

    /**
     * 範囲の外の文言（値は桁区切りで差し込まれる）
     */
    private static final String MESSAGE = "注文の総額は 50〜99,999,999 円にしてください。カートの商品か数量を変えてください。";

    // ============================== Bean ==============================

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ApplicationContext applicationContext;

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

    /**
     * テストの会員のカート ID
     */
    private UUID cartId;

    @BeforeEach
    void setUp() throws Exception {
        fixture = new CheckoutStartFixture(applicationContext);
        totals = new OrderTotalFixture(fixture, properties, mockMvc);
        cartId = fixture.cartId();
    }

    // ============================== テスト ==============================

    @Nested
    @DisplayName("範囲の外")
    class OutOfRange {

        @Test
        @DisplayName("AC-518-1、AC-518-5 総額が下限の外（49 円）だと、開始は 400 で拒否し、注文手続きも引当も作らない")
        void rejectsBelowMinimum() throws Exception {
            // 準備：総額 49 円のカート
            var variant = totals.fillCartWithTotal(49);
            var stock = fixture.sellable(variant);
            var checkouts = fixture.checkoutCount();

            // 操作と確認：範囲の外のエラーで拒否される
            mockMvc.perform(startCheckoutRequest(cartId))
                    .andExpect(status().isBadRequest())
                    .andExpectAll(problem(ERROR_CODE, MESSAGE));

            // 確認：注文手続きを作らず、引当もしない
            assertThat(fixture.checkoutCount()).isEqualTo(checkouts);
            assertThat(fixture.sellable(variant)).isEqualTo(stock);
        }

        @Test
        @DisplayName("AC-518-2、AC-518-5 総額が上限の外（100,000,000 円）だと、開始は 400 で拒否し、注文手続きも引当も作らない")
        void rejectsAboveMaximum() throws Exception {
            // 準備：総額 100,000,000 円のカート
            var variant = totals.fillCartWithTotal(100_000_000);
            var stock = fixture.sellable(variant);
            var checkouts = fixture.checkoutCount();

            // 操作と確認：範囲の外のエラーで拒否される
            mockMvc.perform(startCheckoutRequest(cartId))
                    .andExpect(status().isBadRequest())
                    .andExpectAll(problem(ERROR_CODE, MESSAGE));

            // 確認：注文手続きを作らず、引当もしない
            assertThat(fixture.checkoutCount()).isEqualTo(checkouts);
            assertThat(fixture.sellable(variant)).isEqualTo(stock);
        }
    }

    @Nested
    @DisplayName("範囲の境界")
    class Boundary {

        @Test
        @DisplayName("AC-518-4 総額が下限ちょうど（50 円）なら、開始できて、引当もする")
        void startsAtMinimum() throws Exception {
            // 準備：総額 50 円のカート
            var variant = totals.fillCartWithTotal(50);
            var stock = fixture.sellable(variant);

            // 操作：注文手続きに進む
            fixture.startCheckout(cartId);

            // 確認：カートの数量だけ引き当てている
            assertThat(fixture.sellable(variant)).isEqualTo(stock - 1);
        }

        @Test
        @DisplayName("AC-518-4 総額が上限ちょうど（99,999,999 円）なら、開始できて、引当もする")
        void startsAtMaximum() throws Exception {
            // 準備：総額 99,999,999 円のカート
            var variant = totals.fillCartWithTotal(99_999_999);
            var stock = fixture.sellable(variant);

            // 操作：注文手続きに進む
            fixture.startCheckout(cartId);

            // 確認：カートの数量だけ引き当てている
            assertThat(fixture.sellable(variant)).isEqualTo(stock - 10);
        }
    }
}
