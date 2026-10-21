package com.ecsite.module.catalog.infrastructure.persistence;

import com.ecsite.module.catalog.application.port.MediaDirectory;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static com.ecsite.jooq.catalog.Tables.PRODUCT_IMAGE;

/**
 * C 側に返す商品画像の URL。一覧・検索・詳細で共通。<br />
 * 画像のアセット ID を引き、配信の URL はメディアモジュールからまとめて取得する。
 */
@Component
@RequiredArgsConstructor
class StoreImageUrls {

    /**
     * jOOQ DSL Context
     */
    private final DSLContext dsl;

    /**
     * メディアの腐敗防止層（画像の配信の URL）
     */
    private final MediaDirectory mediaDirectory;

    /**
     * 商品ごとの代表画像（画像の並びの先頭）の URL を取得する。
     *
     * @param productIds 商品 ID
     * @return 商品 ID → 代表画像の URL。画像のない商品は含まない
     */
    Map<UUID, String> representatives(Collection<UUID> productIds) {
        if (productIds.isEmpty()) {
            return Map.of();
        }
        var assetIds = dsl.select(PRODUCT_IMAGE.PRODUCT_ID, PRODUCT_IMAGE.ASSET_ID)
                // 商品ごとに、表示順の最初の 1 枚だけを取る
                .distinctOn(PRODUCT_IMAGE.PRODUCT_ID)
                .from(PRODUCT_IMAGE)
                .where(PRODUCT_IMAGE.PRODUCT_ID.in(productIds))
                .orderBy(PRODUCT_IMAGE.PRODUCT_ID, PRODUCT_IMAGE.DISPLAY_ORDER)
                .fetchMap(PRODUCT_IMAGE.PRODUCT_ID, PRODUCT_IMAGE.ASSET_ID);
        return toUrls(assetIds);
    }

    /**
     * 商品の画像の URL を、表示順で取得する。
     *
     * @param productId 商品 ID
     * @return 画像 ID → URL（表示順）
     */
    Map<UUID, String> ofProduct(UUID productId) {
        var assetIds = new LinkedHashMap<UUID, UUID>();
        dsl.select(PRODUCT_IMAGE.ID, PRODUCT_IMAGE.ASSET_ID)
                .from(PRODUCT_IMAGE)
                .where(PRODUCT_IMAGE.PRODUCT_ID.eq(productId))
                .orderBy(PRODUCT_IMAGE.DISPLAY_ORDER)
                .forEach(record -> assetIds.put(record.value1(), record.value2()));
        return toUrls(assetIds);
    }

    /**
     * 画像 ID ごとの URL を取得する（バリアントの画像）。
     *
     * @param imageIds 画像 ID
     * @return 画像 ID → URL。見つからない画像は含まない
     */
    Map<UUID, String> ofImages(Collection<UUID> imageIds) {
        if (imageIds.isEmpty()) {
            return Map.of();
        }
        var assetIds = dsl.select(PRODUCT_IMAGE.ID, PRODUCT_IMAGE.ASSET_ID)
                .from(PRODUCT_IMAGE)
                .where(PRODUCT_IMAGE.ID.in(imageIds))
                .fetchMap(PRODUCT_IMAGE.ID, PRODUCT_IMAGE.ASSET_ID);
        return toUrls(assetIds);
    }

// ============================== プライベートメソッド ==============================

    /**
     * アセット ID を、メディアモジュールからまとめて取得した配信の URL に置き換える。並びは保つ。
     *
     * @param assetIds キー（商品 ID、画像 ID など）→ アセット ID
     * @param <K>      キーの型
     * @return キー → 配信の URL。URL のないアセット（メディアモジュールに記録がない）は含めない
     */
    private <K> Map<K, String> toUrls(Map<K, UUID> assetIds) {
        var urls = mediaDirectory.urls(assetIds.values());
        var result = new LinkedHashMap<K, String>();
        assetIds.forEach((key, assetId) -> {
            var url = urls.get(assetId);
            if (url != null) {
                result.put(key, url.toString());
            }
        });
        return result;
    }
}
