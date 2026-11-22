package com.ecsite.module.order.application.model;

import com.ecsite.common.model.id.CartId;
import com.ecsite.common.model.id.MemberId;
import com.ecsite.common.model.id.VariantId;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * カートの中身
 *
 * @param cartId   カート ID
 * @param memberId 会員 ID（ログインせずに作ったカートは null）
 * @param version  バージョン
 * @param lines    品目（入れた順）
 */
public record CartSnapshot(CartId cartId, @Nullable MemberId memberId, long version, List<Line> lines) {

    /**
     * リクエストした会員がカートを使えるか。
     *
     * <p>会員のカートは持ち主の会員だけ、会員を持たないカートはだれでも。
     *
     * @param requester リクエストした会員の ID（ログインしていなければ null）
     * @return 使えれば true
     */
    public boolean isAccessibleBy(@Nullable MemberId requester) {
        return memberId == null || memberId.equals(requester);
    }

    /**
     * 会員のカートで、その会員が持ち主かを返す。
     *
     * @param memberId 会員 ID
     * @return 会員のカートで、その会員が持ち主なら true
     */
    public boolean isOwnedBy(MemberId memberId) {
        return this.memberId != null && this.memberId.equals(memberId);
    }

    /**
     * 品目
     *
     * @param variantId バリアント ID
     * @param quantity  数量
     */
    public record Line(VariantId variantId, int quantity) {
    }
}
