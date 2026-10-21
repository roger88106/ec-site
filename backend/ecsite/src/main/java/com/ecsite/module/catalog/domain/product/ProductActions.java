package com.ecsite.module.catalog.domain.product;

import jakarta.annotation.Nullable;
import lombok.NonNull;

import java.time.Instant;

/**
 * 商品にできる操作（管理画面に返すフラグ）。画面はこれで操作を出し分け、同じ判定を持たない。<br />
 * 値は商品（集約）の規則から作る。すでにその状態で何もしない操作（公開中の公開など）は出さない。
 * 集約が別の理由で拒む操作（バリアントのない商品の公開など）はフラグでは止めず、操作したときの業務のエラーで知らせる。
 *
 * @param publish        公開できる
 * @param unpublish      非公開にできる
 * @param discontinue    販売終了にできる
 * @param delete         削除できる
 * @param deleteVariants バリアントを削除できる
 * @param addVariant     バリアントを追加できる
 * @param deleteBlockedReason 削除できない理由。削除できるなら null
 */
public record ProductActions(boolean publish, boolean unpublish, boolean discontinue, boolean delete,
                             boolean deleteVariants, boolean addVariant, @Nullable DeleteBlockedReason deleteBlockedReason) {

    /**
     * 商品の状態から、できる操作を決める。商品（集約）を読み込まないクエリでも使う。
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
