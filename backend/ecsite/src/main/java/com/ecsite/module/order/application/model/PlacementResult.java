package com.ecsite.module.order.application.model;

import java.util.UUID;

/**
 * 注文の結果
 *
 * @param orderId 注文 ID
 * @param created この要求で注文を作ったら true。同じ注文手続きでの再送で、作成済みの注文を返したら false
 */
public record PlacementResult(UUID orderId, boolean created) {
}
