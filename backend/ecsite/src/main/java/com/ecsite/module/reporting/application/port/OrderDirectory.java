package com.ecsite.module.reporting.application.port;

import com.ecsite.module.reporting.domain.ReportOrder;

import java.util.Optional;
import java.util.UUID;

/**
 * 注文の腐敗防止層（レポート用の注文の写しの API）
 */
public interface OrderDirectory {

    /**
     * 注文の今の写しを読む。明細の商品 ID、出荷日時、配送状態は空（注文は持たない）。
     *
     * @param orderId 注文 ID
     * @return 写し。注文がなければ空
     */
    Optional<ReportOrder> find(UUID orderId);
}
