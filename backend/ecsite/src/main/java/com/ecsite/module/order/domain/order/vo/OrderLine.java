package com.ecsite.module.order.domain.order.vo;

import com.ecsite.common.model.id.VariantId;
import com.ecsite.common.model.value.Money;
import com.ecsite.module.order.domain.quote.AxisValue;
import org.jmolecules.ddd.annotation.ValueObject;

import java.math.BigDecimal;
import java.util.List;

/**
 * 注文明細
 *
 * <p>注文時点の写しで、注文の成立後は変わらない。
 *
 * @param lineNo           行番号（1 始まり。カートの並び）
 * @param variantId        バリアント ID
 * @param productCode      商品品番
 * @param variantCode      バリアント品番
 * @param productName      商品名
 * @param axisValues       軸の名前と値
 * @param unitPriceExclTax 税抜単価
 * @param ratePercent      率（%）
 * @param unitPriceInclTax 税込単価
 * @param quantity         数量（{@value #MIN_QUANTITY} 以上）
 * @param subtotal         小計（税込単価 × 数量）
 */
@ValueObject
public record OrderLine(int lineNo, VariantId variantId, String productCode, String variantCode, String productName,
                        List<AxisValue> axisValues, Money unitPriceExclTax, BigDecimal ratePercent,
                        Money unitPriceInclTax, int quantity, Money subtotal) {

    /**
     * 行番号の下限
     */
    private static final int MIN_LINE_NO = 1;

    /**
     * 数量の下限
     */
    private static final int MIN_QUANTITY = 1;

    /**
     * コンストラクタ。
     *
     * <p>行番号と数量が下限以上で、小計が税込単価と数量の積であることを確認する。
     *
     * @param lineNo           行番号
     * @param variantId        バリアント ID
     * @param productCode      商品品番
     * @param variantCode      バリアント品番
     * @param productName      商品名
     * @param axisValues       軸の名前と値
     * @param unitPriceExclTax 税抜単価
     * @param ratePercent      率（%）
     * @param unitPriceInclTax 税込単価
     * @param quantity         数量
     * @param subtotal         小計
     */
    public OrderLine {
        if (lineNo < MIN_LINE_NO) {
            throw new IllegalArgumentException("行番号が下限より小さい");
        }
        if (quantity < MIN_QUANTITY) {
            throw new IllegalArgumentException("数量が下限より小さい");
        }
        if (!subtotal.equals(unitPriceInclTax.times(quantity))) {
            throw new IllegalArgumentException("小計が税込単価と数量の積と違う");
        }
    }
}
