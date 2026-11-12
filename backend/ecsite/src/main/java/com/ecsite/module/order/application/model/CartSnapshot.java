package com.ecsite.module.order.application.model;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * カートの中身
 *
 * @param cartId   カート ID
 * @param memberId 会員 ID（ログインせずに作ったカートは null）
 * @param version  バージョン
 * @param lines    品目（入れた順）
 */
public record CartSnapshot(UUID cartId, @Nullable UUID memberId, long version, List<Line> lines) {

    /**
     * リクエストした会員がカートを使えるか。
     *
     * <p>会員のカートは持ち主の会員だけ、会員を持たないカートはだれでも。
     *
     * @param requester リクエストした会員の ID（ログインしていなければ null）
     * @return 使えれば true
     */
    public boolean isAccessibleBy(@Nullable UUID requester) {
        return memberId == null || memberId.equals(requester);
    }

    /**
     * 会員のカートで、その会員が持ち主かを返す。
     *
     * @param memberId 会員 ID
     * @return 会員のカートで、その会員が持ち主なら true
     */
    public boolean isOwnedBy(UUID memberId) {
        return this.memberId != null && this.memberId.equals(memberId);
    }

    /**
     * 品目
     *
     * @param variantId バリアント ID
     * @param quantity  数量
     */
    public record Line(UUID variantId, int quantity) {
    }
}
