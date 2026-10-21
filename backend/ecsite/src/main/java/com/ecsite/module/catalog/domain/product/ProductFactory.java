package com.ecsite.module.catalog.domain.product;

import com.ecsite.common.model.base.AggregateGuards;
import com.ecsite.module.catalog.domain.product.vo.PriceExclTax;
import com.ecsite.module.catalog.domain.product.vo.ProductCode;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * 商品のファクトリー
 */
public class ProductFactory {

    /**
     * 商品を作る。登録直後は非公開で、バリアントはない。
     *
     * @param productCode   商品品番
     * @param name          商品名
     * @param description   説明
     * @param taxCategoryId 税区分 ID
     * @param priceExclTax  税抜価格
     * @param categoryIds   所属カテゴリの ID
     * @param axes          バリアント軸の名前（並び順）
     * @return 商品
     */
    public Product create(ProductCode productCode, String name, String description, UUID taxCategoryId,
                          PriceExclTax priceExclTax, Collection<UUID> categoryIds, List<String> axes) {
        return Product.create(UUID.randomUUID(), productCode, name, description, taxCategoryId, priceExclTax,
                categoryIds, axes);
    }

    /**
     * スナップショットから商品を復元する
     *
     * @param snapshot 商品のスナップショット
     * @return 商品
     */
    public Product restore(Product.Snapshot snapshot) {
        // 復元失敗の場合、復元エラーに変換してスローする
        return AggregateGuards.restore("product", snapshot, () -> new Product(snapshot));
    }
}
