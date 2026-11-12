package com.ecsite.module.catalog.domain.product;

import lombok.NonNull;
import org.jmolecules.ddd.annotation.ValueObject;
import org.jspecify.annotations.Nullable;

import java.time.Instant;

/**
 * 商品にできる操作
 *
 * <p>値は商品（集約）の規則から作る。既にその状態で、状態を変えない操作（公開中の公開など）は含めない。
 * 集約が別の理由で拒む操作（バリアントのない商品の公開など）はフラグでは止めず、操作したときに拒む。
 *
 * @param publish        公開できる
 * @param unpublish      非公開にできる
 * @param discontinue    販売終了にできる
 * @param delete         削除できる
 * @param deleteVariants バリアントを削除できる
 * @param addVariant     バリアントを追加できる
 * @param deleteBlockedReason 削除できない理由。削除できるなら null
 */
@ValueObject
public record ProductActions(boolean publish, boolean unpublish, boolean discontinue, boolean delete,
                             boolean deleteVariants, boolean addVariant, @Nullable DeleteBlockedReason deleteBlockedReason) {

    /**
     * 商品の状態から、できる操作を決める。
     *
     * @param status       公開状態
     * @param firstShownAt 初回表示日時。なければ null
     * @param axisCount    バリアント軸の数
     * @param variantCount バリアントの数
     * @param now          現在時刻
     * @return できる操作
     */
    public static ProductActions of(@NonNull PublicationStatus status, @Nullable Instant firstShownAt, int axisCount,
                                    int variantCount, @NonNull Instant now) {
        var neverShown = !Product.hasBeenShown(firstShownAt, now);
        return new ProductActions(
                status == PublicationStatus.UNPUBLISHED,
                status == PublicationStatus.PUBLISHED,
                status != PublicationStatus.DISCONTINUED,
                neverShown,
                neverShown,
                Product.canAddVariant(axisCount, variantCount),
                neverShown ? null : DeleteBlockedReason.ALREADY_SHOWN);
    }
}
