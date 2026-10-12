package com.ecsite.module.inventory.presentation;

import com.ecsite.api.admin.model.AdjustStockRequest;
import com.ecsite.api.admin.model.StockAdjustment;
import com.ecsite.api.admin.model.StockAdjustmentPage;
import com.ecsite.api.admin.model.StockAdjustmentReason;
import com.ecsite.api.admin.model.StockSummary;
import com.ecsite.common.paging.PageResult;
import com.ecsite.common.model.Operator;
import com.ecsite.module.inventory.application.model.command.AdjustStockCommand;
import com.ecsite.module.inventory.application.model.view.StockAdjustmentView;
import com.ecsite.module.inventory.application.model.view.StockSummaryView;
import org.springframework.stereotype.Component;

import java.time.ZoneOffset;
import java.util.UUID;

/**
 * 在庫 API のリクエスト・レスポンスと、アプリケーション層のモデルの変換
 */
@Component
class InventoryMapper {

    /**
     * 在庫の調整コマンドに変換する。
     *
     * @param variantId バリアント ID
     * @param request   在庫の調整リクエスト
     * @param operator  調整した運営者（token の運営者）
     * @return 在庫の調整コマンド
     */
    AdjustStockCommand toAdjustStockCommand(UUID variantId, AdjustStockRequest request, Operator operator) {
        return new AdjustStockCommand(variantId, request.getDelta(), request.getReason().getValue(),
                request.getMemo().orElse(null), operator);
    }

    /**
     * 在庫調整のレスポンスに変換する。
     *
     * @param view 在庫調整のビュー
     * @return 在庫調整のレスポンス
     */
    StockAdjustment toStockAdjustment(StockAdjustmentView view) {
        return new StockAdjustment(view.id(), view.delta(), StockAdjustmentReason.fromValue(view.reason()),
                view.quantityAfter(), view.adjustedAt().atOffset(ZoneOffset.UTC))
                .memo(view.memo())
                .orderId(view.orderId())
                .orderNumber(view.orderNumber())
                .operatorName(view.operatorName());
    }

    /**
     * 調整履歴のページのレスポンスに変換する。
     *
     * @param page 調整履歴のページ
     * @return 調整履歴のページのレスポンス
     */
    StockAdjustmentPage toStockAdjustmentPage(PageResult<StockAdjustmentView> page) {
        return new StockAdjustmentPage(page.page(), page.size(), page.totalItems(), page.totalPages(),
                page.items().stream().map(this::toStockAdjustment).toList());
    }

    /**
     * 在庫数の合計のレスポンスに変換する。
     *
     * @param view 在庫数の合計のビュー
     * @return 在庫数の合計のレスポンス
     */
    StockSummary toStockSummary(StockSummaryView view) {
        return new StockSummary(view.productId(), view.totalQuantity());
    }
}
