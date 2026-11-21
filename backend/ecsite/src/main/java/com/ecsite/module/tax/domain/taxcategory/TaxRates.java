package com.ecsite.module.tax.domain.taxcategory;

import com.ecsite.common.error.exception.BusinessException;
import com.ecsite.module.tax.domain.TaxErrorCode;
import com.ecsite.module.tax.domain.taxcategory.vo.EffectivePeriod;
import com.ecsite.module.tax.domain.taxcategory.vo.TaxRateId;
import lombok.NonNull;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 税率の一覧
 *
 * <p>適用開始日時の古い順（空白が先頭）に並べて持つ。
 *
 * @param list 税率の一覧
 */
record TaxRates(List<TaxRate> list) {

    /**
     * 税率の並び順
     *
     * <p>適用開始日時の古い順（空白が先頭）。
     */
    private static final Comparator<TaxRate> BY_EFFECTIVE_FROM = Comparator.comparing(
            (TaxRate rate) -> rate.period().effectiveFrom(), Comparator.nullsFirst(Comparator.naturalOrder()));

    /**
     * コンストラクタ。
     *
     * <p>適用開始日時の古い順に並べ替える。一覧が空でなければ、期間が重複せず、途切れがなく、最後の税率の終了が
     * 空白であることを検証する。
     *
     * @param list 税率の一覧（空でもよい）
     * @throws BusinessException 期間が重複している（{@code TAX_RATE_PERIODS_OVERLAP}）
     * @throws BusinessException 期間が途切れている（{@code TAX_RATE_PERIODS_GAP}）
     * @throws BusinessException 最後の税率の終了が空白でない（{@code TAX_RATE_LAST_PERIOD_NOT_OPEN_ENDED}）
     */
    TaxRates(@NonNull List<TaxRate> list) {
        this.list = list.stream().sorted(BY_EFFECTIVE_FROM).toList();
        verifyContinuous();
    }

    /**
     * 指定した時点の税率を返す。
     *
     * <p>期間は重複しないので、該当は 0 件か 1 件。
     *
     * @param instant 時点
     * @return その時点を含む税率。なければ空
     */
    Optional<TaxRate> at(Instant instant) {
        return EffectivePeriod.findAt(list, TaxRate::period, instant);
    }

    /**
     * 税率を ID で引ける形にする。
     *
     * @return 税率（ID → 税率）
     */
    Map<TaxRateId, TaxRate> byId() {
        return list.stream().collect(Collectors.toMap(TaxRate::id, Function.identity()));
    }

    /**
     * 期間が重複せず、途切れがなく、最後の税率の終了が空白であることを検証する。
     */
    private void verifyContinuous() {
        // 税率のない一覧（登録したばかりの税区分）は、検証する対象がない
        if (list.isEmpty()) {
            return;
        }
        // 隣り合う 2 件ずつ、前の終了と次の開始を比べる
        TaxRate previous = null;
        for (var rate : list) {
            if (previous != null) {
                verifyAdjacent(previous, rate);
            }
            previous = rate;
        }
        if (list.getLast().period().effectiveTo() != null) {
            throw BusinessException.of(TaxErrorCode.TAX_RATE_LAST_PERIOD_NOT_OPEN_ENDED);
        }
    }

    /**
     * 隣り合う 2 件の税率の期間が、重ならず、途切れずにつながることを検証する。
     *
     * @param previous 前の税率
     * @param next     次の税率
     */
    private static void verifyAdjacent(TaxRate previous, TaxRate next) {
        var previousEnd = previous.period().effectiveTo();
        var start = next.period().effectiveFrom();
        // どちらかが空白なら、無限に伸びる側と重なる
        if (previousEnd == null || start == null || previousEnd.isAfter(start)) {
            throw BusinessException.of(TaxErrorCode.TAX_RATE_PERIODS_OVERLAP);
        }
        if (previousEnd.isBefore(start)) {
            throw BusinessException.of(TaxErrorCode.TAX_RATE_PERIODS_GAP);
        }
    }
}
