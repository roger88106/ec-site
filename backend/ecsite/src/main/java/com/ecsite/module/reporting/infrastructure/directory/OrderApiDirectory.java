package com.ecsite.module.reporting.infrastructure.directory;

import com.ecsite.module.order.api.OrderReportApi;
import com.ecsite.module.order.api.OrderReportSnapshot;
import com.ecsite.module.reporting.application.port.OrderDirectory;
import com.ecsite.module.reporting.domain.PrefectureCode;
import com.ecsite.module.reporting.domain.ReportOrder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * 注文の腐敗防止層の実装。注文モジュールの API（{@link OrderReportApi}）を呼び、お届け先の都道府県をコードに読み替える。
 */
@Slf4j
@Component
@RequiredArgsConstructor
class OrderApiDirectory implements OrderDirectory {

    /**
     * レポート用の注文の写しの API
     */
    private final OrderReportApi orderReportApi;

    @Override
    public Optional<ReportOrder> find(UUID orderId) {
        return orderReportApi.find(orderId).map(OrderApiDirectory::toReportOrder);
    }

    /**
     * 注文の API の型を、レポートの写しにする。
     */
    private static ReportOrder toReportOrder(OrderReportSnapshot snapshot) {
        var prefectureCode = PrefectureCode.of(snapshot.prefecture()).orElse(null);
        if (prefectureCode == null) {
            log.warn("都道府県をコードに読み替えられない: order={}, prefecture={}", snapshot.orderId(), snapshot.prefecture());
        }
        return new ReportOrder(
                snapshot.orderId(),
                snapshot.orderNumber(),
                snapshot.memberId(),
                snapshot.orderedAt(),
                snapshot.status().name(),
                null,
                null,
                snapshot.cancelledAt(),
                snapshot.cancellationKind() == null ? null : snapshot.cancellationKind().name(),
                snapshot.restocked(),
                snapshot.postalCode(),
                prefectureCode,
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
}
