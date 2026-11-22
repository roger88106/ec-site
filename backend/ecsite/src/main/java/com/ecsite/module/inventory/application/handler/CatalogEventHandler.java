package com.ecsite.module.inventory.application.handler;

import com.ecsite.module.catalog.event.ProductDeleted;
import com.ecsite.module.catalog.event.VariantDeleted;
import com.ecsite.module.catalog.event.VariantRegistered;
import com.ecsite.module.inventory.application.StockCommandService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 商品カタログのイベントのハンドラー
 *
 * <p>バリアント登録は、商品カタログのトランザクションの中で同期で受け、在庫の作成を登録と同じコミットに入れる。
 * バリアント削除と商品削除は、商品カタログのコミットの後に、新しいトランザクションで受ける。
 *
 * <p>同じイベントが複数回届いても、結果は変わらない。
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
     * <p>発行したトランザクションに加わる。外で発行されたときは例外にする。在庫の作成に失敗すると、バリアントの登録も巻き戻る。
     *
     * @param event バリアント登録
     */
    // 削除のイベントは登録のコミットの後にしか発行されないので、在庫の作成を同じコミットに入れると、削除より後に在庫を作ることがない
    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    void on(VariantRegistered event) {
        commandService.createIfAbsent(event.variantId().value(), event.productId().value());
    }

    /**
     * バリアント削除：その在庫と調整履歴を消す。
     *
     * @param event バリアント削除
     */
    @ApplicationModuleListener
    void on(VariantDeleted event) {
        commandService.deleteByVariant(event.variantId().value());
    }

    /**
     * 商品削除：その商品のすべての在庫と調整履歴を消す。
     *
     * @param event 商品削除
     */
    @ApplicationModuleListener
    void on(ProductDeleted event) {
        commandService.deleteByProduct(event.productId().value());
    }
}
