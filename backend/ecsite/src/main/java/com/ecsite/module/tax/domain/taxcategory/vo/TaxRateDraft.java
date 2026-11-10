package com.ecsite.module.tax.domain.taxcategory.vo;

import lombok.NonNull;
import org.jmolecules.ddd.annotation.ValueObject;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * 置き換え後の税率の内容
 *
 * <p>既存の税率を残すときだけ ID を持つ。
 *
 * @param id          既存の税率を残す場合はその ID。null は新しい税率
 * @param ratePercent 率
 * @param period      適用期間
 */
@ValueObject
public record TaxRateDraft(@Nullable UUID id, @NonNull RatePercent ratePercent, @NonNull EffectivePeriod period) {
}
