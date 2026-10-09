package com.ecsite.module.tax.application;

import com.ecsite.common.error.CommonErrorCode;
import com.ecsite.common.error.exception.BusinessException;
import com.ecsite.module.tax.application.model.view.TaxCategoryView;
import com.ecsite.module.tax.domain.taxcategory.vo.EffectivePeriod;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 税区分のクエリサービス
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TaxCategoryQueryService {

    /**
     * 税区分クエリリポジトリ
     */
    private final TaxCategoryQueryRepository taxCategoryQueryRepository;

    /**
     * 時計
     */
    private final Clock clock;

    /**
     * すべての税区分を取得する。
     *
     * @return 税区分の一覧（現時点の率を含む）
     */
    public List<TaxCategoryView> listTaxCategories() {
        var now = clock.instant();
        return taxCategoryQueryRepository.findAll().stream()
                .map(view -> withCurrentRate(view, now))
                .toList();
    }

    /**
     * 税区分を取得する。
     *
     * @param id 税区分 ID
     * @return 税区分（現時点の率を含む）
     */
    public TaxCategoryView getTaxCategory(UUID id) {
        return taxCategoryQueryRepository.findById(id)
                .map(view -> withCurrentRate(view, clock.instant()))
                .orElseThrow(() -> BusinessException.of(CommonErrorCode.NOT_FOUND));
    }

// ============================== プライベートメソッド ==============================

    /**
     * 現時点の率を設定する。
     *
     * @param view 税区分のビュー
     * @param now  現在時刻
     * @return 現時点の率を設定したビュー
     */
    private static TaxCategoryView withCurrentRate(TaxCategoryView view, Instant now) {
        var current = view.rates().stream()
                .filter(rate -> EffectivePeriod.of(rate.effectiveFrom(), rate.effectiveTo()).contains(now))
                .findFirst()
                .map(TaxCategoryView.TaxRateView::ratePercent)
                .orElse(null);
        return new TaxCategoryView(view.id(), view.name(), current, view.rates(), view.version());
    }
}
