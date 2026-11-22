package com.ecsite.module.tax.domain.taxcategory;

import com.ecsite.module.tax.domain.taxcategory.vo.EffectivePeriod;
import com.ecsite.module.tax.domain.taxcategory.vo.RatePercent;
import com.ecsite.module.tax.domain.taxcategory.vo.TaxRateDraft;
import com.ecsite.module.tax.domain.taxcategory.vo.TaxRateId;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.experimental.Accessors;
import org.jmolecules.ddd.annotation.Entity;
import org.jmolecules.ddd.annotation.Identity;

/**
 * 税率
 *
 * <p>税区分の集約の一部で、率と適用期間を持つ。
 */
@Getter
@Accessors(fluent = true)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@AllArgsConstructor
@Entity
public final class TaxRate {

    /**
     * 税率 ID
     */
    @Identity
    @EqualsAndHashCode.Include
    @NonNull
    private final TaxRateId id;

    /**
     * 率
     */
    @NonNull
    private final RatePercent ratePercent;

    /**
     * 適用期間
     */
    @NonNull
    private final EffectivePeriod period;

    /**
     * 率と適用期間が同じか（ID は比べない）。
     *
     * @param draft 置き換え後の税率
     * @return 同じなら true
     */
    boolean sameAs(TaxRateDraft draft) {
        return ratePercent.equals(draft.ratePercent()) && period.equals(draft.period());
    }
}
