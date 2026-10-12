package com.ecsite.module.inventory.presentation;

import com.ecsite.api.admin.InventoryApi;
import com.ecsite.api.admin.model.AdjustStockRequest;
import com.ecsite.api.admin.model.Stock;
import com.ecsite.api.admin.model.StockAdjustment;
import com.ecsite.api.admin.model.StockAdjustmentPage;
import com.ecsite.api.admin.model.StockSummaryList;
import com.ecsite.common.paging.PageRequest;
import com.ecsite.common.security.CurrentOperator;
import com.ecsite.module.inventory.application.StockCommandService;
import com.ecsite.module.inventory.application.StockQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * 在庫のコントローラー（B 側。admin.yaml の tag: inventory）
 */
@RestController
@RequiredArgsConstructor
class InventoryController implements InventoryApi {

    /**
     * 在庫コマンドサービス
     */
    private final StockCommandService commandService;

    /**
     * 在庫クエリサービス
     */
    private final StockQueryService queryService;

    /**
     * 在庫マッパー
     */
    private final InventoryMapper mapper;

    /**
     * 要求の token の運営者
     */
    private final CurrentOperator currentOperator;

// ============================== GET ==============================

    /**
     * バリアントの在庫数
     *
     * @param variantId バリアント ID
     * @return Http 200 : {@link Stock}
     */
    @Override
    public ResponseEntity<Stock> getStock(UUID variantId) {
        return ResponseEntity.ok(new Stock(variantId, queryService.getQuantity(variantId)));
    }

    /**
     * 在庫調整の履歴
     *
     * @param variantId バリアント ID
     * @param page      ページ番号
     * @param size      1 ページの件数
     * @return Http 200 : {@link StockAdjustmentPage}
     */
    @Override
    public ResponseEntity<StockAdjustmentPage> listStockAdjustments(UUID variantId, Integer page, Integer size) {
        var result = queryService.listAdjustments(variantId, PageRequest.of(page, size));
        return ResponseEntity.ok(mapper.toStockAdjustmentPage(result));
    }

    /**
     * 商品ごとの在庫数の合計
     *
     * @param productIds 商品 ID
     * @return Http 200 : {@link StockSummaryList}
     */
    @Override
    public ResponseEntity<StockSummaryList> listStockSummaries(List<UUID> productIds) {
        var items = queryService.listStockSummaries(productIds).stream().map(mapper::toStockSummary).toList();
        return ResponseEntity.ok(new StockSummaryList(items));
    }

// ============================== POST ==============================

    /**
     * 在庫の調整。履歴の 1 件で単独の URL を持たないので、Location は返さない
     *
     * @param variantId バリアント ID
     * @param request   在庫の調整リクエスト
     * @return Http 201 : {@link StockAdjustment}
     */
    @Override
    public ResponseEntity<StockAdjustment> adjustStock(UUID variantId, AdjustStockRequest request) {
        var adjustment = commandService.adjustStock(mapper.toAdjustStockCommand(variantId, request, currentOperator.require()));
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toStockAdjustment(adjustment));
    }
}
