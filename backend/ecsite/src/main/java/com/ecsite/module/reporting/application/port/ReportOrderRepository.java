package com.ecsite.module.reporting.application.port;

import com.ecsite.module.reporting.domain.ReportOrder;
import com.ecsite.module.reporting.domain.StatusHistoryEntry;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 注文の写しと状態の履歴のリポジトリ
 */
public interface ReportOrderRepository {

    /**
     * 注文ごとのロックを、トランザクションの終わりまで取る。同じ注文のイベントを同時に受けても、1 つずつ処理するため。
     *
     * @param orderId 注文 ID
     */
    void lock(UUID orderId);

    /**
     * 状態の履歴に 1 行追加する。同じイベント ID の行があれば何もしない。
     *
     * @param entry 履歴の行
     * @return 追加したら true
     */
    boolean appendHistory(StatusHistoryEntry entry);

    /**
     * 注文の状態の履歴を、発生日時の順に読む。
     *
     * @param orderId 注文 ID
     * @return 履歴
     */
    List<StatusHistoryEntry> history(UUID orderId);

    /**
     * 写しの明細の商品 ID を読む。
     *
     * @param orderId 注文 ID
     * @return 行番号 → 商品 ID。写しがない、または商品 ID がない行は含めない
     */
    Map<Integer, UUID> productIds(UUID orderId);

    /**
     * 写しを上書きする（なければ作る）。明細は消して入れ直す。
     *
     * @param order 写し
     */
    void save(ReportOrder order);
}
