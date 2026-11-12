package com.ecsite.module.catalog.domain.product.port;

import com.ecsite.common.error.exception.BusinessException;
import com.ecsite.module.catalog.domain.product.Product;
import org.jmolecules.ddd.annotation.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * 商品のリポジトリ
 */
@Repository
public interface ProductRepository {

    /**
     * 商品を取得する（バリアントを含む）。
     *
     * @param id 商品 ID
     * @return 商品。なければ空
     */
    Optional<Product> findById(UUID id);

    /**
     * 商品を登録する。
     *
     * @param product 商品
     * @throws BusinessException 品番が重複している（{@code PRODUCT_CODE_ALREADY_EXISTS}、{@code VARIANT_CODE_ALREADY_EXISTS}）。
     *                           軸の値の組み合わせが重複している（{@code VARIANT_AXIS_VALUES_ALREADY_EXISTS}）。
     *                           所属カテゴリが存在しない（{@code INVALID_CATEGORY_ID}）
     */
    void add(Product product);

    /**
     * 商品を更新する（バリアントは差分で更新する）。
     *
     * @param product 商品
     * @throws BusinessException 取得した後に他で更新された（{@code CONCURRENT_MODIFICATION}）。
     *                           品番が重複している（{@code PRODUCT_CODE_ALREADY_EXISTS}、{@code VARIANT_CODE_ALREADY_EXISTS}）。
     *                           軸の値の組み合わせが重複している（{@code VARIANT_AXIS_VALUES_ALREADY_EXISTS}）。
     *                           所属カテゴリが存在しない（{@code INVALID_CATEGORY_ID}）
     */
    void save(Product product);

    /**
     * 商品を削除する（バリアントなど商品の一部も消える）。
     *
     * @param product 商品
     * @throws BusinessException 取得した後に他で更新された（{@code CONCURRENT_MODIFICATION}）
     */
    void delete(Product product);
}
