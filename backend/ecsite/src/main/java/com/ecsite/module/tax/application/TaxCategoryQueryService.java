package com.ecsite.module.tax.application;

import com.ecsite.common.error.CommonErrorCode;
import com.ecsite.common.error.exception.BusinessException;
import com.ecsite.module.tax.application.model.StoredTaxCategory;
import com.ecsite.module.tax.application.model.view.TaxCategoryView;
import com.ecsite.module.tax.application.port.TaxCategoryQueryRepository;
import com.ecsite.module.tax.domain.taxcategory.vo.EffectivePeriod;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
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
     * @return 税区分の一覧（現時点の率を含む。税率は適用開始日時の古い順）
     */
    public List<TaxCategoryView> listTaxCategories() {
        var now = clock.instant();
        return taxCategoryQueryRepository.findAll().stream()
                .map(stored -> toView(stored, now))
                .toList();
    }

    /**
     * 税区分を取得する。
     *
     * @param id 税区分 ID
     * @return 税区分（現時点の率を含む。税率は適用開始日時の古い順）
     * @throws BusinessException 税区分がない（{@code NOT_FOUND}）
     */
    public TaxCategoryView getTaxCategory(@NonNull UUID id) {
        return findTaxCategory(id).orElseThrow(() -> BusinessException.of(CommonErrorCode.NOT_FOUND));
    }

    /**
     * 税区分を取得する。
     *
     * <p>なければ空を返す。
     *
     * @param id 税区分 ID
     * @return 税区分（現時点の率を含む。税率は適用開始日時の古い順）。なければ空
     */
    public Optional<TaxCategoryView> findTaxCategory(@NonNull UUID id) {
        return taxCategoryQueryRepository.findById(id)
                .map(stored -> toView(stored, clock.instant()));
    }

    /**
     * 複数の税区分を、数によらず決まった回数の問い合わせで取得する。
     *
     * <p>ない税区分は結果に含めない。空の集まりなら、問い合わせをせずに空を返す。
     *
     * @param ids 税区分 ID の集まり
     * @return 税区分の一覧（現時点の率を含む。税率は適用開始日時の古い順）
     */
    public List<TaxCategoryView> findTaxCategories(@NonNull Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        var now = clock.instant();
        return taxCategoryQueryRepository.findAllByIds(ids).stream()
                .map(stored -> toView(stored, now))
                .toList();
    }

// ============================== プライベートメソッド ==============================

    /**
     * 保存されている税区分に、現時点の率を加えてビューにする。
     *
     * @param stored 保存されている税区分
     * @param now    現在時刻
     * @return 税区分のビュー
     */
    private static TaxCategoryView toView(StoredTaxCategory stored, Instant now) {
        var current = EffectivePeriod.findAt(stored.rates(), TaxCategoryView.TaxRateView::period, now)
                .map(TaxCategoryView.TaxRateView::ratePercent)
                .orElse(null);
        return new TaxCategoryView(stored.id(), stored.name(), current, stored.rates(), stored.version());
    }
}
