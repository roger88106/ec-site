package com.ecsite.module.tax.application;

import com.ecsite.common.error.CommonErrorCode;
import com.ecsite.common.error.exception.BusinessException;
import com.ecsite.module.tax.application.model.command.CreateTaxCategoryCommand;
import com.ecsite.module.tax.application.model.command.ReplaceTaxRatesCommand;
import com.ecsite.module.tax.domain.taxcategory.TaxCategoryFactory;
import com.ecsite.module.tax.domain.taxcategory.port.TaxCategoryRepository;
import com.ecsite.module.tax.domain.taxcategory.vo.EffectivePeriod;
import com.ecsite.module.tax.domain.taxcategory.vo.RatePercent;
import com.ecsite.module.tax.domain.taxcategory.vo.TaxRateDraft;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

/**
 * 税区分のコマンドサービス
 */
@Service
@RequiredArgsConstructor
@Transactional
public class TaxCategoryCommandService {

    /**
     * 税区分リポジトリ
     */
    private final TaxCategoryRepository taxCategoryRepository;

    /**
     * 税区分ファクトリ
     */
    private final TaxCategoryFactory taxCategoryFactory;

    /**
     * 時計
     */
    private final Clock clock;

    /**
     * 税区分を登録する。
     *
     * @param command 税区分の登録コマンド
     * @return 登録した税区分の ID
     * @throws BusinessException 名称の長さが範囲の外（{@code INVALID_TAX_CATEGORY_NAME}）
     * @throws BusinessException 名称が重複している（{@code TAX_CATEGORY_NAME_ALREADY_EXISTS}）
     */
    public UUID createTaxCategory(@NonNull CreateTaxCategoryCommand command) {
        // 税区分作成
        var taxCategory = taxCategoryFactory.create(command.name());

        // 税区分登録 ※名称の重複は DB の一意制約で検出する
        taxCategoryRepository.add(taxCategory);

        return taxCategory.id();
    }

    /**
     * 税率の一覧を置き換える。
     *
     * @param command 税率の一覧の置き換えコマンド
     * @throws BusinessException 税区分がない（{@code NOT_FOUND}）
     * @throws BusinessException 取得した後に他で更新された（{@code CONCURRENT_MODIFICATION}）
     * @throws BusinessException 率が範囲の外（{@code INVALID_RATE_PERCENT}）
     * @throws BusinessException 適用期間の開始が終了より前でない（{@code INVALID_EFFECTIVE_PERIOD}）
     * @throws BusinessException 置き換え後の一覧が空（{@code TAX_RATES_REQUIRED}）
     * @throws BusinessException 税区分にない税率、または重複した税率を指定した（{@code INVALID_TAX_RATE_ID}）
     * @throws BusinessException 終了済みの税率を変更、または削除しようとした（{@code TAX_RATE_ENDED_UNCHANGEABLE}）
     * @throws BusinessException 開始済みの税率の率・適用開始日時を変更、または削除しようとした（{@code TAX_RATE_STARTED_UNCHANGEABLE}）
     * @throws BusinessException 現在より前の日時を新たに設定しようとした（{@code TAX_RATE_PAST_DATETIME}）
     * @throws BusinessException 適用期間が重複している（{@code TAX_RATE_PERIODS_OVERLAP}）
     * @throws BusinessException 適用期間の間に途切れがある（{@code TAX_RATE_PERIODS_GAP}）
     * @throws BusinessException 最後の税率の適用終了日時が空白でない（{@code TAX_RATE_LAST_PERIOD_NOT_OPEN_ENDED}）
     */
    public void replaceTaxRates(@NonNull ReplaceTaxRatesCommand command) {
        // 税区分取得
        var taxCategory = taxCategoryRepository.findById(command.taxCategoryId())
                .orElseThrow(() -> BusinessException.of(CommonErrorCode.NOT_FOUND));

        // 取得時のバージョンと一致しなければ、他の人が先に更新している（保存のときにもリポジトリが比べる）
        taxCategory.requireVersion(command.version());

        // 税率の一覧の置き換え
        var drafts = command.rates().stream()
                .map(rate -> new TaxRateDraft(
                        rate.id(),
                        RatePercent.of(rate.ratePercent()),
                        EffectivePeriod.of(rate.effectiveFrom(), rate.effectiveTo())))
                .toList();
        taxCategory.replaceRates(drafts, clock.instant());

        // 税区分保存
        taxCategoryRepository.save(taxCategory);
    }
}
