package com.ecsite.module.reporting.infrastructure.directory;

import com.ecsite.module.order.api.OrderReportApi;
import com.ecsite.module.order.api.OrderReportSnapshot;
import com.ecsite.module.reporting.application.port.OrderDirectory;
import com.ecsite.module.reporting.domain.snapshot.ReportBuyerGender;
import com.ecsite.module.reporting.domain.snapshot.ReportCancellationKind;
import com.ecsite.module.reporting.domain.sales.ReportGender;
import com.ecsite.module.reporting.domain.snapshot.ReportOrder;
import com.ecsite.module.reporting.domain.snapshot.ReportOrderStatus;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
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
                toCancellationKind(snapshot.cancellationKind()),
                snapshot.restocked(),
                snapshot.postalCode(),
                // 注文の写しのコードのまま持つ（名前から引き直さない）
                snapshot.prefectureCode(),
                snapshot.buyerAge(),
                toBuyerGender(snapshot.buyerGender()),
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
    static ReportOrderStatus toStatus(OrderReportSnapshot.Status status) {
        return switch (status) {
            case PENDING_PAYMENT -> ReportOrderStatus.PENDING_PAYMENT;
            case ACCEPTED -> ReportOrderStatus.ACCEPTED;
            case CANCELLATION_REQUESTED -> ReportOrderStatus.CANCELLATION_REQUESTED;
            case CANCELLED -> ReportOrderStatus.CANCELLED;
            case SHIPPED -> ReportOrderStatus.SHIPPED;
        };
    }

    /**
     * 注文の API のキャンセルの種類を、レポートの型にして写しに入れる。
     *
     * @param kind キャンセルの種類。キャンセルでなければ null
     * @return レポートのキャンセルの種類。キャンセルでなければ null
     */
    static @Nullable ReportCancellationKind toCancellationKind(OrderReportSnapshot.@Nullable CancellationKind kind) {
        if (kind == null) {
            return null;
        }
        return switch (kind) {
            case REQUEST_APPROVED -> ReportCancellationKind.REQUEST_APPROVED;
            case OPERATOR -> ReportCancellationKind.OPERATOR;
            case PAYMENT_EXPIRED -> ReportCancellationKind.PAYMENT_EXPIRED;
        };
    }

    /**
     * 注文の API の性別を、レポートの注文時の性別にして写しに入れる。<br />
     * 「回答しない」を不明にまとめるのは、読むとき（{@link ReportGender#of}）に行う。
     *
     * @param gender 注文時の性別。分からなければ null
     * @return 写しの性別。分からなければ null
     */
    static @Nullable ReportBuyerGender toBuyerGender(OrderReportSnapshot.@Nullable Gender gender) {
        if (gender == null) {
            return null;
        }
        return switch (gender) {
            case MALE -> ReportBuyerGender.MALE;
            case FEMALE -> ReportBuyerGender.FEMALE;
            case OTHER -> ReportBuyerGender.OTHER;
            case UNDISCLOSED -> ReportBuyerGender.UNDISCLOSED;
        };
    }
}
