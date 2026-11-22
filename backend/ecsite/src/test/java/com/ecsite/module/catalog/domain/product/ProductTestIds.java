package com.ecsite.module.catalog.domain.product;

import com.ecsite.common.model.id.ProductId;
import com.ecsite.common.model.id.VariantId;

import java.util.UUID;

/**
 * 商品の集約のテストで使う、新しい識別子を作る補助
 */
final class ProductTestIds {

    private ProductTestIds() {
    }

    /**
     * 新しい商品の識別子を作る。
     *
     * @return 乱数の UUID を持つ商品の識別子
     */
    static ProductId newProductId() {
        return new ProductId(UUID.randomUUID());
    }

    /**
     * 新しいバリアントの識別子を作る。
     *
     * @return 乱数の UUID を持つバリアントの識別子
     */
    static VariantId newVariantId() {
        return new VariantId(UUID.randomUUID());
    }
}
