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

import static com.ecsite.module.order.presentation.OrderTotalFixture.OUT_OF_RANGE_ERROR_CODE;
import static com.ecsite.module.order.presentation.OrderTotalFixture.OUT_OF_RANGE_MESSAGE;
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
            rejectsAtTotal(49);
        }

        @Test
        @DisplayName("AC-518-2、AC-518-5 総額が上限の外（100,000,000 円）だと、開始は 400 で拒否し、注文手続きも引当も作らない")
        void rejectsAboveMaximum() throws Exception {
            rejectsAtTotal(100_000_000);
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

    // ============================== 操作の補助 ==============================

    /**
     * 指定の総額のカートで注文手続きを開始すると拒否されることを確認する。
     *
     * <p>確認すること：
     * <ul>
     *   <li>状態が 400 で、エラーコードと文言が範囲の外のもの</li>
     *   <li>注文手続きが作られない</li>
     *   <li>販売可能数が変わらない（引当をしない）</li>
     * </ul>
     *
     * @param total カートの総額
     */
    private void rejectsAtTotal(long total) throws Exception {
        // 準備：範囲の外の総額のカート
        var variant = totals.fillCartWithTotal(total);
        var stock = fixture.sellable(variant);
        var checkouts = fixture.checkoutCount();

        // 操作と確認：範囲の外のエラーで拒否される
        mockMvc.perform(startCheckoutRequest(cartId))
                .andExpect(status().isBadRequest())
                .andExpectAll(problem(OUT_OF_RANGE_ERROR_CODE, OUT_OF_RANGE_MESSAGE));

        // 確認：注文手続きを作らず、引当もしない
        assertThat(fixture.checkoutCount()).isEqualTo(checkouts);
        assertThat(fixture.sellable(variant)).isEqualTo(stock);
    }
}
