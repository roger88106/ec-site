package com.ecsite.module.reporting.domain;

import java.util.List;

/**
 * 売上の数え方（注文基準・出荷基準）
 */
public enum Basis {

    /**
     * 注文基準：受付・キャンセル申請中・出荷済みの注文を、注文日で数える
     */
    ORDER(List.of(ReportOrderStatus.ACCEPTED, ReportOrderStatus.CANCELLATION_REQUESTED, ReportOrderStatus.SHIPPED)),

    /**
     * 出荷基準：出荷済みの注文を、出荷日で数える（出荷の後のキャンセルは返送の返金なので、状態が出荷済みでないものは除く）
     */
    SHIPMENT(List.of(ReportOrderStatus.SHIPPED)),
    ;

    /**
     * 数える注文の状態
     */
    private final List<ReportOrderStatus> countedStatuses;

    /**
     * @param countedStatuses 数える注文の状態
     */
    Basis(List<ReportOrderStatus> countedStatuses) {
        this.countedStatuses = countedStatuses;
    }

    /**
     * 数える注文の状態を返す。
     *
     * @return 数える注文の状態
     */
    public List<ReportOrderStatus> countedStatuses() {
        return countedStatuses;
    }

    /**
     * その状態の注文を数えるかを返す。
     *
     * @param status 注文の状態
     * @return その状態の注文を数えるか
     */
    public boolean counts(ReportOrderStatus status) {
        return countedStatuses.contains(status);
    }
}
