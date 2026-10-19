package com.ecsite.module.reporting.infrastructure.directory;

import com.ecsite.module.order.api.OrderReportApi;
import com.ecsite.module.order.api.OrderReportSnapshot;
import com.ecsite.module.reporting.application.port.OrderDirectory;
import com.ecsite.module.reporting.domain.ReportOrder;
import com.ecsite.module.reporting.domain.ReportOrderStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * 注文の腐敗防止層の実装。注文モジュールの API（{@link OrderReportApi}）を呼ぶ。
 */
@Component
@RequiredArgsConstructor
class OrderApiDirectory implements OrderDirectory {

    /**
     * レポート用の注文の写しの API
     */
    private final OrderReportApi orderReportApi;

    /**
     * 注文の今の写しを、注文の API で読む。
     *
     * @param orderId 注文 ID
     * @return 写し。注文がなければ空
     */
    @Override
    public Optional<ReportOrder> find(UUID orderId) {
        return orderReportApi.find(orderId).map(OrderApiDirectory::toReportOrder);
    }

    /**
     * 注文の API の型を、レポートの写しにする。
     */
    private static ReportOrder toReportOrder(OrderReportSnapshot snapshot) {
        return new ReportOrder(
                snapshot.orderId(),
                snapshot.orderNumber(),
                snapshot.memberId(),
                snapshot.orderedAt(),
                toStatus(snapshot.status()),
                null,
                null,
                snapshot.cancelledAt(),
                snapshot.cancellationKind() == null ? null : snapshot.cancellationKind().name(),
                snapshot.restocked(),
                snapshot.postalCode(),
                // 注文の写しのコードのまま持つ（名前から引き直さない）
                snapshot.prefectureCode(),
                snapshot.buyerAge(),
                snapshot.buyerGender() == null ? null : snapshot.buyerGender().name(),
                snapshot.total(),
                snapshot.shippingFee(),
                snapshot.taxTotal(),
                snapshot.shippingTax(),
                snapshot.lines().stream().map(line -> new ReportOrder.Line(
                        line.lineNo(),
                        null,
                        line.variantId(),
                        line.productCode(),
                        line.variantCode(),
                        line.productName(),
                        line.axisValues().stream().map(axis -> new ReportOrder.AxisValue(axis.name(), axis.value())).toList(),
                        line.quantity(),
                        line.ratePercent(),
                        line.subtotalInclTax(),
                        line.subtotalExclTax())).toList());
    }

    /**
     * 注文の API の注文の状態を、レポートの型にする。
     */
    private static ReportOrderStatus toStatus(OrderReportSnapshot.Status status) {
        return switch (status) {
            case PENDING_PAYMENT -> ReportOrderStatus.PENDING_PAYMENT;
            case ACCEPTED -> ReportOrderStatus.ACCEPTED;
            case CANCELLATION_REQUESTED -> ReportOrderStatus.CANCELLATION_REQUESTED;
            case CANCELLED -> ReportOrderStatus.CANCELLED;
            case SHIPPED -> ReportOrderStatus.SHIPPED;
        };
    }
}
