package com.ecsite.module.reporting.domain.sales;

import lombok.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * 集計の範囲
 *
 * <p>商品を選んだら、その商品の明細だけを数え、送料は含めない（指定によらず送料を除く）。
 *
 * @param productId       商品 ID。全商品なら null
 * @param excludeTax      消費税を除くか
 * @param excludeShipping 送料を除く指定（商品を選んだときは使わず、いつも除く）
 */
public record SalesScope(@Nullable UUID productId, boolean excludeTax, boolean excludeShipping) {

    /**
     * 商品を選ぶ範囲を作る。
     *
     * <p>明細だけを数えるので、送料はいつも除く。
     *
     * @param productId  商品 ID
     * @param excludeTax 消費税を除くか
     * @return 商品を選ぶ範囲
     */
    public static SalesScope ofProduct(@NonNull UUID productId, boolean excludeTax) {
        return new SalesScope(productId, excludeTax, true);
    }

    /**
     * 商品を選んだか。
     *
     * @return 商品を選んだなら true
     */
    public boolean hasProduct() {
        return productId != null;
    }

    /**
     * 売上額から送料を除くか。
     *
     * <p>商品を選んだら、指定によらず除く。選ばなければ、指定のとおり。
     *
     * @return 送料を除くなら true
     */
    public boolean shippingExcluded() {
        return hasProduct() || excludeShipping;
    }
}
