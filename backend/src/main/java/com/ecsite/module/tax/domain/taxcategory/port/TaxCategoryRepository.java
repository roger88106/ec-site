package com.ecsite.module.tax.domain.taxcategory.port;

import com.ecsite.module.tax.domain.taxcategory.TaxCategory;

import java.util.Optional;
import java.util.UUID;

/**
 * 税区分のリポジトリ
 */
public interface TaxCategoryRepository {

    /**
     * 税区分を取得する。
     *
     * @param id 税区分 ID
     * @return 税区分。なければ空
     */
    Optional<TaxCategory> findById(UUID id);

    /**
     * 税区分を登録する。<br />
     * 名称が重複していれば、業務のエラー（{@code TAX_CATEGORY_NAME_ALREADY_EXISTS}）を投げる。
     *
     * @param taxCategory 税区分
     */
    void add(TaxCategory taxCategory);

    /**
     * 税区分を更新する。<br />
     * 取得後に他で更新されていれば、業務のエラー（{@code CONCURRENT_MODIFICATION}）を投げる。
     *
     * @param taxCategory 税区分
     */
    void save(TaxCategory taxCategory);
}
