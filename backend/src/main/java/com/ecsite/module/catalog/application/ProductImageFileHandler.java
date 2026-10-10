package com.ecsite.module.catalog.application;

import com.ecsite.module.catalog.domain.product.port.ImageStorage;
import com.ecsite.module.catalog.event.ProductDeleted;
import com.ecsite.module.catalog.event.ProductImageDeleted;
import lombok.RequiredArgsConstructor;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * 配信のバケットのファイルを、商品画像・商品の削除の後に消す（docs/modules/catalog.md「商品画像」）。<br />
 * イベントはコミット後に届き、重ねて届くことがある（constitution 5-7）。削除は何度行っても同じ結果になる。
 */
@Component
@RequiredArgsConstructor
class ProductImageFileHandler {

    /**
     * 画像の置き場所
     */
    private final ImageStorage imageStorage;

    /**
     * 商品画像削除：その画像のファイルを消す。
     *
     * @param event 商品画像削除
     */
    @ApplicationModuleListener
    void on(ProductImageDeleted event) {
        imageStorage.deletePublished(event.storageKey());
    }

    /**
     * 商品削除：その商品のすべての画像のファイルを消す。
     *
     * @param event 商品削除
     */
    @ApplicationModuleListener
    void on(ProductDeleted event) {
        imageStorage.deletePublishedByPrefix(ProductImageKeys.productPrefix(event.productId()));
    }
}
