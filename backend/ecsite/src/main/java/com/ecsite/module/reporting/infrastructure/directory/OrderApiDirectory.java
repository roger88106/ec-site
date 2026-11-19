package com.ecsite.module.reporting.infrastructure.directory;

import com.ecsite.common.model.value.Money;
import com.ecsite.module.order.api.OrderApi;
import com.ecsite.module.order.api.model.OrderSnapshot;
import com.ecsite.module.order.api.model.TaxAmount;
import com.ecsite.module.order.event.OrderStatusChanged;
import com.ecsite.module.reporting.application.port.OrderDirectory;
import com.ecsite.module.reporting.domain.snapshot.ReportBuyerGender;
import com.ecsite.module.reporting.domain.snapshot.ReportCancellationKind;
import com.ecsite.module.reporting.domain.snapshot.ReportOrder;
import com.ecsite.module.reporting.domain.snapshot.ReportOrderStatus;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 注文の腐敗防止層
 *
 * <p>注文モジュールの API を呼び、レポートの写しに変換する。
 */
@Component
@RequiredArgsConstructor
class OrderApiDirectory implements OrderDirectory {

    /**
     * 注文の API
     */
    private final OrderApi orderApi;

    /**
     * 注文の今の写しを、注文の API で読む。
     *
     * @param orderId 注文 ID
     * @return 写し。注文がなければ空
     */
    @Override
    public Optional<ReportOrder> find(UUID orderId) {
        return orderApi.find(orderId).map(OrderApiDirectory::toReportOrder);
    }

    /**
     * 注文の API の型を、レポートの写しにする。
     */
    static ReportOrder toReportOrder(OrderSnapshot snapshot) {
        var cancellation = snapshot.cancellation();
        return new ReportOrder(
                snapshot.orderId(),
                snapshot.orderNumber(),
                snapshot.memberId(),
                snapshot.orderedAt(),
                toStatus(snapshot.status()),
                null,
                null,
                cancellation == null ? null : cancellation.cancelledAt(),
                cancellation == null ? null : toCancellationKind(cancellation.kind()),
                cancellation == null ? null : cancellation.restocked(),
                snapshot.postalCode(),
                // 注文の写しのコードのまま持つ（名前から引き直さない）
                snapshot.prefectureCode(),
                snapshot.buyerAge(),
                toBuyerGender(snapshot.buyerGender()),
                snapshot.amounts().total(),
                snapshot.amounts().shippingFee(),
                taxTotal(snapshot.amounts().taxes()),
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
                        subtotalExclTax(line))).toList());
    }

    /**
     * 税率ごとの消費税額の合計を求める。
     *
     * @param taxes 税率ごとの消費税額
     * @return 消費税の合計
     */
    static Money taxTotal(List<TaxAmount> taxes) {
        return taxes.stream().map(TaxAmount::taxAmount).reduce(Money.ZERO, Money::plus);
    }

    /**
     * 明細の税抜小計（税抜単価 × 数量）を求める。
     *
     * @param line 明細
     * @return 税抜小計
     */
    static Money subtotalExclTax(OrderSnapshot.Line line) {
        return line.unitPriceExclTax().times(line.quantity());
    }

    /**
     * 注文の API の注文の状態を、レポートの型にする。
     *
     * @param status 注文の API の注文の状態
     * @return レポートの注文の状態
     */
    static ReportOrderStatus toStatus(OrderStatusChanged.Status status) {
        return switch (status) {
            case PENDING_PAYMENT -> ReportOrderStatus.PENDING_PAYMENT;
            case ACCEPTED -> ReportOrderStatus.ACCEPTED;
            case CANCELLATION_REQUESTED -> ReportOrderStatus.CANCELLATION_REQUESTED;
            case CANCELLED -> ReportOrderStatus.CANCELLED;
            case SHIPPED -> ReportOrderStatus.SHIPPED;
        };
    }

    /**
     * 注文の API のキャンセルの種類を、レポートの型にする。
     *
     * @param kind キャンセルの種類
     * @return レポートのキャンセルの種類
     */
    static ReportCancellationKind toCancellationKind(OrderStatusChanged.CancellationKind kind) {
        return switch (kind) {
            case REQUEST_APPROVED -> ReportCancellationKind.REQUEST_APPROVED;
            case OPERATOR -> ReportCancellationKind.OPERATOR;
            case PAYMENT_EXPIRED -> ReportCancellationKind.PAYMENT_EXPIRED;
        };
    }

    /**
     * 注文の API の性別を、レポートの注文時の性別にする。
     *
     * <p>「回答しない」は不明にまとめず、そのまま持つ。
     *
     * @param gender 注文時の性別。分からなければ null
     * @return 写しの性別。分からなければ null
     */
    static @Nullable ReportBuyerGender toBuyerGender(OrderSnapshot.@Nullable Gender gender) {
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
