package com.ecsite.module.catalog.domain.product.vo;

import com.ecsite.common.error.exception.BusinessException;
import com.ecsite.module.catalog.domain.ProductErrorCode;
import lombok.NonNull;
import org.jmolecules.ddd.annotation.ValueObject;
import org.jspecify.annotations.Nullable;

import java.time.Instant;

/**
 * 公開
 *
 * <p>公開状態と公開期間と初回表示日時を、1 つの値にまとめる。状態を変える操作は、新しい値を返す。
 *
 * @param status            公開状態
 * @param publicationPeriod 公開期間
 * @param firstShownAt      初回表示日時。null は一度も表示されていない（表示の予定もない）
 */
@ValueObject
public record Publication(@NonNull PublicationStatus status, @NonNull PublicationPeriod publicationPeriod,
                          @Nullable Instant firstShownAt) {

    /**
     * コンストラクタ。
     *
     * <p>公開で初回表示日時も公開終了もないとき、IllegalArgumentException を投げる。例外のメッセージには値を入れない。
     *
     * @param status            公開状態
     * @param publicationPeriod 公開期間
     * @param firstShownAt      初回表示日時
     */
    public Publication {
        // 公開で初回表示日時がないのは、公開の時点で公開終了を過ぎていて、表示されなかったときだけ
        if (status == PublicationStatus.PUBLISHED && firstShownAt == null && publicationPeriod.publishEnd() == null) {
            throw new IllegalArgumentException("公開中で、初めて表示した日時も公開終了もない");
        }
    }

    /**
     * 表示される条件を満たすかを返す。
     *
     * <p>条件は、公開状態が「公開」で、公開期間内であること。
     *
     * @param now 時点
     * @return 表示されるなら true
     */
    public boolean isVisibleAt(@NonNull Instant now) {
        return status == PublicationStatus.PUBLISHED && publicationPeriod.contains(now);
    }

    /**
     * 表示されたことがあるかを返す。
     *
     * <p>初回表示日時が空か未来なら、まだ表示されていない。
     *
     * @param now 現在時刻
     * @return 表示されたことがあれば true
     */
    public boolean hasBeenShown(@NonNull Instant now) {
        return hasBeenShown(firstShownAt, now);
    }

    /**
     * 表示されたことがあるかを返す。
     *
     * <p>初回表示日時が空か未来なら、まだ表示されていない。
     *
     * @param firstShownAt 初回表示日時。なければ null
     * @param now          現在時刻
     * @return 表示されたことがあれば true
     */
    public static boolean hasBeenShown(@Nullable Instant firstShownAt, @NonNull Instant now) {
        return firstShownAt != null && !firstShownAt.isAfter(now);
    }

    /**
     * 公開にする。
     *
     * <p>既に公開なら、同じ値を返す。
     *
     * @param now 現在時刻
     * @return 公開にした値
     * @throws BusinessException 販売終了（{@code PRODUCT_DISCONTINUED}）
     */
    public Publication publish(@NonNull Instant now) {
        verifyNotDiscontinued();
        if (status == PublicationStatus.PUBLISHED) {
            return this;
        }
        return new Publication(PublicationStatus.PUBLISHED, publicationPeriod, calculateFirstShownAt(publicationPeriod, now));
    }

    /**
     * 非公開にする。
     *
     * <p>既に非公開なら、同じ値を返す。表示前の初回表示日時は空に戻す。
     *
     * @param now 現在時刻
     * @return 非公開にした値
     * @throws BusinessException 販売終了（{@code PRODUCT_DISCONTINUED}）
     */
    public Publication unpublish(@NonNull Instant now) {
        verifyNotDiscontinued();
        if (status == PublicationStatus.UNPUBLISHED) {
            return this;
        }
        return new Publication(PublicationStatus.UNPUBLISHED, publicationPeriod, scheduledFirstShownAtCleared(now));
    }

    /**
     * 販売終了にする。
     *
     * <p>既に販売終了なら、同じ値を返す。表示前の初回表示日時は空に戻す。
     *
     * @param now 現在時刻
     * @return 販売終了にした値
     */
    public Publication discontinue(@NonNull Instant now) {
        if (status == PublicationStatus.DISCONTINUED) {
            return this;
        }
        return new Publication(PublicationStatus.DISCONTINUED, publicationPeriod, scheduledFirstShownAtCleared(now));
    }

    /**
     * 公開期間を変える。
     *
     * <p>公開中なら、表示前の初回表示日時を計算する。表示されて確定した初回表示日時は変わらない。販売終了でも変えられる。
     *
     * @param period 公開期間
     * @param now    現在時刻
     * @return 公開期間を変えた値
     */
    public Publication changePublicationPeriod(@NonNull PublicationPeriod period, @NonNull Instant now) {
        var shownAt = status == PublicationStatus.PUBLISHED ? calculateFirstShownAt(period, now) : firstShownAt;
        return new Publication(status, period, shownAt);
    }

    /**
     * 販売終了でないことを検証する。
     *
     * @throws BusinessException 販売終了（{@code PRODUCT_DISCONTINUED}）
     */
    private void verifyNotDiscontinued() {
        if (status == PublicationStatus.DISCONTINUED) {
            throw BusinessException.of(ProductErrorCode.PRODUCT_DISCONTINUED);
        }
    }

    /**
     * 公開したとき（または公開中に公開期間を変えたとき）の初回表示日時を求める。
     *
     * @param period 公開期間
     * @param now    現在時刻
     * @return 初回表示日時。表示されないなら null
     */
    private @Nullable Instant calculateFirstShownAt(PublicationPeriod period, Instant now) {
        // 表示されて確定した値（過去）は変えない
        if (hasBeenShown(now)) {
            return firstShownAt;
        }
        // 現在時刻と公開開始日時の遅いほう。それが公開終了日時以降なら、表示されないので null にする
        var start = period.publishStart();
        var candidate = start != null && start.isAfter(now) ? start : now;
        var end = period.publishEnd();
        return end != null && !candidate.isBefore(end) ? null : candidate;
    }

    /**
     * 未来（表示予定）の初回表示日時を空に戻した値を求める。
     *
     * <p>確定済みなら、今の初回表示日時のまま。
     *
     * @param now 現在時刻
     * @return 初回表示日時
     */
    private @Nullable Instant scheduledFirstShownAtCleared(Instant now) {
        return hasBeenShown(now) ? firstShownAt : null;
    }
}
