package com.ecsite.module.reporting.application.model.view;

import java.util.List;

/**
 * 地域・年齢層・性別
 *
 * @param prefectures      都道府県ごと（コードの順、不明が最後。売れた都道府県だけ）
 * @param ageGroups        年齢層ごと（若い順、不明が最後。すべての層）
 * @param genders          性別ごと（男性、女性、その他、不明。すべての区分）
 * @param shippingExcluded 送料を除いたか（指定したとき、または商品を選んだとき）
 */
public record CustomerBreakdownReport(List<PrefectureSales> prefectures, List<AgeGroupSales> ageGroups,
                                      List<GenderSales> genders, boolean shippingExcluded) {
}
