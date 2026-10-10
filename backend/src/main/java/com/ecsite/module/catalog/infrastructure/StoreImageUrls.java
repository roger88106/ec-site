package com.ecsite.module.catalog.infrastructure;

import com.ecsite.module.catalog.domain.product.port.ImageStorage;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static com.ecsite.jooq.catalog.Tables.PRODUCT_IMAGE;

/**
 * C 側に返す商品画像の URL（plan 001 第 7.4 節）。一覧・検索・詳細で共通。
 */
@Component
@RequiredArgsConstructor
class StoreImageUrls {

    /**
     * jOOQ DSL Context
     */
    private final DSLContext dsl;

    /**
     * 画像の置き場所（配信の URL を組み立てる）
     */
    private final ImageStorage imageStorage;

    /**
     * 商品ごとの代表画像（画像の並びの先頭。AC-37）の URL を取得する。
     *
     * @param productIds 商品 ID
     * @return 商品 ID → 代表画像の URL。画像のない商品は含まない
     */
    Map<UUID, String> representatives(Collection<UUID> productIds) {
        if (productIds.isEmpty()) {
            return Map.of();
        }
        return dsl.select(PRODUCT_IMAGE.PRODUCT_ID, PRODUCT_IMAGE.STORAGE_KEY)
                .distinctOn(PRODUCT_IMAGE.PRODUCT_ID)
                .from(PRODUCT_IMAGE)
                .where(PRODUCT_IMAGE.PRODUCT_ID.in(productIds))
                .orderBy(PRODUCT_IMAGE.PRODUCT_ID, PRODUCT_IMAGE.DISPLAY_ORDER)
                .fetchMap(PRODUCT_IMAGE.PRODUCT_ID, record -> url(record.value2()));
    }

    /**
     * 商品の画像の URL を、表示順で取得する。
     *
     * @param productId 商品 ID
     * @return 画像 ID → URL（表示順）
     */
    Map<UUID, String> ofProduct(UUID productId) {
        var urls = new LinkedHashMap<UUID, String>();
        dsl.select(PRODUCT_IMAGE.ID, PRODUCT_IMAGE.STORAGE_KEY)
                .from(PRODUCT_IMAGE)
                .where(PRODUCT_IMAGE.PRODUCT_ID.eq(productId))
                .orderBy(PRODUCT_IMAGE.DISPLAY_ORDER)
                .forEach(record -> urls.put(record.value1(), url(record.value2())));
        return urls;
    }

    private String url(String storageKey) {
        return imageStorage.publicUrl(storageKey).toString();
    }
}
