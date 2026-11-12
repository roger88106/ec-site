package com.ecsite.module.order.domain.order.port;

import com.ecsite.module.order.domain.order.Order;
import com.ecsite.module.order.domain.order.vo.OrderNumber;
import org.jmolecules.ddd.annotation.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 注文のリポジトリ
 */
@Repository
public interface OrderRepository {

    /**
     * 新しい注文を保存し、登録されたイベントを発行する。
     *
     * <p>注文番号が重複したら何も保存せずに false を返す。トランザクションは壊さない。
     *
     * @param order 注文
     * @return 保存したら true
     */
    boolean insert(Order order);

    /**
     * 注文の状態の変更（状態、キャンセルの申請、キャンセル）を保存し、登録されたイベントを発行する。
     *
     * <p>バージョンを上げる。
     *
     * @param order 変更した注文
     */
    void update(Order order);

    /**
     * 注文を行ロックして取得する。
     *
     * <p>ロックはトランザクションの終わりまで続く。
     *
     * @param id 注文 ID
     * @return 注文。なければ空
     */
    Optional<Order> lockById(UUID id);

    /**
     * 注文を取得する。
     *
     * @param id 注文 ID
     * @return 注文。なければ空
     */
    Optional<Order> findById(UUID id);

    /**
     * 注文手続きの注文 ID を取得する。
     *
     * @param checkoutId 注文手続き ID
     * @return 注文 ID。まだ注文がなければ空
     */
    Optional<UUID> findIdByCheckoutId(UUID checkoutId);

    /**
     * 会員の支払待ちの注文の ID を、注文日時の新しい順に取得する。
     *
     * <p>同じ日時なら ID の順。
     *
     * @param memberId 会員 ID
     * @return 注文 ID。支払待ちの注文がなければ空
     */
    List<UUID> findPendingIdsByMemberId(UUID memberId);

    /**
     * 会員ごとのロックを、トランザクションの終わりまで取る。
     *
     * <p>トランザクションの外では、すぐに解放され、ロックとして働かない。
     *
     * @param memberId 会員 ID
     */
    void lockForMember(UUID memberId);

    /**
     * 注文番号が使われているか。
     *
     * @param orderNumber 注文番号
     * @return 使われていれば true
     */
    boolean existsByOrderNumber(OrderNumber orderNumber);
}
