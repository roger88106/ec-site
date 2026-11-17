package com.ecsite.module.inventory.application.handler;

import com.ecsite.module.catalog.event.ProductDeleted;
import com.ecsite.module.catalog.event.VariantDeleted;
import com.ecsite.module.catalog.event.VariantRegistered;
import com.ecsite.module.inventory.application.StockCommandService;
import lombok.RequiredArgsConstructor;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * 商品カタログのイベントのハンドラー
 *
 * <p>商品カタログのコミットの後に、新しいトランザクションで受ける。同じイベントが複数回届いても、結果は変わらない。
 */
@Component
@RequiredArgsConstructor
class CatalogEventHandler {

    /**
     * 在庫のコマンドサービス
     */
    private final StockCommandService commandService;

    /**
     * バリアント登録：在庫数 0 の在庫を作る。
     *
     * @param event バリアント登録
     */
    @ApplicationModuleListener
    void on(VariantRegistered event) {
        commandService.createIfAbsent(event.variantId(), event.productId());
    }

    /**
     * バリアント削除：その在庫と調整履歴を消す。
     *
     * @param event バリアント削除
     */
    @ApplicationModuleListener
    void on(VariantDeleted event) {
        commandService.deleteByVariant(event.variantId());
    }

    /**
     * 商品削除：その商品のすべての在庫と調整履歴を消す。
     *
     * @param event 商品削除
     */
    @ApplicationModuleListener
    void on(ProductDeleted event) {
        commandService.deleteByProduct(event.productId());
    }
}
