package com.ecsite.module.order.application.port;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * カートの腐敗防止層（バックエンドのアーキテクチャ 第 1 節）
 */
public interface CartDirectory {

    /**
     * カートの中身を返す。期限切れのカートは品目が空。
     *
     * @param cartId カート ID
     * @return カートの中身。カートがなければ空
     */
    Optional<CartSnapshot> find(UUID cartId);

    /**
     * カートを空にする（注文の成立）。
     *
     * @param cartId カート ID
     */
    void clear(UUID cartId);

    /**
     * カートの中身
     *
     * @param cartId   カート ID
     * @param memberId 会員 ID（ログインせずに作ったカートは null）
     * @param version  バージョン（注文手続きの後の変更を見つけるため）
     * @param lines    品目（入れた順）
     */
    record CartSnapshot(UUID cartId, UUID memberId, long version, List<Line> lines) {

        /**
         * 要求した会員がカートを使えるか。会員のカートは持ち主の会員だけ、会員を持たないカートはだれでも。
         *
         * @param requester 要求した会員の ID（ログインしていなければ null）
         * @return 使えれば true
         */
        public boolean isAccessibleBy(UUID requester) {
            return memberId == null || memberId.equals(requester);
        }

        /**
         * @param memberId 会員 ID
         * @return 会員のカートで、その会員が持ち主なら true（注文手続きを始められるカート）
         */
        public boolean isOwnedBy(UUID memberId) {
            return this.memberId != null && this.memberId.equals(memberId);
        }
    }

    /**
     * 品目
     *
     * @param variantId バリアント ID
     * @param quantity  数量
     */
    record Line(UUID variantId, int quantity) {
    }
}
