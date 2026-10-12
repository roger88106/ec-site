package com.ecsite.module.tax.application;

import com.ecsite.common.error.CommonErrorCode;
import com.ecsite.common.error.exception.BusinessException;
import com.ecsite.module.tax.application.model.command.CreateTaxCategoryCommand;
import com.ecsite.module.tax.application.model.command.ReplaceTaxRatesCommand;
import com.ecsite.module.tax.domain.taxcategory.TaxCategoryFactory;
import com.ecsite.module.tax.domain.taxcategory.TaxRateDraft;
import com.ecsite.module.tax.domain.taxcategory.port.TaxCategoryRepository;
import com.ecsite.module.tax.domain.taxcategory.vo.EffectivePeriod;
import com.ecsite.module.tax.domain.taxcategory.vo.RatePercent;
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
     */
    @Transactional
    public UUID createTaxCategory(CreateTaxCategoryCommand command) {
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
     */
    @Transactional
    public void replaceTaxRates(ReplaceTaxRatesCommand command) {
        // 税区分取得
        var taxCategory = taxCategoryRepository.findById(command.taxCategoryId())
                .orElseThrow(() -> BusinessException.of(CommonErrorCode.NOT_FOUND));

        // 取得時のバージョンと一致しなければ、他の人が先に更新している
        if (taxCategory.version() != command.version()) {
            throw BusinessException.of(CommonErrorCode.CONCURRENT_MODIFICATION);
        }

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
