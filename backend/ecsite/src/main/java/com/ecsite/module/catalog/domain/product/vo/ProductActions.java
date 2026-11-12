package com.ecsite.module.catalog.domain.product.vo;

import org.jmolecules.ddd.annotation.ValueObject;
import org.jspecify.annotations.Nullable;

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
     * 商品を削除できない理由
     */
    public enum DeleteBlockedReason {

        /**
         * C 側に表示されたことがある（注文や閲覧の記録と結び付くので、削除せずに販売終了にする）
         */
        ALREADY_SHOWN
    }
}
