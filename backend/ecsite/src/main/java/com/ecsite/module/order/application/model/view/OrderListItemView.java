package com.ecsite.module.order.application.model.view;

import org.jspecify.annotations.Nullable;

/**
 * 注文の一覧の 1 件の表示
 *
 * @param order                 注文の概要
 * @param payment               注文の支払。決済を入れる前の注文は null
 * @param shipment              注文の出荷。出荷していなければ null
 * @param authorizationExpiring 与信期限が近い印を付ける（受付の注文で、支払の与信期限が近い）
 */
public record OrderListItemView(OrderSummaryView order, @Nullable OrderPayment payment,
                                @Nullable OrderShipment shipment, boolean authorizationExpiring) {
}
