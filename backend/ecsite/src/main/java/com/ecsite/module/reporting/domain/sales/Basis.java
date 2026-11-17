package com.ecsite.module.reporting.domain.sales;

import com.ecsite.module.reporting.domain.snapshot.ReportOrderStatus;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import java.util.List;

/**
 * 売上の数え方
 */
@RequiredArgsConstructor
public enum Basis {

    /**
     * 注文基準
     *
     * <p>受付・キャンセル申請中・出荷済みの注文を、注文日で数える。
     */
    ORDER(List.of(ReportOrderStatus.ACCEPTED, ReportOrderStatus.CANCELLATION_REQUESTED, ReportOrderStatus.SHIPPED)),

    /**
     * 出荷基準
     *
     * <p>出荷済みの注文を、出荷日で数える。出荷の後のキャンセルは返送の返金なので、状態が出荷済みでないものは除く。
     */
    SHIPMENT(List.of(ReportOrderStatus.SHIPPED)),
    ;

    /**
     * 数える注文の状態
     */
    @NonNull
    private final List<ReportOrderStatus> countedStatuses;

    /**
     * 数える注文の状態を返す。
     *
     * @return 数える注文の状態
     */
    public List<ReportOrderStatus> countedStatuses() {
        return countedStatuses;
    }
}
