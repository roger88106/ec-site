package com.ecsite.module.catalog.domain.product.vo;

import com.ecsite.common.error.exception.BusinessException;
import com.ecsite.module.catalog.domain.ProductErrorCode;
import org.jmolecules.ddd.annotation.ValueObject;

import java.util.List;

/**
 * バリアント品番。形式は商品品番と同じ。<br />
 * 業務上の番号であり、他のデータからの参照には使わない（バックエンドのアーキテクチャの「永続化」）。
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
            throw BusinessException.of(ProductErrorCode.INVALID_VARIANT_CODE, List.of(CodeFormat.MAX_LENGTH));
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
