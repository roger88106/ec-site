package com.ecsite.module.order.presentation;

import com.ecsite.module.order.application.OrderProperties;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/**
 * 狙った総額のカートを作る補助
 *
 * <p>注文の総額の範囲の受け入れテスト（注文手続きの開始と注文）が共有する。
 * 商品の価格（税抜、標準税率 10%）と数量、注文の設定の送料で総額を作る。
 * 送料は、注文の設定の {@link org.springframework.test.context.bean.override.mockito.MockitoSpyBean} で差し替える。
 *
 * <p>総額は、税込単価（税抜 × 1.1 の端数切り捨て）× 数量 + 送料。作った後に、カートの応答の総額を読んで確認する。
 * 総額が大きいとき（大きい品目の小計以上）は、税込の単価が上限に近い品目を、カートの 1 品目の数量の上限まで入れて小計を最大にする。
 * そうでなければ、範囲の下限に近い小さい総額も作れるよう、税込の小計が小さい品目を 1 個だけ入れる。
 * 総額と小計の差は送料にする。どちらの品目も、カートの数量と税抜の価格の上限の中にある。
 */
final class OrderTotalFixture {

    /**
     * 範囲の外のエラーコード
     */
    static final String OUT_OF_RANGE_ERROR_CODE = "ORDER_AMOUNT_OUT_OF_RANGE";

    /**
     * 範囲の外の文言（値は桁区切りで差し込まれる）
     */
    static final String OUT_OF_RANGE_MESSAGE = "注文の総額は 50〜99,999,999 円にしてください。カートの商品か数量を変えてください。";

    /**
     * 大きい総額の品目の税抜価格（税込 9,999,999 円）
     */
    private static final long HIGH_PRICE_EXCL_TAX = 9_090_909L;

    /**
     * 大きい総額の品目の数量
     */
    private static final int HIGH_QUANTITY = 10;

    /**
     * 大きい総額の品目の小計（税込）
     */
    private static final long HIGH_ITEMS_TOTAL = 99_999_990L;

    /**
     * 小さい総額の品目の税抜価格（税込 44 円）
     */
    private static final long LOW_PRICE_EXCL_TAX = 40L;

    /**
     * 小さい総額の品目の小計（税込）
     */
    static final long LOW_ITEMS_TOTAL = 44L;

    /**
     * 小さい総額の品目の数量（在庫にも同じ数量を入れる）
     */
    static final int LOW_QUANTITY = 1;

    /**
     * 注文手続きの開始の準備の補助
     */
    private final CheckoutStartFixture fixture;

    /**
     * 注文の設定（送料を差し替える）
     */
    private final OrderProperties properties;

    /**
     * API を呼ぶ MockMvc
     */
    private final MockMvc mockMvc;

    /**
     * 補助を作る。
     *
     * @param fixture    注文手続きの開始の準備の補助
     * @param properties 注文の設定（{@code @MockitoSpyBean} で差し替えられるもの）
     * @param mockMvc    API を呼ぶ MockMvc
     */
    OrderTotalFixture(CheckoutStartFixture fixture, OrderProperties properties, MockMvc mockMvc) {
        this.fixture = fixture;
        this.properties = properties;
        this.mockMvc = mockMvc;
    }

    /**
     * 総額が指定の額になるカートを作り、その品目のバリアント ID を返す。
     *
     * <p>送料を差し替え、品目をカートに入れ、カートの応答の総額が狙った額であることを確認する。
     *
     * @param total 狙う総額（税込、円。品目の小計より小さい額は指定できない）
     * @return 品目のバリアント ID
     */
    UUID fillCartWithTotal(long total) throws Exception {
        var high = total >= HIGH_ITEMS_TOTAL;
        var price = high ? HIGH_PRICE_EXCL_TAX : LOW_PRICE_EXCL_TAX;
        var quantity = high ? HIGH_QUANTITY : LOW_QUANTITY;
        var itemsTotal = high ? HIGH_ITEMS_TOTAL : LOW_ITEMS_TOTAL;
        var shippingFee = total - itemsTotal;
        if (shippingFee < 0) {
            throw new IllegalArgumentException("総額が品目の小計より小さい: " + total);
        }

        doReturn(shippingFee).when(properties).shippingFee();
        var product = fixture.catalog().product(fixture.standard(), "総額の範囲", price).publish();
        var variant = product.variantIds.getFirst();
        fixture.inventory().stock(variant, product.id, quantity);
        fixture.orders().add(fixture.cartId(), variant, quantity);

        // 前提の確認：税込の端数の扱いを含めて、狙った総額になっている
        mockMvc.perform(get("/api/store/carts/" + fixture.cartId()))
                .andExpect(jsonPath("$.amounts.itemsTotal").value(itemsTotal))
                .andExpect(jsonPath("$.amounts.total").value(total));
        return variant;
    }
}
