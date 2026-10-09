package com.ecsite.module.tax.domain.taxcategory;

import com.ecsite.module.tax.domain.taxcategory.vo.EffectivePeriod;
import com.ecsite.module.tax.domain.taxcategory.vo.RatePercent;
import lombok.NonNull;
import org.jmolecules.ddd.annotation.Entity;
import org.jmolecules.ddd.annotation.Identity;

import java.util.UUID;

/**
 * 税率。税区分の集約の一部で、率と適用期間を持つ。
 *
 * @param id          税率 ID
 * @param ratePercent 率
 * @param period      適用期間
 */
@Entity
public record TaxRate(@Identity @NonNull UUID id, @NonNull RatePercent ratePercent, @NonNull EffectivePeriod period) {

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
