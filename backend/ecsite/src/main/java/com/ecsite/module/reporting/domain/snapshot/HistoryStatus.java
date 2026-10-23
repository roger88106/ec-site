package com.ecsite.module.reporting.domain.snapshot;

/**
 * 状態の履歴の状態。出来事に応じて、注文の状態（{@link ReportOrderStatus}）か配送状態（{@link DeliveryStatus}）のどちらかを持つ。
 */
public sealed interface HistoryStatus permits ReportOrderStatus, DeliveryStatus {

    /**
     * 状態の名前を返す（履歴の列に保存する値）。
     *
     * @return 列挙の名前
     */
    String name();
}
