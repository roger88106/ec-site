package com.ecsite.module.reporting.domain.snapshot;

/**
 * 配送状態（docs/design/modules/shipping.md「配送状態と順位」の写し）。<br />
 * 写しの配送状態は、状態の履歴のうち順位の最も高いものにする（イベントの届く順によらないため）。
 */
public enum DeliveryStatus implements HistoryStatus {

    /**
     * 出荷済み
     */
    SHIPPED(0),

    /**
     * 配送中
     */
    IN_TRANSIT(1),

    /**
     * 配達完了
     */
    DELIVERED(2),

    /**
     * 返送
     */
    RETURNED(2),
    ;

    /**
     * 順位
     */
    private final int rank;

    /**
     * @param rank 順位
     */
    DeliveryStatus(int rank) {
        this.rank = rank;
    }

    /**
     * 配送状態の順位を返す。
     *
     * @return 順位（出荷済み 0、配送中 1、配達完了・返送 2）
     */
    public int rank() {
        return rank;
    }
}
