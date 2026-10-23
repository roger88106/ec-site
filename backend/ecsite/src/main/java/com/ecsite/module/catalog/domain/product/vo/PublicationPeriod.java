package com.ecsite.module.catalog.domain.product.vo;

import com.ecsite.common.error.exception.BusinessException;
import com.ecsite.module.catalog.domain.ProductErrorCode;
import lombok.NonNull;
import org.jmolecules.ddd.annotation.ValueObject;
import org.jspecify.annotations.Nullable;

import java.time.Instant;

/**
 * 公開期間。公開状態でも、C 側に表示する期間を限る。<br />
 * 開始を含み、終了を含まない。null は無限。
 *
 * @param publishStart 公開開始日時。null は過去すべて
 * @param publishEnd   公開終了日時。null は無期限
 */
@ValueObject
public record PublicationPeriod(@Nullable Instant publishStart, @Nullable Instant publishEnd) {

    /**
     * 期間の限りがない公開期間
     */
    public static final PublicationPeriod UNLIMITED = new PublicationPeriod(null, null);

    /**
     * コンストラクタ。
     *
     * @param publishStart 公開開始日時
     * @param publishEnd   公開終了日時
     */
    public PublicationPeriod {
        // 開始と終了の両方があるときだけ、開始が終了より前であることを求める
        if (publishStart != null && publishEnd != null && !publishStart.isBefore(publishEnd)) {
            throw BusinessException.of(ProductErrorCode.INVALID_PUBLICATION_PERIOD);
        }
    }

    /**
     * 公開期間を作る。
     *
     * @param publishStart 公開開始日時。null は過去すべて
     * @param publishEnd   公開終了日時。null は無期限
     * @return 公開期間
     */
    public static PublicationPeriod of(@Nullable Instant publishStart, @Nullable Instant publishEnd) {
        return new PublicationPeriod(publishStart, publishEnd);
    }

    /**
     * 時点を含むかを返す。
     *
     * @param instant 時点
     * @return 含めば true
     */
    public boolean contains(@NonNull Instant instant) {
        // 空白の側は限りなしとする。開始の時点は含み、終了の時点は含まない
        return (publishStart == null || !instant.isBefore(publishStart))
                && (publishEnd == null || instant.isBefore(publishEnd));
    }
}
