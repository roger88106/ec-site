package com.ecsite;

import com.ecsite.module.inventory.domain.stock.StockAdjustment;
import com.ecsite.module.inventory.domain.stock.StockAdjustmentReason;
import com.ecsite.module.inventory.domain.stock.port.StockItemRepository;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/**
 * 在庫のテストデータ
 *
 * <p>統合テストで、バリアントの在庫を作り、入荷として調整する。
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
     * 在庫を作る（なければ）。
     *
     * <p>在庫数は 0 のまま。
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
     * <p>数量が 0 なら、作るだけで調整しない。
     *
     * @param variantId バリアント ID
     * @param productId 商品 ID
     * @param quantity  入荷の数量
     */
    public void stock(UUID variantId, UUID productId, int quantity) {
        stockItemRepository.createIfAbsent(variantId, productId);
        if (quantity != 0) {
            stockItemRepository.adjust(variantId,
                    StockAdjustment.create(quantity, StockAdjustmentReason.ARRIVAL, null, TestTokens.OPERATOR, clock.instant()));
        }
    }

    /**
     * 新しい商品 ID で在庫を作り、在庫数を入荷として調整する。
     *
     * <p>商品を問わないテストで使う。数量が 0 なら、作るだけで調整しない。
     *
     * @param variantId バリアント ID
     * @param quantity  入荷の数量
     */
    public void stock(UUID variantId, int quantity) {
        stock(variantId, UUID.randomUUID(), quantity);
    }

    /**
     * 新しいバリアント ID で在庫を作り、在庫数を入荷として調整する。
     *
     * <p>数量が 0 なら、作るだけで調整しない。
     *
     * @param productId 商品 ID
     * @param quantity  入荷の数量
     * @return 作った在庫のバリアント ID
     */
    public UUID stockNewVariant(UUID productId, int quantity) {
        var variantId = UUID.randomUUID();
        stock(variantId, productId, quantity);
        return variantId;
    }

    /**
     * 商品（1,000 円）を作って公開し、そのバリアントの在庫を作って、在庫数を入荷として調整する。
     *
     * @param catalog       商品を作る補助
     * @param taxCategoryId 税区分 ID
     * @param name          商品名
     * @param quantity      入荷の数量
     * @return バリアント ID
     * @throws Exception 商品の API の呼び出しに失敗したとき
     */
    public UUID stockedVariant(CatalogTestData catalog, UUID taxCategoryId, String name, int quantity) throws Exception {
        var product = catalog.product(taxCategoryId, name, 1000).publish();
        var variantId = product.variantIds.getFirst();
        stock(variantId, product.id, quantity);
        return variantId;
    }

    /**
     * バリアントの在庫数を、管理の API で確認する。
     *
     * @param mockMvc   API を呼ぶ MockMvc
     * @param variantId バリアント ID
     * @param quantity  期待する在庫数
     * @throws Exception API の呼び出しに失敗したとき、または在庫数が違うとき
     */
    public static void expectQuantity(MockMvc mockMvc, UUID variantId, int quantity) throws Exception {
        mockMvc.perform(get("/api/admin/inventory/variants/" + variantId)).andExpect(jsonPath("$.quantity").value(quantity));
    }
}
