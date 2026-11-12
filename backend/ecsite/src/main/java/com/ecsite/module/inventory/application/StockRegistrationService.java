package com.ecsite.module.inventory.application;

import com.ecsite.module.inventory.domain.stock.port.StockItemRepository;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * 在庫の作成と削除のサービス
 */
@Service
@RequiredArgsConstructor
public class StockRegistrationService {

    /**
     * 在庫リポジトリ
     */
    private final StockItemRepository stockItemRepository;

    /**
     * 在庫数 0 の在庫を作る。すでにあれば何もしない。
     *
     * @param variantId バリアント ID
     * @param productId 商品 ID
     */
    @Transactional
    public void createIfAbsent(@NonNull UUID variantId, @NonNull UUID productId) {
        stockItemRepository.createIfAbsent(variantId, productId);
    }

    /**
     * バリアントの在庫と調整履歴を消す。なければ何もしない。
     *
     * @param variantId バリアント ID
     */
    @Transactional
    public void deleteByVariant(@NonNull UUID variantId) {
        stockItemRepository.deleteByVariantId(variantId);
    }

    /**
     * 商品のすべての在庫と調整履歴を消す。なければ何もしない。
     *
     * @param productId 商品 ID
     */
    @Transactional
    public void deleteByProduct(@NonNull UUID productId) {
        stockItemRepository.deleteByProductId(productId);
    }
}
