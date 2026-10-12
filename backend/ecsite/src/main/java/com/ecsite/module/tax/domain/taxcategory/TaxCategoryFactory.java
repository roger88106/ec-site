package com.ecsite.module.tax.domain.taxcategory;

import com.ecsite.common.error.CommonErrorCode;
import com.ecsite.common.error.exception.SystemException;

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
        try {
            return new TaxCategory(snapshot);
        } catch (Exception e) {
            // 復元失敗の場合、復元エラーに変換してスローする
            String debugMessage = """
                    tax category restore error.
                    snapshot: %s
                    """.formatted(snapshot);
            throw SystemException.of(debugMessage, e, CommonErrorCode.RESTORE_ERROR);
        }
    }
}
