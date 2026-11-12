package com.ecsite.module.reporting.application;

import com.ecsite.module.order.event.OrderStatusChanged;
import com.ecsite.module.reporting.application.port.CatalogDirectory;
import com.ecsite.module.reporting.application.port.OrderDirectory;
import com.ecsite.module.reporting.application.port.ReportOrderRepository;
import com.ecsite.module.reporting.domain.snapshot.DeliveryStatus;
import com.ecsite.module.reporting.domain.snapshot.HistoryKind;
import com.ecsite.module.reporting.domain.snapshot.ReportCancellationKind;
import com.ecsite.module.reporting.domain.snapshot.ReportOrder;
import com.ecsite.module.reporting.domain.snapshot.ReportOrderStatus;
import com.ecsite.module.reporting.domain.snapshot.StatusHistoryEntry;
import com.ecsite.module.shipping.event.DeliveryStatusChanged;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 写しと状態の履歴を集めるサービス
 *
 * <p>写しはイベントの中身で差分で直さず、注文の API から読み直して上書きするので、イベントが何度・どの順で届いても同じになる。
 */
@Slf4j
@Service
@RequiredArgsConstructor
class ReportCollectionService {

    /**
     * 注文の写しと状態の履歴のリポジトリ
     */
    private final ReportOrderRepository repository;

    /**
     * 注文の腐敗防止層
     */
    private final OrderDirectory orderDirectory;

    /**
     * 商品カタログの腐敗防止層
     */
    private final CatalogDirectory catalogDirectory;

    /**
     * 注文の状態の変化を履歴に 1 行追加し、注文の写しを再作成する。履歴の状態は、レポートの列挙で持つ。
     *
     * @param event 注文の状態の変化
     */
    @Transactional
    public void record(@NonNull OrderStatusChanged event) {
        recordEntry(new StatusHistoryEntry(event.eventId(), event.orderId(), HistoryKind.ORDER_STATUS,
                event.from() == null ? null : toOrderStatus(event.from()), toOrderStatus(event.to()),
                event.occurredAt(),
                event.cancellationKind() == null ? null : toCancellationKind(event.cancellationKind()),
                event.restocked()));
    }

    /**
     * 配送状態の変化を履歴に 1 行追加し、注文の写しを再作成する。履歴の状態は、レポートの列挙で持つ。
     *
     * @param event 配送状態の変化
     */
    @Transactional
    public void record(@NonNull DeliveryStatusChanged event) {
        recordEntry(new StatusHistoryEntry(event.eventId(), event.orderId(), HistoryKind.DELIVERY_STATUS,
                event.from() == null ? null : toDeliveryStatus(event.from()), toDeliveryStatus(event.to()),
                event.occurredAt(), null, null));
    }

    /**
     * 注文のイベントの状態を、レポートの型にする。
     *
     * @param status 注文のイベントの状態
     * @return レポートの注文の状態
     */
    static ReportOrderStatus toOrderStatus(OrderStatusChanged.Status status) {
        return switch (status) {
            case PENDING_PAYMENT -> ReportOrderStatus.PENDING_PAYMENT;
            case ACCEPTED -> ReportOrderStatus.ACCEPTED;
            case CANCELLATION_REQUESTED -> ReportOrderStatus.CANCELLATION_REQUESTED;
            case CANCELLED -> ReportOrderStatus.CANCELLED;
            case SHIPPED -> ReportOrderStatus.SHIPPED;
        };
    }

    /**
     * 注文のイベントのキャンセルの種類を、レポートの型にする。
     *
     * @param kind 注文のイベントのキャンセルの種類
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
     * 出荷のイベントの配送状態を、レポートの型にする。
     *
     * @param status 出荷のイベントの配送状態
     * @return レポートの配送状態
     */
    static DeliveryStatus toDeliveryStatus(DeliveryStatusChanged.Status status) {
        return switch (status) {
            case SHIPPED -> DeliveryStatus.SHIPPED;
            case IN_TRANSIT -> DeliveryStatus.IN_TRANSIT;
            case DELIVERED -> DeliveryStatus.DELIVERED;
            case RETURNED -> DeliveryStatus.RETURNED;
        };
    }

// ============================== プライベートメソッド ==============================

    /**
     * 履歴に 1 行追加し、注文の写しを再作成する。
     */
    private void recordEntry(StatusHistoryEntry entry) {
        // 同じ注文のイベントは、注文ごとのロックで 1 つずつ処理する（同時に処理すると、互いの履歴が見えないまま写しを上書きするため）
        repository.lock(entry.orderId());
        // 状態の履歴の追加
        repository.appendHistory(entry);
        // 注文の写しの再作成
        refresh(entry.orderId());
    }

    /**
     * 注文の写しを再作成する。
     */
    private void refresh(UUID orderId) {
        var found = orderDirectory.find(orderId);
        if (found.isEmpty()) {
            // 注文の API がない注文のイベントは来ないはずだが、来たら履歴だけ残す
            log.warn("写しを作れない注文のイベントを受けた: {}", orderId);
            return;
        }
        var order = found.get();
        var history = repository.history(orderId);
        repository.save(order.withProductIds(productIds(order)).withShipment(shippedAt(history), deliveryStatus(history)));
    }

    /**
     * 明細の商品 ID を、商品カタログの API で求める。求められなければ、前の写しの商品 ID を残す。
     */
    private Map<Integer, UUID> productIds(ReportOrder order) {
        var byVariant = catalogDirectory.productIdsOf(order.lines().stream().map(ReportOrder.Line::variantId).toList());
        var result = new HashMap<>(repository.productIds(order.orderId()));
        order.lines().forEach(line -> {
            var productId = byVariant.get(line.variantId());
            // 商品カタログで求められた行だけを置き換え、求められない行は前の写しの商品 ID のままにする
            if (productId != null) {
                result.put(line.lineNo(), productId);
            }
        });
        return result;
    }

    /**
     * 出荷日時を求める。注文の状態が出荷済みになった変化と、配送状態が出荷済みになった変化のうち、早いほうの発生日時。
     */
    private static @Nullable Instant shippedAt(List<StatusHistoryEntry> history) {
        return history.stream()
                .filter(StatusHistoryEntry::marksShipment)
                .map(StatusHistoryEntry::occurredAt)
                .min(Comparator.naturalOrder())
                .orElse(null);
    }

    /**
     * 配送状態を求める。配送状態の変化の後の状態のうち、順位の最も高いもの。
     */
    private static @Nullable DeliveryStatus deliveryStatus(List<StatusHistoryEntry> history) {
        // 届いた順でなく順位で決める（イベントの届く順によらないため）
        return history.stream()
                .filter(entry -> entry.kind() == HistoryKind.DELIVERY_STATUS)
                .map(StatusHistoryEntry::deliveryStatus)
                .max(Comparator.comparingInt(DeliveryStatus::rank))
                .orElse(null);
    }
}
