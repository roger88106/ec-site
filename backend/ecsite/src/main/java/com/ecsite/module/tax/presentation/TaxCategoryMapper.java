package com.ecsite.module.tax.presentation;

import com.ecsite.api.admin.model.CreateTaxCategoryRequest;
import com.ecsite.api.admin.model.ReplaceTaxRatesRequest;
import com.ecsite.api.admin.model.TaxCategory;
import com.ecsite.api.admin.model.TaxCategoryList;
import com.ecsite.api.admin.model.TaxRate;
import com.ecsite.api.admin.model.TaxRateDraft;
import com.ecsite.common.presentation.ApiDateTimes;
import com.ecsite.module.tax.application.model.command.CreateTaxCategoryCommand;
import com.ecsite.module.tax.application.model.command.ReplaceTaxRatesCommand;
import com.ecsite.module.tax.application.model.view.TaxCategoryView;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * 税区分 API のリクエスト・レスポンスと、アプリケーション層のモデルの変換
 */
@Component
class TaxCategoryMapper {

    /**
     * 税区分の登録コマンドに変換する。
     *
     * @param request 税区分の登録リクエスト
     * @return 税区分の登録コマンド
     */
    CreateTaxCategoryCommand toCreateTaxCategoryCommand(CreateTaxCategoryRequest request) {
        return new CreateTaxCategoryCommand(request.getName());
    }

    /**
     * 税率の一覧の置き換えコマンドに変換する。
     *
     * @param taxCategoryId 税区分 ID
     * @param request       税率の一覧の置き換えリクエスト
     * @return 税率の一覧の置き換えコマンド
     */
    ReplaceTaxRatesCommand toReplaceTaxRatesCommand(UUID taxCategoryId, ReplaceTaxRatesRequest request) {
        return new ReplaceTaxRatesCommand(
                taxCategoryId,
                request.getVersion(),
                request.getRates().stream().map(TaxCategoryMapper::toRate).toList());
    }

    /**
     * 税区分のレスポンスに変換する。
     *
     * @param view 税区分のビュー
     * @return 税区分のレスポンス
     */
    TaxCategory toTaxCategory(TaxCategoryView view) {
        return new TaxCategory()
                .id(view.id())
                .name(view.name())
                .currentRatePercent(view.currentRatePercent())
                .rates(view.rates().stream().map(TaxCategoryMapper::toTaxRate).toList())
                .version(view.version());
    }

    /**
     * 税区分の一覧のレスポンスに変換する。
     *
     * @param views 税区分のビューの一覧
     * @return 税区分の一覧のレスポンス
     */
    TaxCategoryList toTaxCategoryList(List<TaxCategoryView> views) {
        return new TaxCategoryList().items(views.stream().map(this::toTaxCategory).toList());
    }

// ============================== プライベートメソッド ==============================

    /**
     * リクエストの税率（新しい税率は ID なし）を、置き換えのコマンドの税率に変換する。
     */
    private static ReplaceTaxRatesCommand.Rate toRate(TaxRateDraft draft) {
        return new ReplaceTaxRatesCommand.Rate(
                draft.getId().orElse(null),
                draft.getRatePercent(),
                ApiDateTimes.toInstant(draft.getEffectiveFrom().orElse(null)),
                ApiDateTimes.toInstant(draft.getEffectiveTo().orElse(null)));
    }

    /**
     * 税率をレスポンスに変換する。
     */
    private static TaxRate toTaxRate(TaxCategoryView.TaxRateView view) {
        return new TaxRate()
                .id(view.id())
                .ratePercent(view.ratePercent())
                .effectiveFrom(ApiDateTimes.toOffsetDateTime(view.effectiveFrom()))
                .effectiveTo(ApiDateTimes.toOffsetDateTime(view.effectiveTo()));
    }
}
