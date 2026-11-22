package com.ecsite.module.tax.application.model;

import com.ecsite.common.model.id.TaxCategoryId;
import com.ecsite.module.tax.application.model.view.TaxCategoryView;

import java.util.List;

/**
 * 保存されている税区分
 *
 * <p>DB に保存されている値だけを持つ。現時点の率は現在時刻で決まるので持たない。
 * サービスが、この型に求めた値を加えて {@link TaxCategoryView} を作る。
 *
 * @param id      税区分 ID
 * @param name    名称
 * @param rates   税率の一覧（適用開始日時の古い順）
 * @param version バージョン
 */
public record StoredTaxCategory(TaxCategoryId id, String name, List<TaxCategoryView.TaxRateView> rates, long version) {
}
