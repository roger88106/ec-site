package com.ecsite.module.reporting.domain;

import java.util.List;

/**
 * 売上の数え方（注文基準・出荷基準）
 */
public enum Basis {

    /**
     * 注文基準：受付・キャンセル申請中・出荷済みの注文を、注文日で数える
     */
    ORDER(List.of("ACCEPTED", "CANCELLATION_REQUESTED", "SHIPPED")),

    /**
     * 出荷基準：出荷済みの注文を、出荷日で数える（出荷の後のキャンセルは返送の返金なので、状態が出荷済みでないものは除く）
     */
    SHIPMENT(List.of("SHIPPED")),
    ;

    /**
     * 数える注文の状態
     */
    private final List<String> countedStatuses;

    /**
     * @param countedStatuses 数える注文の状態
     */
    Basis(List<String> countedStatuses) {
        this.countedStatuses = countedStatuses;
    }

    /**
     * @return 数える注文の状態（注文の状態の名前）
     */
    public List<String> countedStatuses() {
        return countedStatuses;
    }
}
