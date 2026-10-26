package com.ecsite.module.order.application.model;

import com.ecsite.common.model.value.Money;
import com.ecsite.module.order.domain.order.BuyerProfile;
import com.ecsite.module.order.domain.order.Cancellation;
import com.ecsite.module.order.domain.order.OrderStatus;
import com.ecsite.module.order.domain.order.vo.OrderLine;
import com.ecsite.module.order.domain.order.vo.OrderNumber;
import com.ecsite.module.order.domain.order.vo.ShippingAddress;
import com.ecsite.module.order.domain.quote.Amounts;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * レポート用の注文の表示
 *
 * @param id              注文 ID
 * @param orderNumber     注文番号
 * @param memberId        会員 ID
 * @param orderedAt       注文日時
 * @param status          状態
 * @param cancellation    キャンセル。なければ null
 * @param shippingAddress お届け先
 * @param buyerProfile    注文時の年齢と性別
 * @param amounts         金額の内訳
 * @param shippingTax     送料の消費税
 * @param lines           明細
 */
public record OrderReportView(UUID id, OrderNumber orderNumber, UUID memberId, Instant orderedAt, OrderStatus status,
                              @Nullable Cancellation cancellation, ShippingAddress shippingAddress,
                              BuyerProfile buyerProfile, Amounts amounts, Money shippingTax, List<OrderLine> lines) {
}
