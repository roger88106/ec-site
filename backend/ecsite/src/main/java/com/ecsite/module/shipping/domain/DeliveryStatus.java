package com.ecsite.module.shipping.domain;

import com.ecsite.module.shipping.event.DeliveryStatusChanged;

/**
 * 配送状態
 *
 * <p>順位を持ち、今より高い順位にだけ進める。
 */
public enum DeliveryStatus {

    /**
     * 出荷済み。運営者が出荷を登録した。物流サービスはまだ荷物を受け取っていない
     */
    SHIPPED(0),

    /**
     * 配送中。物流サービスが荷物を受け取り、届けるまでの間
     */
    IN_TRANSIT(1),

    /**
     * 配達完了。荷物を届けた
     */
    DELIVERED(2),

    /**
     * 返送。届けられず、荷物が送り主に戻った
     */
    RETURNED(2),
    ;

    /**
     * 順位。同じ順位の状態には進めない
     */
    private final int rank;

    /**
     * 順位を持つ配送状態を作る。
     *
     * @param rank 順位
     */
    DeliveryStatus(int rank) {
        this.rank = rank;
    }

    /**
     * 指定した状態へ進めるかを返す。
     *
     * <p>今より高い順位だけ。
     *
     * @param next 進める先の状態
     * @return 進めれば true
     */
    public boolean canAdvanceTo(DeliveryStatus next) {
        return next.rank > rank;
    }

    /**
     * 配送状態を、イベントの配送状態にする。
     *
     * @return イベントの配送状態
     */
    public DeliveryStatusChanged.Status toEventStatus() {
        return switch (this) {
            case SHIPPED -> DeliveryStatusChanged.Status.SHIPPED;
            case IN_TRANSIT -> DeliveryStatusChanged.Status.IN_TRANSIT;
            case DELIVERED -> DeliveryStatusChanged.Status.DELIVERED;
            case RETURNED -> DeliveryStatusChanged.Status.RETURNED;
        };
    }
}
