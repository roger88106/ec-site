package com.ecsite.module.tax.application.port;

import com.ecsite.common.model.id.TaxCategoryId;
import com.ecsite.module.tax.application.model.StoredTaxCategory;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

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
    Optional<StoredTaxCategory> findById(TaxCategoryId id);

    /**
     * 複数の税区分を、数によらず決まった回数の問い合わせで取得する。
     *
     * @param ids 税区分 ID
     * @return 保存されている税区分の一覧（登録の古い順。税率は適用開始日時の古い順）。存在しない ID の税区分は含まない
     */
    List<StoredTaxCategory> findAllByIds(Collection<TaxCategoryId> ids);
}
