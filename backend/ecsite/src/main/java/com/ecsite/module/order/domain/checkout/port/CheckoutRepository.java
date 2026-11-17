package com.ecsite.module.order.domain.checkout.port;

import com.ecsite.module.order.domain.checkout.Checkout;
import org.jmolecules.ddd.annotation.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 注文手続きのリポジトリ
 */
@Repository
public interface CheckoutRepository {

    /**
     * 注文手続きを保存する。
     *
     * @param checkout 注文手続き
     */
    void insert(Checkout checkout);

    /**
     * 注文手続きを取得する。
     *
     * @param id 注文手続き ID
     * @return 注文手続き。なければ空
     */
    Optional<Checkout> findById(UUID id);

    /**
     * 注文手続きを行ロックして取得する。
     *
     * <p>ロックはトランザクションの終わりまで続く。
     *
     * @param id 注文手続き ID
     * @return 注文手続き。なければ空
     */
    Optional<Checkout> lockById(UUID id);

    /**
     * カートの注文手続きの ID を返す。
     *
     * @param cartId カート ID
     * @return 注文手続き ID
     */
    List<UUID> findIdsByCartId(UUID cartId);

    /**
     * カートの最新の注文手続きの ID を返す。
     *
     * @param cartId カート ID
     * @return 注文手続き ID。なければ空
     */
    Optional<UUID> findLatestIdByCartId(UUID cartId);

    /**
     * 期限が指定した時刻より前の注文手続きを、件数の上限まで消す。
     *
     * <p>注文になったものも消す。
     *
     * @param expiredBefore この時刻より前に期限が切れた注文手続きを消す
     * @param limit         消す件数の上限
     * @return 消した件数
     */
    int deleteExpiredBefore(Instant expiredBefore, int limit);
}
