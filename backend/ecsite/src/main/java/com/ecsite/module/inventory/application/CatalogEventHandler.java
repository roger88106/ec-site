package com.ecsite.module.inventory.application;

import com.ecsite.module.catalog.event.ProductDeleted;
import com.ecsite.module.catalog.event.VariantDeleted;
import com.ecsite.module.catalog.event.VariantRegistered;
import lombok.RequiredArgsConstructor;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * 商品カタログのイベントのリスナー。バリアントの登録・削除に合わせて、在庫を作る・消す（docs/design/modules/inventory.md）。<br />
 * コミット後に非同期で、別のトランザクションで動く。同じイベントが複数回届いても結果が変わらないように作る（バックエンドのアーキテクチャ 第 5 節）。
 */
@Component
@RequiredArgsConstructor
class CatalogEventHandler {

    /**
     * 在庫の作成と削除のサービス
     */
    private final StockRegistrationService registrationService;

    /**
     * バリアント登録：在庫数 0 の在庫を作る。
     *
     * @param event バリアント登録
     */
    @ApplicationModuleListener
    void on(VariantRegistered event) {
        registrationService.createIfAbsent(event.variantId(), event.productId());
    }

    /**
     * バリアント削除：その在庫と調整履歴を消す。
     *
     * @param event バリアント削除
     */
    @ApplicationModuleListener
    void on(VariantDeleted event) {
        registrationService.deleteByVariant(event.variantId());
    }

    /**
     * 商品削除：その商品のすべての在庫と調整履歴を消す。
     *
     * @param event 商品削除
     */
    @ApplicationModuleListener
    void on(ProductDeleted event) {
        registrationService.deleteByProduct(event.productId());
    }
}
