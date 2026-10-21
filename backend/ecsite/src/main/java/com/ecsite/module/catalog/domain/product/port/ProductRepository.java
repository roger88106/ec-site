package com.ecsite.module.catalog.domain.product.port;

import com.ecsite.module.catalog.domain.product.Product;

import java.util.Optional;
import java.util.UUID;

/**
 * 商品のリポジトリ
 */
public interface ProductRepository {

    /**
     * 商品を取得する（バリアントを含む）。
     *
     * @param id 商品 ID
     * @return 商品。なければ空
     */
    Optional<Product> findById(UUID id);

    /**
     * 商品を登録する。<br />
     * 品番が重複していれば {@code PRODUCT_CODE_ALREADY_EXISTS} / {@code VARIANT_CODE_ALREADY_EXISTS}、
     * 所属カテゴリが存在しなければ {@code INVALID_CATEGORY_ID} の業務のエラーを投げる。
     *
     * @param product 商品
     */
    void add(Product product);

    /**
     * 商品を更新する（バリアントは差分で更新する）。<br />
     * 取得後に他で更新されていれば {@code CONCURRENT_MODIFICATION}、ほかは {@link #add} と同じ業務のエラーを投げる。
     *
     * @param product 商品
     */
    void save(Product product);

    /**
     * 商品を削除する（バリアントなど商品の一部も消える）。<br />
     * 取得後に他で更新されていれば {@code CONCURRENT_MODIFICATION} の業務のエラーを投げる。
     *
     * @param product 商品
     */
    void delete(Product product);
}
