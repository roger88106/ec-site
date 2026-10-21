package com.ecsite.module.tax.application.port;

import com.ecsite.module.tax.application.model.view.TaxCategoryView;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 税区分のクエリリポジトリ。<br />
 * 返すビューの現時点の率（{@code currentRatePercent}）は空のまま。時刻で決まるので、クエリサービスが設定する。
 */
public interface TaxCategoryQueryRepository {

    /**
     * すべての税区分を取得する。
     *
     * @return 税区分の一覧（登録の古い順）
     */
    List<TaxCategoryView> findAll();

    /**
     * 税区分を取得する。
     *
     * @param id 税区分 ID
     * @return 税区分。なければ空
     */
    Optional<TaxCategoryView> findById(UUID id);
}
