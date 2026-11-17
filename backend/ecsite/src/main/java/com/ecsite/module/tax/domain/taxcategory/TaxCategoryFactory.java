package com.ecsite.module.tax.domain.taxcategory;

import com.ecsite.common.error.exception.BusinessException;
import com.ecsite.common.model.base.RestoreGuard;
import org.jmolecules.ddd.annotation.Factory;

import java.util.UUID;

/**
 * 税区分のファクトリー
 */
@Factory
public class TaxCategoryFactory {

    /**
     * 税率のない税区分を作る。
     *
     * @param name 名称
     * @return 税区分
     * @throws BusinessException 名称が 1 文字以上 {@value TaxCategory#NAME_MAX_LENGTH} 文字以内でない（{@code INVALID_TAX_CATEGORY_NAME}）
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
        return RestoreGuard.restore("tax category", () -> new TaxCategory(snapshot));
    }
}
