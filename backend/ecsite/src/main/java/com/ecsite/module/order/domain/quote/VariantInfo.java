package com.ecsite.module.order.domain.quote;

import com.ecsite.common.model.value.Money;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * 見積もりと注文の写しに使う、バリアントの情報（商品カタログから受け取る）
 *
 * @param variantId      バリアント ID
 * @param productId      商品 ID
 * @param productCode    商品品番
 * @param variantCode    バリアント品番
 * @param productName    商品名
 * @param axisValues     軸の名前と値（商品の軸の並び）
 * @param priceExclTax   税抜価格
 * @param taxCategoryId  税区分 ID
 * @param imageUrl       画像の URL。なければ null
 * @param visible        商品が C 側に表示されるか
 * @param salesSuspended 販売停止か
 */
public record VariantInfo(UUID variantId, UUID productId, String productCode, String variantCode, String productName,
                          List<AxisValue> axisValues, Money priceExclTax, UUID taxCategoryId,
                          @Nullable String imageUrl, boolean visible, boolean salesSuspended) {
}
