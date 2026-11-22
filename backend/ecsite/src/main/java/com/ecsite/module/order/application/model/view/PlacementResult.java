package com.ecsite.module.order.application.model.view;

import com.ecsite.common.model.id.OrderId;

/**
 * 注文の結果
 *
 * @param orderId 注文 ID
 * @param created このリクエストで注文を作ったら true。同じ注文手続きでの再送で、作成済みの注文を返したら false
 */
public record PlacementResult(OrderId orderId, boolean created) {
}
