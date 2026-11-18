package com.ecsite.module.tax.application.port;

import com.ecsite.module.tax.application.model.StoredTaxCategory;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 税区分のクエリリポジトリ
 */
public interface TaxCategoryQueryRepository {

    /**
     * すべての税区分を取得する。
     *
     * @return 保存されている税区分の一覧（登録の古い順）。税率は適用開始日時の古い順
     */
    List<StoredTaxCategory> findAll();

    /**
     * 税区分を取得する。
     *
     * @param id 税区分 ID
     * @return 保存されている税区分（税率は適用開始日時の古い順）。なければ空
     */
    Optional<StoredTaxCategory> findById(UUID id);
}
