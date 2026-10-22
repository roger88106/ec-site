package com.ecsite.module.reporting.application;

import com.ecsite.module.reporting.application.port.CatalogDirectory;
import com.ecsite.module.reporting.application.port.OrderDirectory;
import com.ecsite.module.reporting.application.port.ReportOrderRepository;
import com.ecsite.module.reporting.domain.DeliveryStatus;
import com.ecsite.module.reporting.domain.HistoryKind;
import com.ecsite.module.reporting.domain.ReportOrder;
import com.ecsite.module.reporting.domain.StatusHistoryEntry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 写しと状態の履歴を集めるサービス（docs/design/modules/reporting.md「写しの作り方」）。<br />
 * イベントを受けるたびに、履歴に 1 行追加し、注文の API から今の写しを読み直して上書きする。
 * イベントの中身で写しを差分で直さないので、イベントが何度・どの順で届いても、写しは同じになる。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportCollectionService {

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
     * 履歴に 1 行追加し、注文の写しを再作成する。同じ注文のイベントは、注文ごとのロックで 1 つずつ処理する
     * （同時に処理すると、互いの履歴が見えないまま写しを上書きするため）。
     *
     * @param entry 受けたイベントの履歴の行
     */
    @Transactional
    public void record(StatusHistoryEntry entry) {
        repository.lock(entry.orderId());
        repository.appendHistory(entry);
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
            if (productId != null) {
                result.put(line.lineNo(), productId);
            }
        });
        return result;
    }

    /**
     * 出荷日時：注文の状態が出荷済みになった変化、または配送状態が出荷済みになった変化の、早いほうの発生日時。
     */
    private static Instant shippedAt(List<StatusHistoryEntry> history) {
        return history.stream()
                .filter(StatusHistoryEntry::marksShipment)
                .map(StatusHistoryEntry::occurredAt)
                .min(Comparator.naturalOrder())
                .orElse(null);
    }

    /**
     * 配送状態：配送状態の変化の後の状態のうち、順位の最も高いもの。
     */
    private static DeliveryStatus deliveryStatus(List<StatusHistoryEntry> history) {
        return history.stream()
                .filter(entry -> entry.kind() == HistoryKind.DELIVERY_STATUS)
                .map(StatusHistoryEntry::deliveryStatus)
                .max(Comparator.comparingInt(DeliveryStatus::rank))
                .orElse(null);
    }
}
