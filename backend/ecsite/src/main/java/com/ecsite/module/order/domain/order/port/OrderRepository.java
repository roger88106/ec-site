package com.ecsite.module.order.domain.order.port;

import com.ecsite.module.order.domain.order.Order;
import com.ecsite.module.order.domain.order.vo.OrderNumber;

import java.util.Optional;
import java.util.UUID;

/**
 * 注文のリポジトリ
 */
public interface OrderRepository {

    /**
     * 新しい注文を保存し、登録されたイベントを発行する。<br />
     * 注文番号が重複したら何も保存せずに false を返す（呼ぶ側が番号を再作成する）。トランザクションは壊さない。
     *
     * @param order 注文
     * @return 保存したら true
     */
    boolean insert(Order order);

    /**
     * 注文の状態の変更（状態、キャンセルの申請、キャンセル）を保存し、登録されたイベントを発行する。バージョンを上げる。
     *
     * @param order 変更した注文
     */
    void update(Order order);

    /**
     * 注文を行ロックして取得する。同じ注文への操作を 1 つずつ処理し、在庫を二重に戻さないため。トランザクションの中で呼ぶ。
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
     * 注文番号が使われているか。
     *
     * @param orderNumber 注文番号
     * @return 使われていれば true
     */
    boolean existsByOrderNumber(OrderNumber orderNumber);
}
