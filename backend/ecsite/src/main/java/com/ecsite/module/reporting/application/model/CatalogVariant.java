package com.ecsite.module.reporting.application.model;

import com.ecsite.common.model.id.VariantId;
import com.ecsite.module.reporting.domain.snapshot.ReportOrder;

import java.util.List;

/**
 * 商品カタログのバリアント
 *
 * @param variantId   バリアント ID
 * @param variantCode バリアント品番
 * @param axisValues  軸の名前と値
 */
public record CatalogVariant(VariantId variantId, String variantCode, List<ReportOrder.AxisValue> axisValues) {
}
