package com.ecsite;

import com.ecsite.module.inventory.domain.stock.StockAdjustment;
import com.ecsite.module.inventory.domain.stock.StockAdjustmentReason;
import com.ecsite.module.inventory.domain.stock.port.StockItemRepository;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * 統合テストで、バリアントの在庫を作り、入荷として調整する補助。<br />
 * 在庫の Repository を直接呼ぶ（API は使わない）。調整の操作者は {@link TestTokens#OPERATOR}、時刻は渡された時計から取る。
 */
public class InventoryTestData {

    /**
     * 在庫の Repository
     */
    private final StockItemRepository stockItemRepository;

    /**
     * 調整の時刻を取る時計
     */
    private final Clock clock;

    /**
     * コンストラクタ
     *
     * @param stockItemRepository 在庫の Repository
     * @param clock               調整の時刻を取る時計
     */
    public InventoryTestData(StockItemRepository stockItemRepository, Clock clock) {
        this.stockItemRepository = stockItemRepository;
        this.clock = clock;
    }

    /**
     * コンストラクタ（調整の時刻を固定する）
     *
     * @param stockItemRepository 在庫の Repository
     * @param instant             調整の時刻
     */
    public InventoryTestData(StockItemRepository stockItemRepository, Instant instant) {
        this(stockItemRepository, Clock.fixed(instant, ZoneOffset.UTC));
    }

    /**
     * 在庫を作る（なければ）。在庫数は 0 のまま。
     *
     * @param variantId バリアント ID
     * @param productId 商品 ID
     */
    public void create(UUID variantId, UUID productId) {
        stockItemRepository.createIfAbsent(variantId, productId);
    }

    /**
     * 在庫を作り（なければ）、在庫数を入荷として調整する。
     *
     * @param variantId バリアント ID
     * @param productId 商品 ID
     * @param quantity  入荷の数量
     */
    public void stock(UUID variantId, UUID productId, int quantity) {
        stockItemRepository.createIfAbsent(variantId, productId);
        stockItemRepository.adjust(variantId,
                StockAdjustment.create(quantity, StockAdjustmentReason.ARRIVAL, null, TestTokens.OPERATOR, clock.instant()));
    }
}
