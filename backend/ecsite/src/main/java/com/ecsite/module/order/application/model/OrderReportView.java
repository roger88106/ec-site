package com.ecsite.module.order.application.model;

import com.ecsite.common.model.value.Money;
import com.ecsite.module.order.domain.order.Order;

/**
 * レポート用の注文と送料の消費税
 *
 * @param order       注文
 * @param shippingTax 送料の消費税
 */
public record OrderReportView(Order order, Money shippingTax) {
}
