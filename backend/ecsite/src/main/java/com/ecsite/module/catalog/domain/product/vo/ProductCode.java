package com.ecsite.module.catalog.domain.product.vo;

import com.ecsite.common.error.exception.BusinessException;
import com.ecsite.module.catalog.domain.product.ProductErrorCode;
import org.jmolecules.ddd.annotation.ValueObject;

import java.util.List;

/**
 * 商品品番（AC-15）。<br />
 * 業務上の番号であり、他のデータからの参照には使わない（constitution 7.1）。
 *
 * @param value 商品品番
 */
@ValueObject
public record ProductCode(String value) {

    /**
     * コンストラクタ。
     *
     * @param value 商品品番
     */
    public ProductCode {
        if (!CodeFormat.matches(value)) {
            throw BusinessException.of(ProductErrorCode.INVALID_PRODUCT_CODE, List.of(CodeFormat.MAX_LENGTH));
        }
    }

    /**
     * 商品品番を作る。
     *
     * @param value 商品品番
     * @return 商品品番
     */
    public static ProductCode of(String value) {
        return new ProductCode(value);
    }
}
