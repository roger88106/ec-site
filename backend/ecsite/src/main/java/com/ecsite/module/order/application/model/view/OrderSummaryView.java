package com.ecsite.module.order.application.model.view;

import com.ecsite.common.model.id.OrderId;
import com.ecsite.common.model.value.Money;
import com.ecsite.module.order.domain.order.OrderStatus;

import java.time.Instant;

/**
 * 注文一覧の 1 件
 *
 * @param id           注文 ID
 * @param orderNumber  注文番号
 * @param orderedAt    注文日時
 * @param customerName お届け先の氏名
 * @param total        総額
 * @param status       状態
 */
public record OrderSummaryView(OrderId id, String orderNumber, Instant orderedAt, String customerName, Money total,
                               OrderStatus status) {
}
