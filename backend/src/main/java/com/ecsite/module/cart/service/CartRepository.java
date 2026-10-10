package com.ecsite.module.cart.service;

import java.util.Optional;
import java.util.UUID;

/**
 * カートのリポジトリ
 */
public interface CartRepository {

    /**
     * カートを作る。
     *
     * @param cart 空のカート
     */
    void create(Cart cart);

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
