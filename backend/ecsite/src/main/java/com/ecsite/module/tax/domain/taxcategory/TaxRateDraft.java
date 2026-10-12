package com.ecsite.module.tax.domain.taxcategory;

import com.ecsite.module.tax.domain.taxcategory.vo.EffectivePeriod;
import com.ecsite.module.tax.domain.taxcategory.vo.RatePercent;
import jakarta.annotation.Nullable;
import lombok.NonNull;
import org.jmolecules.ddd.annotation.ValueObject;

import java.util.UUID;

/**
 * 税率の一覧の置き換えで指定する、置き換え後の税率の 1 件。
 *
 * @param id          既存の税率を残す場合はその ID。null は新しい税率
 * @param ratePercent 率
 * @param period      適用期間
 */
@ValueObject
public record TaxRateDraft(@Nullable UUID id, @NonNull RatePercent ratePercent, @NonNull EffectivePeriod period) {
}
