package com.ecsite.module.cart.application.port;

import com.ecsite.common.error.exception.BusinessException;
import com.ecsite.module.cart.domain.Cart;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * カートのリポジトリ
 */
public interface CartRepository {

    /**
     * カートを作る。
     *
     * <p>会員のカートは、同じ会員のカートがすでにあれば何も保存せずに false を返す。トランザクションは壊さない。
     *
     * @param cart 空のカート
     * @return 保存したら true
     */
    boolean create(Cart cart);

    /**
     * 会員のカートを作り、カート ID を返す。
     *
     * <p>同じ会員のカートがすでにあれば、何も変えずにそのカート ID を返す。1 つの文で行い、同時に作られたときも、
     * 先に作られたカートの ID を返す。
     *
     * @param cart 会員の空のカート
     * @return カート ID（作ったとき {@code cart} の ID、すでにあったとき既存のカートの ID）
     */
    UUID createForMemberOrGetId(Cart cart);

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
     * カートの変更を保存する。
     *
     * <p>品目は置き換える。
     *
     * @param cart 変更したカート
     * @throws BusinessException 読み込んだ後に他で変更された（保存されているバージョンが 1 つ前でない。{@code CONCURRENT_MODIFICATION}）
     */
    void save(Cart cart);

    /**
     * 最後の変更が指定した時刻より前のカートを、品目と共に、件数の上限まで消す。
     *
     * @param updatedBefore 最後の変更の時刻の上限
     * @param limit         消す件数の上限
     * @return 消した件数
     */
    int deleteUpdatedBefore(Instant updatedBefore, int limit);
}
