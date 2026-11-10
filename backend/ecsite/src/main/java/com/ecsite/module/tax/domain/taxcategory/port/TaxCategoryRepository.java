package com.ecsite.module.tax.domain.taxcategory.port;

import com.ecsite.common.error.exception.BusinessException;
import com.ecsite.module.tax.domain.taxcategory.TaxCategory;
import org.jmolecules.ddd.annotation.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * 税区分のリポジトリ
 */
@Repository
public interface TaxCategoryRepository {

    /**
     * 税区分を取得する。
     *
     * @param id 税区分 ID
     * @return 税区分。なければ空
     */
    Optional<TaxCategory> findById(UUID id);

    /**
     * 税区分を登録する。
     *
     * @param taxCategory 税区分
     * @throws BusinessException 名称が重複している（{@code TAX_CATEGORY_NAME_ALREADY_EXISTS}）
     */
    void add(TaxCategory taxCategory);

    /**
     * 税区分を更新する。
     *
     * @param taxCategory 税区分
     * @throws BusinessException 取得した後に他で更新された（{@code CONCURRENT_MODIFICATION}）
     */
    void save(TaxCategory taxCategory);
}
