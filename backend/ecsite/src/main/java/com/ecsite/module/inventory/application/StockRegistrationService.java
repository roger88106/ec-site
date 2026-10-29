package com.ecsite.module.inventory.application;

import com.ecsite.module.inventory.domain.stock.port.StockItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * 在庫の作成と削除のサービス（docs/03-機能仕様/inventory.md）。<br />
 * 商品カタログのバリアントの登録・削除に合わせて、在庫を作る・消す。
 */
@Service
@RequiredArgsConstructor
public class StockRegistrationService {

    /**
     * 在庫リポジトリ
     */
    private final StockItemRepository stockItemRepository;

    /**
     * 在庫数 0 の在庫を作る。すでにあれば何もしない（同じイベントが複数回届いても 1 つ）。
     *
     * @param variantId バリアント ID
     * @param productId 商品 ID
     */
    @Transactional
    public void createIfAbsent(UUID variantId, UUID productId) {
        stockItemRepository.createIfAbsent(variantId, productId);
    }

    /**
     * バリアントの在庫と調整履歴を消す。なければ何もしない。
     *
     * @param variantId バリアント ID
     */
    @Transactional
    public void deleteByVariant(UUID variantId) {
        stockItemRepository.deleteByVariantId(variantId);
    }

    /**
     * 商品のすべての在庫と調整履歴を消す。なければ何もしない。
     *
     * @param productId 商品 ID
     */
    @Transactional
    public void deleteByProduct(UUID productId) {
        stockItemRepository.deleteByProductId(productId);
    }
}
