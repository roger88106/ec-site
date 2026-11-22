package com.ecsite.module.tax.domain.taxcategory.vo;

import lombok.NonNull;
import org.jmolecules.ddd.annotation.ValueObject;
import org.jspecify.annotations.Nullable;

/**
 * 置き換え後の税率の内容
 *
 * <p>既存の税率を残すときだけ {@code id} を持つ。新しい税率は {@code id} が null で、使う識別子を {@code newId} で受け取る。
 *
 * @param id          既存の税率を残す場合はその ID。null は新しい税率
 * @param newId       新しい税率（{@code id} が null）に使う ID。既存の税率では使わない
 * @param ratePercent 率
 * @param period      適用期間
 */
@ValueObject
public record TaxRateDraft(@Nullable TaxRateId id, @Nullable TaxRateId newId, @NonNull RatePercent ratePercent, @NonNull EffectivePeriod period) {
}
