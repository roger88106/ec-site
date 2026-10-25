package com.ecsite.module.tax.domain.taxcategory;

import com.ecsite.common.model.base.AggregateGuards;

import java.util.UUID;

/**
 * 税区分のファクトリー
 */
public class TaxCategoryFactory {

    /**
     * 税率のない税区分を作る。
     *
     * @param name 名称
     * @return 税区分
     */
    public TaxCategory create(String name) {
        return TaxCategory.create(UUID.randomUUID(), name);
    }

    /**
     * スナップショットから税区分を復元する
     *
     * @param snapshot 税区分のスナップショット
     * @return 税区分
     */
    public TaxCategory restore(TaxCategory.Snapshot snapshot) {
        // 復元失敗の場合、復元エラーに変換してスローする
        return AggregateGuards.restore("tax category", () -> new TaxCategory(snapshot));
    }
}
