package com.ecsite.module.cart.service;

import java.util.Optional;
import java.util.UUID;

/**
 * カートのリポジトリ
 */
public interface CartRepository {

    /**
     * カートを作る。会員のカートは、同じ会員のカートがすでにあれば何もしない（同じ会員の同時の初回の要求）。
     *
     * @param cart 空のカート
     */
    void create(Cart cart);

    /**
     * 会員のカートの ID を探す。
     *
     * @param memberId 会員 ID
     * @return カート ID。なければ空
     */
    Optional<UUID> findIdByMemberId(UUID memberId);

    /**
     * カートを取得する。
     *
     * @param id カート ID
     * @return カート（品目は入れた順）。なければ空
     */
    Optional<Cart> findById(UUID id);

    /**
     * カートの変更を保存する。品目は置き換える。<br />
     * 読み込んだ後に他で変更されていたら（保存されているバージョンが 1 つ前でない）、
     * {@code CONCURRENT_MODIFICATION} の業務のエラーを投げる。
     *
     * @param cart 変更したカート
     */
    void save(Cart cart);
}
