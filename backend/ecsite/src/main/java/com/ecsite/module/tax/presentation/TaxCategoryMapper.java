package com.ecsite.module.tax.presentation;

import com.ecsite.api.admin.model.CreateTaxCategoryRequest;
import com.ecsite.api.admin.model.ReplaceTaxRatesRequest;
import com.ecsite.api.admin.model.TaxCategory;
import com.ecsite.api.admin.model.TaxRate;
import com.ecsite.api.admin.model.TaxRateDraft;
import com.ecsite.module.tax.application.model.command.CreateTaxCategoryCommand;
import com.ecsite.module.tax.application.model.command.ReplaceTaxRatesCommand;
import com.ecsite.module.tax.application.model.view.TaxCategoryView;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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
        return new TaxCategory(
                view.id(),
                view.name(),
                view.rates().stream().map(TaxCategoryMapper::toTaxRate).toList(),
                view.version())
                .currentRatePercent(view.currentRatePercent());
    }

// ============================== プライベートメソッド ==============================

    /**
     * リクエストの税率（新しい税率は ID なし）を、置き換えのコマンドの税率に変換する。
     */
    private static ReplaceTaxRatesCommand.Rate toRate(TaxRateDraft draft) {
        return new ReplaceTaxRatesCommand.Rate(
                draft.getId().orElse(null),
                draft.getRatePercent(),
                toInstant(draft.getEffectiveFrom().orElse(null)),
                toInstant(draft.getEffectiveTo().orElse(null)));
    }

    /**
     * 税率をレスポンスに変換する。
     */
    private static TaxRate toTaxRate(TaxCategoryView.TaxRateView view) {
        return new TaxRate(view.id(), view.ratePercent())
                .effectiveFrom(toOffsetDateTime(view.effectiveFrom()))
                .effectiveTo(toOffsetDateTime(view.effectiveTo()));
    }

    /**
     * リクエストの日時を時刻に変換する。null（期間の端がない）は null のまま。
     */
    private static Instant toInstant(OffsetDateTime dateTime) {
        return dateTime == null ? null : dateTime.toInstant();
    }

    /**
     * 時刻をレスポンスの日時（UTC）に変換する。null は null のまま。
     */
    private static OffsetDateTime toOffsetDateTime(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }
}
