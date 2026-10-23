package com.ecsite.module.reporting.application.port;

import com.ecsite.module.reporting.domain.snapshot.ReportOrder;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 商品カタログの腐敗防止層
 */
public interface CatalogDirectory {

    /**
     * バリアントの商品 ID を求める。
     *
     * @param variantIds バリアント ID
     * @return バリアント ID → 商品 ID。存在しないバリアントは含めない
     */
    Map<UUID, UUID> productIdsOf(Collection<UUID> variantIds);

    /**
     * 商品とバリアントの一覧を読む。
     *
     * @param productId 商品 ID
     * @return 商品。なければ空
     */
    Optional<Product> findProduct(UUID productId);

    /**
     * 商品
     *
     * @param productId 商品 ID
     * @param name      商品名
     * @param variants  バリアント（商品カタログの並びの順）
     */
    record Product(UUID productId, String name, List<Variant> variants) {
    }

    /**
     * バリアント
     *
     * @param variantId   バリアント ID
     * @param variantCode バリアント品番
     * @param axisValues  軸の名前と値
     */
    record Variant(UUID variantId, String variantCode, List<ReportOrder.AxisValue> axisValues) {
    }
}
