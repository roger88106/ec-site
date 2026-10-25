package com.ecsite.module.cart.application.port;

import com.ecsite.module.cart.domain.Cart;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * カートのリポジトリ
 */
public interface CartRepository {

    /**
     * カートを作る。<br />
     * 会員のカートは、同じ会員のカートがすでにあれば何も保存せずに false を返す（同じ会員の同時の初回の要求）。トランザクションは壊さない。
     *
     * @param cart 空のカート
     * @return 保存したら true
     */
    boolean create(Cart cart);

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

    /**
     * 最後の変更が指定した時刻より前のカートを、品目と共に、件数の上限まで消す（片付け）。
     *
     * @param updatedBefore 最後の変更の時刻の上限
     * @param limit         消す件数の上限
     * @return 消した件数
     */
    int deleteUpdatedBefore(Instant updatedBefore, int limit);
}
