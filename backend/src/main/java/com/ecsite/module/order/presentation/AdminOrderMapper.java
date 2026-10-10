package com.ecsite.module.order.presentation;

import com.ecsite.api.admin.model.Cancellation;
import com.ecsite.api.admin.model.CancellationRequest;
import com.ecsite.api.admin.model.OrderAmounts;
import com.ecsite.api.admin.model.OrderAxisValue;
import com.ecsite.api.admin.model.OrderDetail;
import com.ecsite.api.admin.model.OrderLine;
import com.ecsite.api.admin.model.OrderStatus;
import com.ecsite.api.admin.model.OrderSummary;
import com.ecsite.api.admin.model.OrderSummaryPage;
import com.ecsite.api.admin.model.OrderTaxAmount;
import com.ecsite.api.admin.model.ShippingAddress;
import com.ecsite.common.paging.PageResult;
import com.ecsite.module.order.application.OrderQueryRepository.OrderSummaryView;
import com.ecsite.module.order.domain.order.Order;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * 管理 API（注文）のレスポンスと、アプリケーション層のモデルの変換
 */
@Component
class AdminOrderMapper {

    /**
     * 注文一覧のページのレスポンスに変換する（AC-97、99）。
     *
     * @param page 注文一覧のページ
     * @return 注文一覧のページのレスポンス
     */
    OrderSummaryPage toOrderSummaryPage(PageResult<OrderSummaryView> page) {
        return new OrderSummaryPage(page.page(), page.size(), page.totalItems(), page.totalPages(),
                page.items().stream().map(view -> new OrderSummary(view.id(), view.orderNumber(),
                        toOffset(view.orderedAt()), view.customerName(), view.total(),
                        OrderStatus.fromValue(view.status().name()))).toList());
    }

    /**
     * 注文の詳細のレスポンスに変換する（AC-100）。申請・キャンセルがなければ、その項目を返さない。
     *
     * @param order 注文
     * @return 注文の詳細のレスポンス
     */
    OrderDetail toOrderDetail(Order order) {
        var amounts = order.amounts();
        var address = order.shippingAddress();
        var detail = new OrderDetail(order.id(), order.orderNumber().value(), toOffset(order.orderedAt()),
                OrderStatus.fromValue(order.status().name()),
                order.lines().stream().map(line -> new OrderLine(line.variantId(), line.productCode(),
                        line.variantCode(), line.productName(),
                        line.axisValues().stream().map(axis -> new OrderAxisValue(axis.name(), axis.value())).toList(),
                        line.unitPriceExclTax().yen(), line.unitPriceInclTax().yen(), line.ratePercent(),
                        line.quantity(), line.subtotal().yen())).toList(),
                new OrderAmounts(amounts.itemsTotal().yen(), amounts.shippingFee().yen(), amounts.total().yen(),
                        amounts.taxes().stream().map(tax -> new OrderTaxAmount(tax.ratePercent(),
                                tax.taxableAmount().yen(), tax.taxAmount().yen())).toList()),
                order.email(),
                new ShippingAddress(address.name(), address.postalCode(), address.prefecture(), address.address(),
                        address.phone()));
        var request = order.cancellationRequest();
        if (request != null) {
            detail.cancellationRequest(new CancellationRequest(toOffset(request.requestedAt()),
                    CancellationRequest.ResultEnum.fromValue(request.result().name()))
                    .reason(request.reason())
                    .decidedAt(toOffset(request.decidedAt())));
        }
        var cancellation = order.cancellation();
        if (cancellation != null) {
            detail.cancellation(new Cancellation(toOffset(cancellation.cancelledAt()), cancellation.byOperator())
                    .reason(cancellation.reason()));
        }
        return detail;
    }

    /**
     * 日時を、オフセット付きの日時（UTC）にする。null は null のまま。
     */
    private static OffsetDateTime toOffset(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }
}
