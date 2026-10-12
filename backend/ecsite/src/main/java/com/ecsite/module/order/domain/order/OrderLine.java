package com.ecsite.module.order.domain.order;

import com.ecsite.common.model.value.Money;
import com.ecsite.module.order.domain.quote.AxisValue;
import org.jmolecules.ddd.annotation.ValueObject;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * 注文明細。注文時点の写しで、注文の成立後は変わらない（AC-92）。
 *
 * @param lineNo           行番号（1 始まり。カートの並び）
 * @param variantId        バリアント ID（在庫の戻しに使う。表示には写しを使う）
 * @param productCode      商品品番
 * @param variantCode      バリアント品番
 * @param productName      商品名
 * @param axisValues       軸の名前と値
 * @param unitPriceExclTax 税抜単価
 * @param ratePercent      率（%）
 * @param unitPriceInclTax 税込単価
 * @param quantity         数量
 * @param subtotal         小計（税込単価 × 数量）
 */
@ValueObject
public record OrderLine(int lineNo, UUID variantId, String productCode, String variantCode, String productName,
                        List<AxisValue> axisValues, Money unitPriceExclTax, BigDecimal ratePercent,
                        Money unitPriceInclTax, int quantity, Money subtotal) {
}
