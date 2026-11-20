package com.ecsite.module.order.application.handler;

import com.ecsite.module.order.application.OrderCartCommandService;
import com.ecsite.module.order.event.OrderStatusChanged;
import lombok.RequiredArgsConstructor;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * 注文の状態の変化のリスナー
 *
 * <p>コミット後に非同期で、別のトランザクションで動く。何度届いても結果は同じ。
 * 処理に失敗したら、自分では再試行せず、未完了のイベントの再発行でやり直す。
 */
@Component
@RequiredArgsConstructor
class OrderStatusChangedHandler {

    /**
     * 注文したカートの更新のサービス
     */
    private final OrderCartCommandService orderCartCommandService;

    /**
     * 支払待ちから受付への遷移：注文した品目を、会員のカートから除く。
     *
     * <p>他の遷移（キャンセル申請中から受付に戻るなど）では除かない。
     *
     * @param event 注文の状態の変化
     */
    @ApplicationModuleListener
    void on(OrderStatusChanged event) {
        if (event.from() == OrderStatusChanged.Status.PENDING_PAYMENT && event.to() == OrderStatusChanged.Status.ACCEPTED) {
            orderCartCommandService.removeOrderedItems(event.orderId());
        }
    }
}
