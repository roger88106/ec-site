package com.ecsite.module.inventory.presentation;

import com.ecsite.api.admin.model.AdjustStockRequest;
import com.ecsite.api.admin.model.ManualStockAdjustmentReason;
import com.ecsite.api.admin.model.Stock;
import com.ecsite.api.admin.model.StockAdjustment;
import com.ecsite.api.admin.model.StockAdjustmentPage;
import com.ecsite.api.admin.model.StockSummary;
import com.ecsite.api.admin.model.StockSummaryList;
import com.ecsite.common.model.value.Operator;
import com.ecsite.common.paging.PageResult;
import com.ecsite.module.inventory.application.model.command.AdjustStockCommand;
import com.ecsite.module.inventory.application.model.view.StockAdjustmentView;
import com.ecsite.module.inventory.application.model.view.StockSummaryView;
import com.ecsite.module.inventory.domain.stock.StockAdjustmentReason;
import org.springframework.stereotype.Component;

import java.time.ZoneOffset;
import java.util.List;
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
        return new AdjustStockCommand(variantId, request.getDelta(), toDomainReason(request.getReason()),
                request.getMemo().orElse(null), operator);
    }

    /**
     * リクエストの調整理由（運営者が選べるもの）を、ドメインの調整理由に変換する。
     *
     * @param reason リクエストの調整理由
     * @return ドメインの調整理由
     */
    static StockAdjustmentReason toDomainReason(ManualStockAdjustmentReason reason) {
        return switch (reason) {
            case ARRIVAL -> StockAdjustmentReason.ARRIVAL;
            case STOCKTAKE -> StockAdjustmentReason.STOCKTAKE;
            case DAMAGE_OR_LOSS -> StockAdjustmentReason.DAMAGE_OR_LOSS;
            case OTHER -> StockAdjustmentReason.OTHER;
        };
    }

    /**
     * ドメインの調整理由を、レスポンスの調整理由に変換する。
     *
     * @param reason ドメインの調整理由
     * @return レスポンスの調整理由
     */
    static com.ecsite.api.admin.model.StockAdjustmentReason toResponseReason(StockAdjustmentReason reason) {
        return switch (reason) {
            case ARRIVAL -> com.ecsite.api.admin.model.StockAdjustmentReason.ARRIVAL;
            case STOCKTAKE -> com.ecsite.api.admin.model.StockAdjustmentReason.STOCKTAKE;
            case DAMAGE_OR_LOSS -> com.ecsite.api.admin.model.StockAdjustmentReason.DAMAGE_OR_LOSS;
            case OTHER -> com.ecsite.api.admin.model.StockAdjustmentReason.OTHER;
            case ORDER -> com.ecsite.api.admin.model.StockAdjustmentReason.ORDER;
            case ORDER_CANCELLATION -> com.ecsite.api.admin.model.StockAdjustmentReason.ORDER_CANCELLATION;
        };
    }

    /**
     * 在庫調整のレスポンスに変換する。
     *
     * @param view 在庫調整のビュー
     * @return 在庫調整のレスポンス
     */
    StockAdjustment toStockAdjustment(StockAdjustmentView view) {
        return new StockAdjustment()
                .id(view.id())
                .delta(view.delta())
                .reason(toResponseReason(view.reason()))
                .quantityAfter(view.quantityAfter())
                .adjustedAt(view.adjustedAt().atOffset(ZoneOffset.UTC))
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
        return new StockAdjustmentPage()
                .page(page.page())
                .size(page.size())
                .totalItems(page.totalItems())
                .totalPages(page.totalPages())
                .items(page.items().stream().map(this::toStockAdjustment).toList());
    }

    /**
     * 在庫数の合計のレスポンスに変換する。
     *
     * @param view 在庫数の合計のビュー
     * @return 在庫数の合計のレスポンス
     */
    StockSummary toStockSummary(StockSummaryView view) {
        return new StockSummary()
                .productId(view.productId())
                .totalQuantity(view.totalQuantity());
    }

    /**
     * 在庫数の合計の一覧のレスポンスに変換する。
     *
     * @param views 在庫数の合計のビューの一覧
     * @return 在庫数の合計の一覧のレスポンス
     */
    StockSummaryList toStockSummaryList(List<StockSummaryView> views) {
        return new StockSummaryList().items(views.stream().map(this::toStockSummary).toList());
    }

    /**
     * バリアントの在庫数のレスポンスに変換する。
     *
     * @param variantId バリアント ID
     * @param quantity  在庫数
     * @return 在庫数のレスポンス
     */
    Stock toStock(UUID variantId, int quantity) {
        return new Stock()
                .variantId(variantId)
                .quantity(quantity);
    }
}
