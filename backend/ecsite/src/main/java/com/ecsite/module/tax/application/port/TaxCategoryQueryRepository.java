package com.ecsite.module.tax.application.port;

import com.ecsite.module.tax.application.model.view.TaxCategoryView;

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
     * @return 税区分の一覧（登録の古い順）。税率は適用開始日時の古い順で、現時点の率は空
     */
    List<TaxCategoryView> findAll();

    /**
     * 税区分を取得する。
     *
     * @param id 税区分 ID
     * @return 税区分（税率は適用開始日時の古い順で、現時点の率は空）。なければ空
     */
    Optional<TaxCategoryView> findById(UUID id);
}
