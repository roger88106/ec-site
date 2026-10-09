package com.ecsite.module.catalog.domain.product.vo;

import com.ecsite.common.error.exception.BusinessException;
import com.ecsite.module.catalog.domain.product.ProductErrorCode;
import org.jmolecules.ddd.annotation.ValueObject;

/**
 * バリアント品番（AC-21）。形式は商品品番と同じ。<br />
 * 業務上の番号であり、他のデータからの参照には使わない（constitution 7.1）。
 *
 * @param value バリアント品番
 */
@ValueObject
public record VariantCode(String value) {

    /**
     * コンストラクタ。
     *
     * @param value バリアント品番
     */
    public VariantCode {
        if (!CodeFormat.matches(value)) {
            throw BusinessException.of(ProductErrorCode.INVALID_VARIANT_CODE);
        }
    }

    /**
     * バリアント品番を作る。
     *
     * @param value バリアント品番
     * @return バリアント品番
     */
    public static VariantCode of(String value) {
        return new VariantCode(value);
    }
}
