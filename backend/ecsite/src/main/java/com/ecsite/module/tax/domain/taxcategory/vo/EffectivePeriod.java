package com.ecsite.module.tax.domain.taxcategory.vo;

import com.ecsite.common.error.exception.BusinessException;
import com.ecsite.module.tax.domain.taxcategory.TaxErrorCode;
import org.jmolecules.ddd.annotation.ValueObject;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import java.util.function.Function;

/**
 * 適用期間。開始を含み、終了を含まない。空白（null）は無限を意味する。
 *
 * @param effectiveFrom 適用開始日時。null は過去すべて
 * @param effectiveTo   適用終了日時（この日時を含まない）。null は無期限
 */
@ValueObject
public record EffectivePeriod(@Nullable Instant effectiveFrom, @Nullable Instant effectiveTo) {

    /**
     * コンストラクタ。
     *
     * @param effectiveFrom 適用開始日時
     * @param effectiveTo   適用終了日時
     */
    public EffectivePeriod {
        // 開始と終了の両方があるときだけ、開始が終了より前であることを求める
        if (effectiveFrom != null && effectiveTo != null && !effectiveFrom.isBefore(effectiveTo)) {
            throw BusinessException.of(TaxErrorCode.INVALID_EFFECTIVE_PERIOD);
        }
    }

    /**
     * 適用期間を作る。
     *
     * @param effectiveFrom 適用開始日時。null は過去すべて
     * @param effectiveTo   適用終了日時。null は無期限
     * @return 適用期間
     */
    public static EffectivePeriod of(@Nullable Instant effectiveFrom, @Nullable Instant effectiveTo) {
        return new EffectivePeriod(effectiveFrom, effectiveTo);
    }

    /**
     * 指定した時点を含むか。
     *
     * @param instant 時点
     * @return 含むなら true
     */
    public boolean contains(Instant instant) {
        return hasStarted(instant) && !hasEnded(instant);
    }

    /**
     * 指定した時点を期間に含むものを返す（時点の税率の判定。税区分、税区分の照会、税率の予定が使う）。
     * 税区分の税率の期間は重複しないので、該当は 0 件か 1 件。
     *
     * @param items   期間を持つもの（税率など）
     * @param period  期間の取り出し方
     * @param instant 時点
     * @param <T>     期間を持つものの型
     * @return その時点を含むもの。なければ空
     */
    public static <T> Optional<T> findAt(Collection<T> items, Function<T, EffectivePeriod> period, Instant instant) {
        return items.stream().filter(item -> period.apply(item).contains(instant)).findFirst();
    }

    /**
     * 指定した時点で開始済みか。開始が空白なら常に開始済み。
     *
     * @param now 時点
     * @return 開始済みなら true
     */
    public boolean hasStarted(Instant now) {
        return effectiveFrom == null || !now.isBefore(effectiveFrom);
    }

    /**
     * 指定した時点で終了済みか。終了が空白なら終了しない。
     *
     * @param now 時点
     * @return 終了済みなら true
     */
    public boolean hasEnded(Instant now) {
        return effectiveTo != null && !now.isBefore(effectiveTo);
    }
}
